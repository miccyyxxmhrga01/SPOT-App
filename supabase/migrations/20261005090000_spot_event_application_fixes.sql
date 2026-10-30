-- Event applications follow what happens to the event and to the Creator:
-- * Cancelling an event clears its pending applications. Their Creators are told it was cancelled, and nothing is
--   left waiting for an answer that can't come.
-- * Accepting an invitation to an event you've already applied to settles the application as accepted, so the
--   organizer isn't asked to review someone who's performing, and a later Decline can't lock them out.
-- * Accepted invitations can take an event past its slots. The organizer can still edit it; only lowering the
--   slots below the performers already in is refused.
-- Needs spot_event_edit_cancel.

-- Rows the old rules left behind.
delete from public.event_applications a using public.events e
where a.event_id = e.id and e.cancelled_at is not null and a.status = 'pending';
update public.event_applications a set status = 'accepted', updated_at = now()
from public.event_participants p
where p.event_id = a.event_id and p.user_id = a.user_id and a.status = 'pending';

create or replace function public.cancel_event(p_id text) returns void
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
  delete from public.event_applications where event_id = p_id and status = 'pending';
  foreach person in array people loop
    perform private.notify_about(person, 'event_cancelled_' || p_id, 'event_cancelled', p_id, 'cancelled ' || e.title);
  end loop;
end $$;

create or replace function public.update_event(p_id text, p_title text, p_description text, p_image_url text, p_category text,
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
  if p_participant_limit is not null and p_participant_limit is distinct from e.participant_limit
    and p_participant_limit < e.participant_count then
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
    -- An application to the same event is answered by this.
    update public.event_applications set status = 'accepted', updated_at = now()
    where event_id = r.source_id and user_id = r.creator_id and status = 'pending';
    perform private.community_chat_member('event', r.source_id, r.creator_id, true);
  end if;
end $$;
