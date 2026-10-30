-- Events and groups become places to talk. Each one has a chat for the people in it, groups have a page of
-- posts, and the people behind them hear about what happens: an organizer when someone joins, an owner about
-- joins, requests and posts, and both sides of an invitation or application. An event invitation also arrives
-- as a message in the direct chat between the Spotter and the creator.

-- =========================================================
-- Notifications
-- =========================================================

alter table public.notifications drop constraint notifications_type_check;
alter table public.notifications add constraint notifications_type_check check (type in (
  'like', 'comment', 'follow', 'event_join', 'group_join', 'group_request', 'group_approved', 'group_post',
  'invitation', 'invitation_accepted', 'invitation_declined', 'application', 'application_accepted', 'application_declined'));
-- What the notification opens: an event, a group, an invitation (its talent request) or an application's opportunity.
alter table public.notifications add column target_id text not null default '';

-- Like private.notify, for notifications about an event, group or request. Blocked people aren't notified.
create function private.notify_about(recipient uuid, notification_id text, kind text, target text, body text)
returns void language sql security definer set search_path = '' as $$
  insert into public.notifications (recipient_id, id, type, sender_id, sender_name, sender_profile_image_url, target_id, message)
  select recipient, notification_id, kind, p.id, p.display_name, p.profile_image_url, target, left(body, 300)
  from public.profiles p
  where p.id = auth.uid() and recipient <> auth.uid() and not private.is_blocked(recipient, p.id)
  on conflict (recipient_id, id) do nothing;
$$;

-- =========================================================
-- Event and group chats
-- =========================================================

-- A community chat is a group conversation whose members are the people in the event or group.
alter table public.conversations
  add column community_kind text check (community_kind in ('event', 'group')),
  add column community_id text,
  add constraint conversations_community_check check ((community_kind is null) = (community_id is null));
create unique index conversations_community_idx on public.conversations (community_kind, community_id);

-- They hold everyone who joined, and carry the event's or group's full name.
alter table public.conversations drop constraint conversations_party_ids_check;
alter table public.conversations add constraint conversations_party_ids_check
  check (cardinality(party_ids) >= 1 and (community_kind is not null or cardinality(party_ids) <= 8));
alter table public.conversations drop constraint conversations_title_check;
alter table public.conversations add constraint conversations_title_check check (char_length(title) <= 100);

-- {"<uid>": {"displayName", "username", "profileImageUrl"}}, the snapshot conversations keep of their people.
create function private.chat_people(ids uuid[]) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_object_agg(p.id::text, jsonb_build_object('displayName', p.display_name,
    'username', coalesce(p.username, ''), 'profileImageUrl', p.profile_image_url)), '{}'::jsonb)
  from public.profiles p where p.id = any (ids);
$$;

-- The chat of an event or group, started on first use with everyone who is in it.
create function private.community_chat(p_kind text, p_id text) returns text
language plpgsql security definer set search_path = '' as $$
declare
  chat_id text;
  host uuid;
  heading text;
  ids uuid[];
begin
  select c.id into chat_id from public.conversations c where c.community_kind = p_kind and c.community_id = p_id;
  if found then return chat_id; end if;
  if p_kind = 'event' then
    select e.organizer_id, e.title into host, heading from public.events e where e.id = p_id;
    select array_agg(p.user_id order by p.seq) into ids from public.event_participants p
    where p.event_id = p_id and p.user_id <> host;
  else
    select g.owner_id, g.name into host, heading from public.groups g where g.id = p_id;
    select array_agg(m.user_id order by m.seq) into ids from public.group_members m
    where m.group_id = p_id and m.user_id <> host;
  end if;
  if host is null then raise exception 'This chat is no longer available.'; end if;
  ids := array[host] || coalesce(ids, '{}');
  insert into public.conversations (id, kind, title, creator_id, party_ids, member_ids, people, community_kind, community_id)
  values (gen_random_uuid()::text, 'group', heading, host, ids, ids, private.chat_people(ids), p_kind, p_id)
  on conflict (community_kind, community_id) do nothing;
  select c.id into chat_id from public.conversations c where c.community_kind = p_kind and c.community_id = p_id;
  return chat_id;
