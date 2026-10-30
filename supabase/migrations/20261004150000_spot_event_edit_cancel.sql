-- Organizers can edit an event until it starts, delete it while nobody else is involved, and otherwise cancel it.
-- Cancelling keeps the event for the people in it (their history, the event chat) but closes it: it leaves
-- browsing and search, and nobody can apply, be invited or be accepted any more. Changing the time, place or
-- talent fee, or cancelling, notifies accepted performers, pending applicants and pending invitees.

alter table public.events add column cancelled_at timestamptz;

alter table public.notifications drop constraint notifications_type_check;
alter table public.notifications add constraint notifications_type_check check (type in ('like', 'comment', 'follow',
  'event_join', 'group_join', 'group_request', 'group_approved', 'group_post', 'invitation', 'invitation_accepted',
  'invitation_declined', 'application', 'application_accepted', 'application_declined', 'event_application',
  'event_application_accepted', 'event_application_declined', 'event_updated', 'event_cancelled'));

-- Everyone besides the organizer who is waiting on or taking part in the event.
create function private.event_people(p_id text) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select p.user_id from public.event_participants p join public.events e on e.id = p.event_id
  where p.event_id = p_id and p.user_id <> e.organizer_id
  union
  select a.user_id from public.event_applications a where a.event_id = p_id and a.status = 'pending'
  union
  select r.creator_id from public.talent_requests r where r.kind = 'invitation' and r.source_id = p_id and r.status = 'pending';
$$;
revoke all on function private.event_people(text) from public, anon, authenticated;

-- The same checks as create_event. Slots can't drop below the performers already accepted.
create function public.update_event(p_id text, p_title text, p_description text, p_image_url text, p_category text,
  p_starts_at timestamptz, p_location text, p_organizer text, p_participant_limit integer, p_talent_fee text,
  p_talent_fee_budget text default '')
returns void language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  e public.events;
  budget text := case when p_talent_fee = 'paid' then btrim(coalesce(p_talent_fee_budget, '')) else '' end;
  changes text[] := '{}';
  person uuid;
begin
  select * into e from public.events where id = p_id for update;
  if not found or e.organizer_id <> me then raise exception 'Only the event''s organizer can edit it.'; end if;
  if e.cancelled_at is not null then raise exception 'This event was cancelled, so it can''t be edited.'; end if;
  if e.starts_at <= now() then raise exception 'This event has already started, so it can''t be edited.'; end if;
  if p_starts_at is null or p_starts_at <= now() then raise exception 'Choose a future date and time.'; end if;
  if p_talent_fee is null or p_talent_fee not in ('free', 'paid') then
    raise exception 'Choose For Free Only or With Talent Fee Budget.';
  end if;
  if char_length(budget) > 80 then raise exception 'Keep the talent fee budget to 80 characters.'; end if;
  if p_participant_limit is not null and p_participant_limit < e.participant_count then
    raise exception 'You''ve accepted % performers, so keep at least % slots.', e.participant_count, e.participant_count;
  end if;
  if coalesce(p_image_url, '') <> '' and p_image_url is distinct from e.image_url
    and not private.cloudinary_url(p_image_url, 'image', 'events', me, p_id) then
    raise exception 'Image upload failed. Please retry.';
  end if;

  if p_starts_at is distinct from e.starts_at then changes := array_append(changes, 'time'); end if;
  if trim(p_location) is distinct from e.location then changes := array_append(changes, 'place'); end if;
  if p_talent_fee is distinct from e.talent_fee or budget is distinct from e.talent_fee_budget then
    changes := array_append(changes, 'talent fee');
  end if;

  update public.events set title = trim(p_title), description = trim(p_description), image_url = coalesce(p_image_url, ''),
    category = p_category, starts_at = p_starts_at, location = trim(p_location), organizer = trim(p_organizer),
    participant_limit = p_participant_limit, talent_fee = p_talent_fee, talent_fee_budget = budget
  where id = p_id;
  -- The event chat and pending invitations show the event's name.
  update public.conversations set title = left(trim(p_title), 100) where community_kind = 'event' and community_id = p_id;
  update public.talent_requests set title = trim(p_title)
  where kind = 'invitation' and source_id = p_id and status = 'pending';

  if cardinality(changes) > 0 then
    foreach person in array array(select private.event_people(p_id)) loop
      perform private.notify_about(person, 'event_updated_' || p_id || '_' || floor(extract(epoch from clock_timestamp()))::bigint,
        'event_updated', p_id, 'changed the ' || case cardinality(changes)
          when 1 then changes[1]
          when 2 then changes[1] || ' and ' || changes[2]
          else array_to_string(changes[1:cardinality(changes) - 1], ', ') || ' and ' || changes[cardinality(changes)]
        end || ' of ' || trim(p_title));
    end loop;
  end if;
