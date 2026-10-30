-- Events take applications. A Creator applies with "Join event" and the Spotter who posted the event accepts or
-- declines each application on its own. Only an accepted Creator takes a slot (one each) and joins the event's
-- chat. Events also say what the Spotter offers performers: no talent fee ("free") or a talent fee ("paid"),
-- with an optional budget. Collaborator suggestions weigh relevant and complementary talents, shared interests,
-- location and collaboration preferences, give a short reason, and can be dismissed.
-- Needs spot_community.

-- =========================================================
-- Talent fee
-- =========================================================

-- Null for events posted before the choice existed, and by older app versions.
alter table public.events
  add column talent_fee text check (talent_fee in ('free', 'paid')),
  add column talent_fee_budget text not null default '' check (char_length(talent_fee_budget) <= 80),
  add constraint events_talent_fee_paid_budget_check check (coalesce(talent_fee, '') = 'paid' or talent_fee_budget = '');

drop function public.create_event(text, text, text, text, text, timestamptz, text, text, integer);
create function public.create_event(
  p_id text, p_title text, p_description text, p_image_url text, p_category text, p_starts_at timestamptz,
  p_location text, p_organizer text, p_participant_limit integer default null,
  p_talent_fee text default null, p_talent_fee_budget text default ''
) returns text
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  organizer uuid;
  budget text := btrim(coalesce(p_talent_fee_budget, ''));
begin
  if me.account_type is distinct from 'talent_seeker' then raise exception 'Only Spotters can create events.'; end if;
  select organizer_id into organizer from public.events where id = p_id;
  if found then
    if organizer <> me.id then raise exception 'Your account changed. Please retry.'; end if;
    return p_id;
  end if;
  if p_starts_at <= now() then raise exception 'Choose a future date and time.'; end if;
  if p_talent_fee is not null and p_talent_fee not in ('free', 'paid') then
    raise exception 'Choose For Free Only or With Talent Fee Budget.';
  end if;
  if char_length(budget) > 80 then raise exception 'Keep the talent fee budget to 80 characters.'; end if;
  if coalesce(p_image_url, '') <> '' and not private.cloudinary_url(p_image_url, 'image', 'events', me.id, p_id) then
    raise exception 'Image upload failed. Please retry.';
  end if;
  insert into public.events (id, title, description, image_url, category, starts_at, location, organizer,
    organizer_id, organizer_name, organizer_profile_image_url, participant_limit, talent_fee, talent_fee_budget)
  values (p_id, trim(p_title), trim(p_description), coalesce(p_image_url, ''), p_category, p_starts_at,
    trim(p_location), trim(p_organizer), me.id, me.display_name, me.profile_image_url, p_participant_limit,
    p_talent_fee, case when p_talent_fee = 'paid' then budget else '' end);
  return p_id;
end $$;

-- =========================================================
-- Event applications
-- =========================================================

alter table public.notifications drop constraint notifications_type_check;
alter table public.notifications add constraint notifications_type_check check (type in (
  'like', 'comment', 'follow', 'event_join', 'group_join', 'group_request', 'group_approved', 'group_post',
  'invitation', 'invitation_accepted', 'invitation_declined', 'application', 'application_accepted', 'application_declined',
  'event_application', 'event_application_accepted', 'event_application_declined'));