end $$;

-- Joining or leaving an event or group does the same in its chat. Its organizer or owner always stays, and
-- someone who left keeps their name on the messages they sent.
create function private.community_chat_member(p_kind text, p_id text, p_user uuid, p_joined boolean) returns text
language plpgsql security definer set search_path = '' as $$
declare chat_id text := private.community_chat(p_kind, p_id);
begin
  if p_joined then
    update public.conversations c set
      member_ids = array_append(c.member_ids, p_user),
      party_ids = case when p_user = any (c.party_ids) then c.party_ids else array_append(c.party_ids, p_user) end,
      people = c.people || private.chat_people(array[p_user])
    where c.id = chat_id and not (p_user = any (c.member_ids));
  else
    update public.conversations c set
      member_ids = array_remove(c.member_ids, p_user), party_ids = array_remove(c.party_ids, p_user)
    where c.id = chat_id and c.creator_id <> p_user;
  end if;
  return chat_id;
end $$;

-- Opens the chat from the event or group page, for its organizer, participants or members.
create function public.open_community_chat(p_kind text, p_id text) returns text
language plpgsql security definer set search_path = '' as $$
declare me uuid := private.me();
begin
  if p_kind = 'event' then
    if not private.can_see_participants(p_id) then raise exception 'Join this event to open its chat.'; end if;
  elsif p_kind = 'group' then
    if not private.is_group_member(p_id) then raise exception 'Join this group to open its chat.'; end if;
  else
    raise exception 'This chat is no longer available.';
  end if;
  return private.community_chat_member(p_kind, p_id, me, true);
end $$;

-- The direct chat between the caller and someone else, with both in it; started if they have none yet.
create function private.direct_chat(p_other uuid) returns text
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  chat_id text;
begin
  select c.id into chat_id from public.conversations c
  where c.kind = 'direct' and not c.demo and me = any (c.party_ids) and p_other = any (c.party_ids)
  order by (me = any (c.member_ids) and p_other = any (c.member_ids)) desc, c.updated_at desc
  limit 1;
  if chat_id is null then
    chat_id := gen_random_uuid()::text;
    insert into public.conversations (id, kind, creator_id, party_ids, member_ids, people)
    values (chat_id, 'direct', me, array[me, p_other], array[me, p_other], private.chat_people(array[me, p_other]));
  else
    update public.conversations c set
      member_ids = array(select distinct x from unnest(c.member_ids || array[me, p_other]) x),
      invited_ids = array_remove(array_remove(c.invited_ids, me), p_other)
    where c.id = chat_id and not (me = any (c.member_ids) and p_other = any (c.member_ids));
  end if;
  return chat_id;
end $$;

-- A community chat can be used by whoever is there, even before anyone else joins.
create or replace function public.send_message(p_conversation_id text, p_message_id text, p_text text, p_attachment jsonb default null)
returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  chat public.conversations;
  body text := trim(coalesce(p_text, ''));
  a jsonb := case when jsonb_typeof(p_attachment) = 'object' then p_attachment end;