end $$;

-- Only while nobody else is involved: no accepted performers, pending applications or pending invitations.
create function public.delete_event(p_id text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  e public.events;
begin
  select * into e from public.events where id = p_id for update;
  if not found then return; end if;
  if e.organizer_id <> me then raise exception 'Only the event''s organizer can delete it.'; end if;
  if exists (select private.event_people(p_id)) then
    raise exception 'People have applied, joined or been invited, so cancel the event instead.';
  end if;
  delete from public.conversations where community_kind = 'event' and community_id = p_id;
  delete from public.events where id = p_id;
end $$;

-- Before it starts. Pending invitations are withdrawn; accepted performers keep the event and its chat.
create function public.cancel_event(p_id text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  e public.events;
  person uuid;
  people uuid[];
begin
  select * into e from public.events where id = p_id for update;
  if not found or e.organizer_id <> me then raise exception 'Only the event''s organizer can cancel it.'; end if;
  if e.cancelled_at is not null then return; end if;
  if e.starts_at <= now() then raise exception 'This event has already started, so it can''t be cancelled.'; end if;
  people := array(select private.event_people(p_id));
  update public.events set cancelled_at = now() where id = p_id;
  update public.talent_requests set status = 'withdrawn', updated_at = now()
  where kind = 'invitation' and source_id = p_id and status = 'pending';
  foreach person in array people loop
    perform private.notify_about(person, 'event_cancelled_' || p_id, 'event_cancelled', p_id, 'cancelled ' || e.title);
  end loop;
end $$;

revoke all on function public.update_event(text, text, text, text, text, timestamptz, text, text, integer, text, text),
  public.delete_event(text), public.cancel_event(text) from public, anon;
grant execute on function public.update_event(text, text, text, text, text, timestamptz, text, text, integer, text, text),
  public.delete_event(text), public.cancel_event(text) to authenticated;

-- Cancelled events leave browsing, search and profiles.
create or replace function public.search_events(p_term text default '', p_category text default '', p_offset integer default 0)
returns setof public.events language sql stable set search_path = '' as $$
  select e.* from public.events e
  where e.cancelled_at is null
    and (coalesce(p_category, '') = '' or e.category = p_category)
    and (coalesce(p_term, '') = '' or starts_with(e.name_key, lower(trim(p_term))))
  order by case when coalesce(p_term, '') = '' then -e.seq end, e.name_key, e.id
  limit 30 offset greatest(p_offset, 0);
$$;

create or replace function public.profile_events(p_user uuid)
returns setof public.events language sql stable security definer set search_path = '' as $$
  select e.*
  from public.event_participants p
  join public.events e on e.id = p.event_id
  join public.profiles owner on owner.id = p.user_id
  where p.user_id = p_user
    and e.cancelled_at is null
    and (select auth.uid()) is not null
    and not private.is_blocked(p_user, (select auth.uid()))
    and not private.is_blocked(e.organizer_id, (select auth.uid()))
    and (p_user = (select auth.uid()) or not owner.is_private
      or exists (select 1 from public.follows f where f.following_id = p_user and f.follower_id = (select auth.uid())))
  order by e.starts_at desc, e.id desc
  limit 30;
$$;

-- Nobody can apply to, be accepted for, or be invited to a cancelled event.
create or replace function public.apply_to_event(p_id text, p_note text default '')
returns void language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  e public.events;
  current_status text;
  body text := btrim(coalesce(p_note, ''));
begin
  if me.account_type is distinct from 'student' then raise exception 'Only Creators can apply to perform at events.'; end if;
  select * into e from public.events where id = p_id;
  if not found or private.is_blocked(e.organizer_id, me.id) then raise exception 'This event is no longer available.'; end if;
  if e.cancelled_at is not null then raise exception 'This event was cancelled.'; end if;
  if exists (select 1 from public.event_participants where event_id = p_id and user_id = me.id) then return; end if;
  select a.status into current_status from public.event_applications a where a.event_id = p_id and a.user_id = me.id;
  if current_status = 'pending' then return; end if;
  if current_status = 'declined' then raise exception 'The organizer declined your application for this event.'; end if;
  if e.starts_at <= now() then raise exception 'Registration has closed.'; end if;
  if e.participant_limit is not null and e.participant_count >= e.participant_limit then
    raise exception 'This event is full.';
  end if;
  if char_length(body) > 500 then raise exception 'Keep your note under 500 characters.'; end if;
  delete from public.event_applications where event_id = p_id and user_id = me.id;
  insert into public.event_applications (event_id, user_id, display_name, profile_image_url, note)
  values (p_id, me.id, me.display_name, me.profile_image_url, body);
  perform private.notify_about(e.organizer_id, 'event_application_' || p_id || '_' || me.id::text, 'event_application', p_id,
    'applied to perform at ' || e.title);
end $$;

create or replace function public.review_event_application(p_id text, p_applicant uuid, p_accept boolean)
returns void language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  e public.events;
  a public.event_applications;
  answer text := case when p_accept then 'accepted' else 'declined' end;
begin
  select * into e from public.events where id = p_id for update;
  if not found or e.organizer_id <> me then raise exception 'Only the event''s organizer can review applications.'; end if;
  select * into a from public.event_applications where event_id = p_id and user_id = p_applicant for update;
  if not found then raise exception 'This application was withdrawn.'; end if;
  if a.status <> 'pending' then raise exception 'This application has already been reviewed.'; end if;
  if p_accept then
    if e.cancelled_at is not null then raise exception 'This event was cancelled.'; end if;
    if e.starts_at <= now() then raise exception 'This event has already started.'; end if;
    if private.is_blocked(me, p_applicant) then raise exception 'This application is no longer available.'; end if;
    if not exists (select 1 from public.event_participants where event_id = p_id and user_id = p_applicant) then
      if e.participant_limit is not null and e.participant_count >= e.participant_limit then
        raise exception 'All slots are filled.';
      end if;
      insert into public.event_participants (event_id, user_id, display_name, profile_image_url)
      select p_id, p.id, p.display_name, p.profile_image_url from public.profiles p where p.id = p_applicant;
      update public.events set participant_count = participant_count + 1 where id = p_id;
    end if;
    perform private.community_chat_member('event', p_id, p_applicant, true);
  end if;
  update public.event_applications set status = answer, updated_at = now() where event_id = p_id and user_id = p_applicant;
  perform private.notify_about(p_applicant, 'event_application_' || answer || '_' || p_id, 'event_application_' || answer, p_id,
    answer || ' your application for ' || e.title);
end $$;

create or replace function public.create_talent_request(p_kind text, p_source_id text, p_creator_id uuid, p_details text)
returns void language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  seeker uuid;
  heading text;
  request_id text := p_kind || '_' || p_source_id || '_' || p_creator_id::text;
  e public.events;
  o public.opportunities;
begin
  if char_length(trim(coalesce(p_details, ''))) not between 10 and 2000 then
    raise exception 'Add details between 10 and 2,000 characters.';
  end if;
  if p_kind = 'invitation' then
    select * into e from public.events where id = p_source_id;
    if me.account_type is distinct from 'talent_seeker' or not found or e.organizer_id <> me.id or p_creator_id = me.id then
      raise exception 'Choose your event and a creator.';
    end if;
    if e.cancelled_at is not null then raise exception 'This event was cancelled.'; end if;
    if e.starts_at <= now() then raise exception 'Choose an upcoming event.'; end if;
    if not exists (select 1 from public.profiles where id = p_creator_id and account_type = 'student' and profile_completed) then
      raise exception 'Choose your event and a creator.';
    end if;
    seeker := me.id;
    heading := e.title;
  elsif p_kind = 'application' then
    select * into o from public.opportunities where id = p_source_id;
    if me.account_type is distinct from 'student' or p_creator_id <> me.id or not found then
      raise exception 'This action is no longer available.';
    end if;
    if o.status <> 'open' or o.deadline <= now() then raise exception 'Applications have closed.'; end if;
    seeker := o.seeker_id;
    heading := o.title;
  else
    raise exception 'This action is no longer available.';
  end if;
  if private.is_blocked(seeker, p_creator_id) then raise exception 'This action is no longer available.'; end if;
  insert into public.talent_requests (id, kind, source_id, seeker_id, creator_id, title, details)
  values (request_id, p_kind, p_source_id, seeker, p_creator_id, heading, trim(p_details))
  on conflict (id) do nothing;
  if not found then raise exception 'A request already exists. See its status in Messages under Requests.'; end if;
  if p_kind = 'invitation' then
    perform private.notify_about(p_creator_id, request_id, 'invitation', request_id, 'invited you to ' || heading);
    perform public.send_message(private.direct_chat(p_creator_id), gen_random_uuid()::text,
      'spot://event/' || p_source_id || E'\n' || trim(p_details), null);
  else
    perform private.notify_about(seeker, request_id, 'application', p_source_id, 'applied to ' || heading);
  end if;
end $$;

create or replace function public.respond_talent_request(p_id text, p_status text)
returns void language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  r public.talent_requests;
  sender uuid;
  recipient uuid;
begin
  select * into r from public.talent_requests where id = p_id for update;
  if not found or r.status <> 'pending' then raise exception 'This request has already been updated.'; end if;
  sender := case when r.kind = 'invitation' then r.seeker_id else r.creator_id end;
  recipient := case when r.kind = 'invitation' then r.creator_id else r.seeker_id end;
  if not ((p_status = 'withdrawn' and me = sender)
    or (p_status in ('accepted', 'declined') and me = recipient and not private.is_blocked(r.creator_id, r.seeker_id))) then
    raise exception 'This action is no longer available.';
  end if;
  if p_status = 'accepted' and not (
    (r.kind = 'invitation' and exists (select 1 from public.events where id = r.source_id and starts_at > now() and cancelled_at is null))
    or (r.kind = 'application' and exists (select 1 from public.opportunities
      where id = r.source_id and status = 'open' and deadline > now()))) then
    raise exception 'This action is no longer available.';
  end if;
  update public.talent_requests set status = p_status, updated_at = now() where id = p_id;
  if p_status = 'withdrawn' then return; end if;
  perform private.notify_about(sender, p_status || '_' || p_id, r.kind || '_' || p_status,
    case when r.kind = 'invitation' then p_id else r.source_id end,
    p_status || case when r.kind = 'invitation' then ' your invitation to ' else ' your application for ' end || r.title);
  if p_status = 'accepted' and r.kind = 'invitation' then
    insert into public.event_participants (event_id, user_id, display_name, profile_image_url)
    select r.source_id, p.id, p.display_name, p.profile_image_url from public.profiles p where p.id = r.creator_id
    on conflict do nothing;
    if found then update public.events set participant_count = participant_count + 1 where id = r.source_id; end if;
    perform private.community_chat_member('event', r.source_id, r.creator_id, true);
  end if;
end $$;