-- One application per Creator and event, under the Creator's name. A withdrawn application is deleted, and so is
-- an accepted one when its Creator leaves, so they can apply again; a declined one stays.
create table public.event_applications (
  event_id text not null references public.events (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  seq bigint generated always as identity unique,
  display_name text not null default '',
  profile_image_url text not null default '',
  note text not null default '' check (char_length(note) <= 500),
  status text not null default 'pending' check (status in ('pending', 'accepted', 'declined')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (event_id, user_id)
);
create index event_applications_event_idx on public.event_applications (event_id, seq desc);
create index event_applications_user_idx on public.event_applications (user_id, seq desc);

create function private.is_event_organizer(event_ref text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.events e where e.id = event_ref and e.organizer_id = auth.uid());
$$;

alter table public.event_applications enable row level security;
revoke all on table public.event_applications from anon, authenticated;
grant select on table public.event_applications to authenticated;
create policy "Applicants and organizers read applications" on public.event_applications for select to authenticated
  using (user_id = (select auth.uid()) or private.is_event_organizer(event_id));

-- "Join event": a Creator asks for a slot. Applying again while one is pending is a no-op.
create function public.apply_to_event(p_id text, p_note text default '') returns void
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  e public.events;
  current_status text;
  body text := btrim(coalesce(p_note, ''));
begin
  if me.account_type is distinct from 'student' then raise exception 'Only Creators can apply to perform at events.'; end if;
  select * into e from public.events where id = p_id;
  if not found or private.is_blocked(e.organizer_id, me.id) then raise exception 'This event is no longer available.'; end if;
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

create function public.withdraw_event_application(p_id text) returns void
language sql security definer set search_path = '' as $$
  delete from public.event_applications where event_id = p_id and user_id = private.me() and status = 'pending';
$$;

-- The organizer answers each application. Accepting takes one slot and adds the Creator to the event and its chat.
create function public.review_event_application(p_id text, p_applicant uuid, p_accept boolean) returns void
language plpgsql security definer set search_path = '' as $$
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

-- Joining goes through an application now; this only leaves. Older app versions get a message to update.
create or replace function public.set_event_joined(p_id text, p_joined boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  e public.events;
begin
  if p_joined then raise exception 'Events now take applications. Update SPOT to apply.'; end if;
  select * into e from public.events where id = p_id for update;
  if not found then raise exception 'This event is no longer available.'; end if;
  delete from public.event_applications where event_id = p_id and user_id = me.id and status = 'accepted';
  delete from public.event_participants where event_id = p_id and user_id = me.id;
  if not found then return; end if;
  update public.events set participant_count = greatest(0, participant_count - 1) where id = p_id;
  perform private.community_chat_member('event', p_id, me.id, false);
end $$;

-- Invitations are answered in Messages now, so the duplicate message points there.
create or replace function public.create_talent_request(p_kind text, p_source_id text, p_creator_id uuid, p_details text)
returns void
language plpgsql security definer set search_path = '' as $$
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

-- =========================================================
-- Collaborator suggestions
-- =========================================================

-- Where someone is based and who they'd like to work with, set from the Collabs tab. Someone who isn't open to
-- collaborations isn't suggested to anyone.
alter table public.profiles
  add column location text not null default '' check (char_length(location) <= 80),
  add column collab_talents text[] not null default '{}' check (cardinality(collab_talents) <= 8),
  add column open_to_collab boolean not null default true;

create function public.set_collab_preferences(p_location text, p_talents text[], p_open boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := private.me();
begin
  if char_length(btrim(coalesce(p_location, ''))) > 80 then raise exception 'Keep your location to 80 characters.'; end if;
  if coalesce(cardinality(p_talents), 0) > 8
    or exists (select 1 from unnest(p_talents) t where char_length(btrim(t)) not between 1 and 60) then
    raise exception 'Choose up to 8 talents.';
  end if;
  update public.profiles set
    location = btrim(coalesce(p_location, '')),
    collab_talents = coalesce((select array_agg(distinct btrim(t)) from unnest(p_talents) t), '{}'),
    open_to_collab = coalesce(p_open, true),
    updated_at = now()
  where id = me;
end $$;

-- Suggestions someone hid; they stay hidden until undone.
create table public.collab_dismissals (
  user_id uuid not null references public.profiles (id) on delete cascade,
  dismissed_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, dismissed_id),
  check (user_id <> dismissed_id)
);
create index collab_dismissals_dismissed_idx on public.collab_dismissals (dismissed_id);
alter table public.collab_dismissals enable row level security;
revoke all on table public.collab_dismissals from anon, authenticated;
grant select, insert, delete on table public.collab_dismissals to authenticated;
create policy "Owners read dismissals" on public.collab_dismissals for select to authenticated
  using (user_id = (select auth.uid()));
create policy "Owners dismiss" on public.collab_dismissals for insert to authenticated
  with check (user_id = (select auth.uid()));
create policy "Owners undo dismissals" on public.collab_dismissals for delete to authenticated
  using (user_id = (select auth.uid()));

-- Talents that work well together (both directions), using SPOT's category names.
create table private.complementary_talents (
  talent text not null,
  partner text not null,
  primary key (talent, partner)
);
insert into private.complementary_talents (talent, partner)
select a, b from (values
  ('Singing', 'Guitar'), ('Singing', 'Piano'), ('Singing', 'Drums'), ('Singing', 'Music'), ('Singing', 'Dancing'),
  ('Guitar', 'Drums'), ('Guitar', 'Piano'), ('Guitar', 'Music'), ('Piano', 'Music'), ('Drums', 'Music'),
  ('Dancing', 'Music'), ('Dancing', 'Photography'), ('Photography', 'Art'), ('Photography', 'Painting'),
  ('Photography', 'Drawing'), ('Photography', 'Cooking'), ('Photography', 'Basketball'), ('Photography', 'Volleyball'),
  ('Photography', 'Futsal'), ('Drawing', 'Painting'), ('Drawing', 'Art'), ('Drawing', 'Coding'), ('Painting', 'Art'),
  ('Art', 'Coding'), ('Coding', 'Music'), ('Coding', 'Photography')
) pairs (a, b)
union select b, a from (values
  ('Singing', 'Guitar'), ('Singing', 'Piano'), ('Singing', 'Drums'), ('Singing', 'Music'), ('Singing', 'Dancing'),
  ('Guitar', 'Drums'), ('Guitar', 'Piano'), ('Guitar', 'Music'), ('Piano', 'Music'), ('Drums', 'Music'),
  ('Dancing', 'Music'), ('Dancing', 'Photography'), ('Photography', 'Art'), ('Photography', 'Painting'),
  ('Photography', 'Drawing'), ('Photography', 'Cooking'), ('Photography', 'Basketball'), ('Photography', 'Volleyball'),
  ('Photography', 'Futsal'), ('Drawing', 'Painting'), ('Drawing', 'Art'), ('Drawing', 'Coding'), ('Painting', 'Art'),
  ('Art', 'Coding'), ('Coding', 'Music'), ('Coding', 'Photography')
) pairs (a, b);

-- Creators who are open to collaborating, best match first, each with a short reason. Points:
--   3 per talent of theirs you're looking for (a Spotter's sought talents count too)
--   2 per talent of yours they're looking for
--   2 per talent of theirs that complements one of yours, up to 3
--   1 per talent you share, up to 3, and 1 per interest your posts share with their public posts, up to 3
--   2 for the same location, 1 for the same course
-- Yourself, blocked accounts, people who aren't open to collaborating and dismissed suggestions are left out.
create function public.suggest_collaborators(p_limit integer default 30)
returns table (user_id uuid, display_name text, username text, avatar_url text, talents text[],
  score double precision, reason text)
language sql stable security definer set search_path = '' as $$
  with me as (
    select p.id, p.course, lower(btrim(p.location)) as place,
      coalesce((select array_agg(t.talent) from public.user_talents t where t.user_id = p.id and t.confirmed), '{}') as talents,
      p.collab_talents || p.seeking_talents as wants,
      coalesce((select array_agg(distinct c) from public.posts x, unnest(x.categories) c
        where x.user_id = p.id or x.id in (select i.post_id from public.post_interactions i where i.user_id = p.id and i.liked)),
        '{}') as interests
    from public.profiles p where p.id = auth.uid()
  ), candidates as (
    select p.id, p.display_name, coalesce(p.username, '') as username, p.profile_image_url, p.course, p.location,
      lower(btrim(p.location)) as place, p.collab_talents as wants,
      array_agg(t.talent order by t.confidence desc, t.talent) as talents
    from me
    join public.profiles p on p.id <> me.id and p.profile_completed and p.open_to_collab
    join public.user_talents t on t.user_id = p.id and t.confirmed
    where not private.is_blocked(p.id, me.id)
      and not exists (select 1 from public.collab_dismissals d where d.user_id = me.id and d.dismissed_id = p.id)
    group by p.id
  ), signals as (
    select c.*,
      array(select x from unnest(c.talents) x where x = any (me.wants)) as relevant,
      array(select x from unnest(me.talents) x where x = any (c.wants)) as wanted,
      array(select k.talent || ' complements your ' || k.partner from private.complementary_talents k
        where k.talent = any (c.talents) and k.talent <> all (me.talents) and k.partner = any (me.talents)
        order by k.talent, k.partner) as complements,
      array(select x from unnest(c.talents) x where x = any (me.talents)) as shared,
      array(select distinct cat from public.posts x, unnest(x.categories) cat
        where x.user_id = c.id and x.audience = 'public' and cat = any (me.interests)) as interests,
      me.place <> '' and c.place = me.place as nearby,
      me.course <> '' and c.course = me.course as classmate
    from candidates c cross join me
  ), scored as (
    select s.*, (3 * cardinality(s.relevant) + 2 * cardinality(s.wanted) + 2 * least(cardinality(s.complements), 3)
      + least(cardinality(s.shared), 3) + least(cardinality(s.interests), 3)
      + case when s.nearby then 2 else 0 end + case when s.classmate then 1 else 0 end)::double precision as points,
      array_remove(array[
        case when cardinality(s.relevant) > 0 then s.relevant[1] || ' is a talent you''re looking for' end,
        case when cardinality(s.wanted) > 0 then 'Looking for ' || s.wanted[1] || ', like you do' end,
        case when cardinality(s.complements) > 0 then s.complements[1] end,
        case when s.nearby then 'Also in ' || btrim(s.location) end,
        case when cardinality(s.interests) > 0 then 'Also into ' || s.interests[1] end,
        case when cardinality(s.shared) > 0 then 'You both do ' || s.shared[1] end,
        case when s.classmate then 'Also in ' || s.course end
      ], null) as reasons
    from signals s
  )
  select s.id, s.display_name, s.username, nullif(s.profile_image_url, ''), s.talents, s.points,
    coalesce(nullif(array_to_string(s.reasons[1:2], ' · '), ''), 'Brings ' || s.talents[1] || ' to a collab')
  from scored s
  order by s.points desc, cardinality(s.talents) desc, s.display_name, s.id
  limit least(greatest(coalesce(p_limit, 30), 1), 50);
$$;

-- Older app versions: honor who's open to collaborating and dismissed suggestions there too.
create or replace function public.get_collaboration_suggestions()
returns table (user_id uuid, display_name text, avatar_url text, talent text, compatibility_score double precision)
language sql stable set search_path = '' as $$
  with scored as (
    select t.user_id as creator, p.display_name as name, nullif(p.profile_image_url, '') as photo, t.talent as skill,
      round((t.confidence * case when exists (select 1 from public.user_talents mine
        where mine.user_id = auth.uid() and mine.talent = t.talent) then 0.5 else 1 end)::numeric, 3)::double precision as score
    from public.user_talents t
    join public.profiles p on p.id = t.user_id
    where t.user_id <> auth.uid() and t.confirmed and p.profile_completed and p.open_to_collab
      and not private.is_blocked(t.user_id, auth.uid())
      and not exists (select 1 from public.collab_dismissals d where d.user_id = auth.uid() and d.dismissed_id = t.user_id)
  ), ranked as (
    select s.*, dense_rank() over (order by s.best desc, s.name, s.creator) as creator_rank
    from (select scored.*, max(scored.score) over (partition by scored.creator) as best from scored) s
  )
  select r.creator, r.name, r.photo, r.skill, r.score from ranked r
  where r.creator_rank <= 30
  order by r.creator_rank, r.score desc, r.skill;
$$;

-- =========================================================
-- API access and realtime
-- =========================================================

revoke all on function public.create_event(text, text, text, text, text, timestamptz, text, text, integer, text, text),
  public.apply_to_event(text, text), public.withdraw_event_application(text),
  public.review_event_application(text, uuid, boolean), public.set_collab_preferences(text, text[], boolean),
  public.suggest_collaborators(integer) from public, anon;
grant execute on function public.create_event(text, text, text, text, text, timestamptz, text, text, integer, text, text),
  public.apply_to_event(text, text), public.withdraw_event_application(text),
  public.review_event_application(text, uuid, boolean), public.set_collab_preferences(text, text[], boolean),
  public.suggest_collaborators(integer) to authenticated;
revoke all on function private.is_event_organizer(text) from public, anon, authenticated;
grant execute on function private.is_event_organizer(text) to authenticated;
revoke all on table private.complementary_talents from public, anon, authenticated;

alter publication supabase_realtime add table public.event_applications, public.collab_dismissals;