begin
  if exists (select 1 from public.messages where conversation_id = p_conversation_id and id = p_message_id) then return; end if;
  select * into chat from public.conversations where id = p_conversation_id for update;
  if not found or not (me = any (chat.member_ids)) then
    raise exception 'Join this conversation before sending a message.';
  end if;
  if not chat.demo and ((chat.community_kind is null and cardinality(chat.member_ids) < 2)
    or (chat.kind = 'direct' and private.is_blocked(chat.party_ids[1], chat.party_ids[2]))) then
    raise exception 'Messages couldn''t access this conversation. Check that your invitation was accepted and try again.';
  end if;
  if p_message_id !~ '^[A-Za-z0-9_-]{1,100}$' then raise exception 'Please check your message and retry.'; end if;
  if char_length(body) > 4000 then raise exception 'Keep your message under 4,000 characters.'; end if;
  if body = '' and a is null then raise exception 'Write a message or choose a photo or video.'; end if;
  if a is not null and not (
    a ->> 'path' = 'chat_media/' || p_conversation_id || '/' || me::text || '/' || p_message_id
    and coalesce(a ->> 'mime', '') ~ '^(image|video)/[a-zA-Z0-9.+-]{1,80}$'
    and char_length(coalesce(a ->> 'name', '')) <= 120
    and coalesce((a ->> 'size')::bigint, 0) between 1 and 25 * 1024 * 1024
    and coalesce(a ->> 'url', '') ~ ('^https://res[.]cloudinary[.]com/jcuzzxsz/raw/upload/v[0-9]+/spot/chat_media/'
      || p_conversation_id || '/' || me::text || '/' || p_message_id || '/[a-zA-Z0-9_-]+([.][a-zA-Z0-9]{1,8})?$')
    and coalesce(a ->> 'key', '') ~ '^[a-zA-Z0-9+/]{43}=$'
    and coalesce(a ->> 'iv', '') ~ '^[a-zA-Z0-9+/]{16}$'
  ) then
    raise exception 'The media service returned an invalid attachment. Retry.';
  end if;
  insert into public.messages (conversation_id, id, sender_id, text, attachment)
  values (p_conversation_id, p_message_id, me::text, body, a);
  update public.conversations set
    preview = coalesce(nullif(left(body, 160), ''), case when a ->> 'mime' like 'video/%' then 'Video' else 'Photo' end),
    last_sender_id = me::text, last_message_id = p_message_id, updated_at = now()
  where id = p_conversation_id;
end $$;

-- A community chat follows the event or group: leaving there is how you leave its chat.
create or replace function public.leave_conversation(p_id text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  chat public.conversations;
begin
  select * into chat from public.conversations where id = p_id and kind = 'group' and me = any (member_ids);
  if not found then raise exception 'Only group conversations can be left.'; end if;
  if chat.community_kind is not null then
    raise exception 'Leave the % to leave its chat. You can mute it here instead.', chat.community_kind;
  end if;
  update public.conversations set member_ids = array_remove(member_ids, me) where id = p_id;
end $$;

-- =========================================================
-- Events
-- =========================================================

create or replace function public.set_event_joined(p_id text, p_joined boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  e public.events;
  is_member boolean;
begin
  select * into e from public.events where id = p_id for update;
  if not found then raise exception 'This event is no longer available.'; end if;
  is_member := exists (select 1 from public.event_participants where event_id = p_id and user_id = me.id);
  if is_member = p_joined then return; end if;
  if p_joined then
    if e.starts_at <= now() then raise exception 'Registration has closed.'; end if;
    if e.participant_limit is not null and e.participant_count >= e.participant_limit then
      raise exception 'This event is full.';
    end if;
    insert into public.event_participants (event_id, user_id, display_name, profile_image_url)
    values (p_id, me.id, me.display_name, me.profile_image_url);
  else
    delete from public.event_participants where event_id = p_id and user_id = me.id;
  end if;
  update public.events set participant_count = greatest(0, participant_count + case when p_joined then 1 else -1 end)
  where id = p_id;
  perform private.community_chat_member('event', p_id, me.id, p_joined);
  if p_joined then
    perform private.notify_about(e.organizer_id, 'event_join_' || p_id || '_' || me.id::text, 'event_join', p_id,
      'joined your event ' || e.title);
  end if;
end $$;

-- =========================================================
-- Groups
-- =========================================================

create or replace function public.request_join_group(p_id text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  g public.groups;
begin
  select * into g from public.groups where id = p_id;
  if not found then raise exception 'This group is no longer available.'; end if;
  if g.privacy <> 'private' then raise exception 'This group uses direct joining.'; end if;
  if exists (select 1 from public.group_members where group_id = p_id and user_id = me.id) then return; end if;
  insert into public.group_join_requests (group_id, user_id, display_name, profile_image_url)
  values (p_id, me.id, me.display_name, me.profile_image_url) on conflict do nothing;
  perform private.notify_about(g.owner_id, 'group_request_' || p_id || '_' || me.id::text, 'group_request', p_id,
    'asked to join ' || g.name);
end $$;

create or replace function public.set_group_joined(p_id text, p_joined boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  g public.groups;
  is_member boolean;
begin
  select * into g from public.groups where id = p_id for update;
  if not found then raise exception 'This group is no longer available.'; end if;
  if g.owner_id = me.id then raise exception 'The owner stays in the group as its admin.'; end if;
  is_member := exists (select 1 from public.group_members where group_id = p_id and user_id = me.id);
  if is_member = p_joined then return; end if;
  if p_joined then
    if g.privacy <> 'public' then raise exception 'Request to join this private group first.'; end if;
    insert into public.group_members (group_id, user_id, display_name, profile_image_url)
    values (p_id, me.id, me.display_name, me.profile_image_url);
  else
    delete from public.group_members where group_id = p_id and user_id = me.id;
  end if;
  update public.groups set member_count = greatest(0, member_count + case when p_joined then 1 else -1 end) where id = p_id;
  perform private.community_chat_member('group', p_id, me.id, p_joined);
  if p_joined then
    perform private.notify_about(g.owner_id, 'group_join_' || p_id || '_' || me.id::text, 'group_join', p_id,
      'joined your group ' || g.name);
  end if;
end $$;

create or replace function public.review_group_request(p_id text, p_applicant uuid, p_approve boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  g public.groups;
  request public.group_join_requests;
begin
  select * into g from public.groups where id = p_id for update;
  if not found or g.owner_id <> me then raise exception 'Only the group owner can review requests.'; end if;
  delete from public.group_join_requests where group_id = p_id and user_id = p_applicant returning * into request;
  if not found or not p_approve then return; end if;
  insert into public.group_members (group_id, user_id, display_name, profile_image_url)
  values (p_id, p_applicant, request.display_name, request.profile_image_url) on conflict do nothing;
  if found then update public.groups set member_count = member_count + 1 where id = p_id; end if;
  perform private.community_chat_member('group', p_id, p_applicant, true);
  perform private.notify_about(p_applicant, 'group_approved_' || p_id, 'group_approved', p_id,
    'approved your request to join ' || g.name);
end $$;

-- =========================================================
-- Group posts
-- =========================================================

-- A group's page: what its members share with each other. Author details are captured when posting.
create table public.group_posts (
  id text primary key check (id ~ '^[A-Za-z0-9_-]{1,100}$'),
  seq bigint generated always as identity unique,
  group_id text not null references public.groups (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  display_name text not null default '',
  profile_image_url text not null default '',
  text text not null default '' check (char_length(text) <= 3000),
  image_url text not null default '' check (char_length(image_url) <= 2000),
  likes_count integer not null default 0 check (likes_count >= 0),
  created_at timestamptz not null default now(),
  check (text <> '' or image_url <> '')
);
create index group_posts_group_idx on public.group_posts (group_id, seq desc);
create index group_posts_user_idx on public.group_posts (user_id);

create table public.group_post_likes (
  post_id text not null references public.group_posts (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  primary key (post_id, user_id)
);
create index group_post_likes_user_idx on public.group_post_likes (user_id);

alter table public.group_posts enable row level security;
alter table public.group_post_likes enable row level security;
revoke all on table public.group_posts, public.group_post_likes from anon, authenticated;
grant select on table public.group_posts, public.group_post_likes to authenticated;

-- A public group's page is open to read; a private group's is for its members.
create policy "Group audience reads posts" on public.group_posts for select to authenticated
  using (private.group_is_public(group_id) or private.is_group_member(group_id));
create policy "Owners read group post likes" on public.group_post_likes for select to authenticated
  using (user_id = (select auth.uid()));

-- Retrying with the same post id is a no-op, so a lost acknowledgement never posts twice.
create function public.create_group_post(p_id text, p_group_id text, p_text text, p_image_url text default '')
returns void
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  g public.groups;
  body text := trim(coalesce(p_text, ''));
  image text := coalesce(p_image_url, '');
begin
  if p_id !~ '^[A-Za-z0-9_-]{1,100}$' then raise exception 'Please check your post and retry.'; end if;
  if exists (select 1 from public.group_posts where id = p_id) then return; end if;
  select * into g from public.groups where id = p_group_id;
  if not found or not private.is_group_member(p_group_id) then raise exception 'Join this group to post in it.'; end if;
  if char_length(body) > 3000 then raise exception 'Keep your post under 3,000 characters.'; end if;
  if body = '' and image = '' then raise exception 'Write something or add a photo.'; end if;
  if image <> '' and not private.cloudinary_url(image, 'image', 'group_posts', me.id, p_id) then
    raise exception 'Image upload failed. Please retry.';
  end if;
  insert into public.group_posts (id, group_id, user_id, display_name, profile_image_url, text, image_url)
  values (p_id, p_group_id, me.id, me.display_name, me.profile_image_url, body, image);
  perform private.notify_about(g.owner_id, 'group_post_' || p_id, 'group_post', p_group_id, 'posted in ' || g.name);
end $$;

-- Authors remove their own posts; the group's owner can remove any.
create function public.delete_group_post(p_id text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  post public.group_posts;
begin
  select * into post from public.group_posts where id = p_id;
  if not found then return; end if;
  if post.user_id <> me and not private.is_group_owner(post.group_id) then
    raise exception 'You can only delete your own posts.';
  end if;
  delete from public.group_posts where id = p_id;
end $$;

create function public.set_group_post_liked(p_id text, p_liked boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  post public.group_posts;
begin
  select * into post from public.group_posts where id = p_id for update;
  if not found or not private.is_group_member(post.group_id) then raise exception 'Join this group to like its posts.'; end if;
  if p_liked then
    insert into public.group_post_likes (post_id, user_id) values (p_id, me) on conflict do nothing;
  else
    delete from public.group_post_likes where post_id = p_id and user_id = me;
  end if;
  if found then
    update public.group_posts set likes_count = greatest(0, likes_count + case when p_liked then 1 else -1 end) where id = p_id;
  end if;
end $$;

-- =========================================================
-- Work: invitations and applications reach the other person
-- =========================================================

-- The source decides the seeker and title, so requests can't be forged. The other person is notified, and an
-- invitation is also sent as a message ("spot://event/<id>" and the details) so the two can talk it over.
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
  if not found then raise exception 'A request already exists. Open Notifications to see its status.'; end if;
  if p_kind = 'invitation' then
    perform private.notify_about(p_creator_id, request_id, 'invitation', request_id, 'invited you to ' || heading);
    perform public.send_message(private.direct_chat(p_creator_id), gen_random_uuid()::text,
      'spot://event/' || p_source_id || E'\n' || trim(p_details), null);
  else
    perform private.notify_about(seeker, request_id, 'application', p_source_id, 'applied to ' || heading);
  end if;
end $$;

-- Whoever sent the request hears the answer. Accepting an invitation puts the creator in the event and its
-- chat; the organizer asked for them, so the participant limit doesn't apply.
create or replace function public.respond_talent_request(p_id text, p_status text) returns void
language plpgsql security definer set search_path = '' as $$
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
    (r.kind = 'invitation' and exists (select 1 from public.events where id = r.source_id and starts_at > now()))
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

-- =========================================================
-- API access and realtime
-- =========================================================

revoke all on function public.open_community_chat(text, text), public.create_group_post(text, text, text, text),
  public.delete_group_post(text), public.set_group_post_liked(text, boolean) from public, anon;
grant execute on function public.open_community_chat(text, text), public.create_group_post(text, text, text, text),
  public.delete_group_post(text), public.set_group_post_liked(text, boolean) to authenticated;
revoke all on function private.notify_about(uuid, text, text, text, text), private.chat_people(uuid[]),
  private.community_chat(text, text), private.community_chat_member(text, text, uuid, boolean),
  private.direct_chat(uuid) from public, anon, authenticated;

alter publication supabase_realtime add table public.group_posts, public.group_post_likes;
