-- SPOT on Supabase: tables, row level security and server-side actions.
--
-- Firestore kept SPOT's invariants (counters, notifications, memberships,
-- audiences) with client transactions that security rules re-checked. Here the
-- app reads through RLS, and every multi-row write is one SECURITY DEFINER
-- function, so each action is atomic and clients cannot forge counters.
--
-- Errors raised with a plain message (SQLSTATE P0001) are shown to the user.

create schema if not exists private;
grant usage on schema private to authenticated;

-- =========================================================
-- Accounts
-- =========================================================

-- One row per auth user. Email stays in auth.users, so every column is public
-- to signed-in members (Firestore needed separate users/publicProfiles docs).
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  full_name text not null default '',
  display_name text not null default '' check (char_length(display_name) <= 200),
  username text unique check (username ~ '^[a-z0-9_]{3,30}$'),
  course text not null default '' check (char_length(course) <= 120),
  talent_categories text[] not null default '{}' check (cardinality(talent_categories) <= 20),
  bio text not null default '' check (char_length(bio) <= 160),
  profile_image_url text not null default '' check (char_length(profile_image_url) <= 2000),
  account_type text check (account_type in ('student', 'talent_seeker')),
  organization text not null default '' check (char_length(organization) <= 80),
  seeking_talents text[] not null default '{}' check (cardinality(seeking_talents) <= 20),
  pronouns text not null default '' check (char_length(pronouns) <= 40),
  links text not null default '' check (char_length(links) <= 300 and (links = '' or links like 'https://%')),
  gender text not null default '' check (char_length(gender) <= 40),
  is_private boolean not null default false,
  profile_completed boolean not null default false,
  name_key text generated always as (lower(display_name)) stored,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index profiles_name_key_idx on public.profiles (name_key);
create index profiles_talents_idx on public.profiles using gin (talent_categories);

create table public.user_settings (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  story_audience text not null default 'followers' check (story_audience in ('public', 'followers', 'private')),
  post_audience text not null default 'followers' check (post_audience in ('public', 'followers', 'private')),
  likes boolean not null default true,
  comments boolean not null default true,
  follows boolean not null default true
);

create table public.follows (
  follower_id uuid not null references public.profiles (id) on delete cascade,
  following_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower_id, following_id),
  check (follower_id <> following_id)
);
create index follows_following_idx on public.follows (following_id, created_at desc);
create index follows_follower_idx on public.follows (follower_id, created_at desc);

create table public.follow_requests (
  requester_id uuid not null references public.profiles (id) on delete cascade,
  target_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (requester_id, target_id),
  check (requester_id <> target_id)
);
create index follow_requests_target_idx on public.follow_requests (target_id, created_at desc);

create table public.blocks (
  blocker_id uuid not null references public.profiles (id) on delete cascade,
  blocked_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);

-- =========================================================
-- Posts, comments and notifications
-- =========================================================

-- seq is a stable, unique keyset for paging live feeds (Firestore used snapshots).
create table public.posts (
  id text primary key check (id ~ '^[A-Za-z0-9_-]{1,100}$'),
  seq bigint generated always as identity unique,
  user_id uuid not null references public.profiles (id) on delete cascade,
  caption text not null default '' check (char_length(caption) <= 2000),
  categories text[] not null default '{}' check (cardinality(categories) <= 8),
  tags text[] not null default '{}' check (cardinality(tags) <= 20),
  media jsonb not null default '[]' check (jsonb_typeof(media) = 'array' and jsonb_array_length(media) <= 10),
  has_media boolean generated always as (jsonb_array_length(media) > 0) stored,
  media_type text not null default 'image' check (media_type in ('image', 'video')),
  audience text not null check (audience in ('public', 'followers', 'private')),
  location text not null default '' check (char_length(location) <= 160),
  tagged_people text[] not null default '{}' check (cardinality(tagged_people) <= 20),
  author_display_name text not null default '',
  author_username text not null default '',
  author_talent text not null default '',
  author_course text not null default '',
  author_profile_image_url text not null default '',
  likes_count bigint not null default 0 check (likes_count >= 0),
  views_count bigint not null default 0 check (views_count >= 0),
  comments_count bigint not null default 0 check (comments_count >= 0),
  shares_count bigint not null default 0 check (shares_count >= 0),
  -- A like is stronger interest than a view; matches popularityScore() in the app.
  popularity_score bigint generated always as (likes_count * 5 + views_count) stored,
  ai_caption boolean not null default false,
  ai_tagged boolean not null default false,
  ai_category_suggested boolean not null default false,
  created_at timestamptz not null default now()
);
create index posts_user_idx on public.posts (user_id, seq desc);
create index posts_public_idx on public.posts (audience, seq desc);
create index posts_popular_idx on public.posts (audience, has_media, popularity_score desc, seq desc);
create index posts_video_idx on public.posts (audience, media_type, seq desc);

create table public.saved_posts (
  user_id uuid not null references public.profiles (id) on delete cascade,
  post_id text not null references public.posts (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, post_id)
);

create table public.post_interactions (
  post_id text not null references public.posts (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  liked boolean not null default false,
  viewed boolean not null default false,
  primary key (post_id, user_id)
);
create index post_interactions_user_idx on public.post_interactions (user_id);

create table public.comments (
  id text primary key check (id ~ '^[A-Za-z0-9_-]{1,100}$'),
  seq bigint generated always as identity unique,
  post_id text not null references public.posts (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  username text not null default '',
  display_name text not null default '',
  profile_image_url text not null default '',
  text text not null default '' check (char_length(text) <= 2000),
  likes_count bigint not null default 0 check (likes_count >= 0),
  deleted boolean not null default false,
  created_at timestamptz not null default now()
);
create index comments_post_idx on public.comments (post_id, seq desc);

create table public.comment_likes (
  comment_id text not null references public.comments (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  primary key (comment_id, user_id)
);
create index comment_likes_user_idx on public.comment_likes (user_id);

-- Ids stay deterministic (like_<post>_<uid>, comment_<id>, follow_<uid>) so a
-- repeated action never produces a second notification.
create table public.notifications (
  recipient_id uuid not null references public.profiles (id) on delete cascade,
  id text not null,
  seq bigint generated always as identity unique,
  type text not null check (type in ('like', 'comment', 'follow')),
  sender_id uuid not null references public.profiles (id) on delete cascade,
  sender_name text not null default '',
  sender_profile_image_url text not null default '',
  post_id text not null default '',
  comment_id text not null default '',
  message text not null default '',
  is_read boolean not null default false,
  created_at timestamptz not null default now(),
  primary key (recipient_id, id)
);
create index notifications_recipient_idx on public.notifications (recipient_id, seq desc);
create index notifications_unread_idx on public.notifications (recipient_id) where not is_read;

-- =========================================================
-- Stories and highlights
-- =========================================================

create table public.stories (
  id text primary key check (id ~ '^[A-Za-z0-9_-]{1,100}$'),
  seq bigint generated always as identity unique,
  user_id uuid not null references public.profiles (id) on delete cascade,
  media_url text not null check (char_length(media_url) <= 2000),
  media_type text not null check (media_type in ('image', 'video')),
  audience text not null check (audience in ('public', 'followers', 'private')),
  deleted boolean not null default false,
  username text not null default '',
  profile_image_url text not null default '',
  composition jsonb,
  likes_count bigint not null default 0 check (likes_count >= 0),
  created_at timestamptz not null default now()
);
create index stories_user_idx on public.stories (user_id, seq desc);
create index stories_public_idx on public.stories (audience, seq desc);

create table public.story_reactions (
  story_id text not null references public.stories (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  primary key (story_id, user_id)
);
create index story_reactions_user_idx on public.story_reactions (user_id);

create table public.highlights (
  id text primary key check (id ~ '^[A-Za-z0-9_-]{1,100}$'),
  user_id uuid not null references public.profiles (id) on delete cascade,
  title text not null check (char_length(title) between 1 and 32),
  media jsonb not null check (jsonb_typeof(media) = 'array' and jsonb_array_length(media) between 1 and 8),
  cover integer not null default 0 check (cover >= 0),
  audience text not null check (audience in ('public', 'followers', 'private')),
  updated_at timestamptz not null default now()
);
create index highlights_user_idx on public.highlights (user_id, updated_at desc);

-- =========================================================
-- Messages
-- =========================================================

create table public.conversations (
  id text primary key check (id ~ '^[A-Za-z0-9_-]{1,200}$'),
  kind text not null check (kind in ('direct', 'group')),
  demo boolean not null default false,
  title text not null default '' check (char_length(title) <= 60),
  creator_id uuid not null references public.profiles (id) on delete cascade,
  party_ids uuid[] not null check (cardinality(party_ids) between 1 and 8),
  member_ids uuid[] not null,
  invited_ids uuid[] not null default '{}',
  -- {"<uid>": {"displayName", "username", "profileImageUrl"}} captured at creation.
  people jsonb not null default '{}',
  invite_note text not null default '' check (char_length(invite_note) <= 500),
  preview text not null default '' check (char_length(preview) <= 160),
  last_message_id text not null default '',
  last_sender_id text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index conversations_members_idx on public.conversations using gin (member_ids);
create index conversations_invited_idx on public.conversations using gin (invited_ids);

-- sender_id is a member's uid, or 'demo' for SPOT's private sample chats.
create table public.messages (
  conversation_id text not null references public.conversations (id) on delete cascade,
  id text not null check (id ~ '^[A-Za-z0-9_-]{1,100}$'),
  seq bigint generated always as identity unique,
  sender_id text not null,
  text text not null default '' check (char_length(text) <= 4000),
  attachment jsonb,
  created_at timestamptz not null default now(),
  unsent_at timestamptz,
  primary key (conversation_id, id)
);
create index messages_conversation_idx on public.messages (conversation_id, seq desc);

create table public.chat_settings (
  user_id uuid not null references public.profiles (id) on delete cascade,
  conversation_id text not null references public.conversations (id) on delete cascade,
  nickname text not null default '' check (char_length(nickname) <= 60),
  muted boolean not null default false,
  hidden_at timestamptz,
  read_at timestamptz,
  primary key (user_id, conversation_id)
);

-- "Delete for you": private markers other members never see.
create table public.hidden_messages (
  user_id uuid not null references public.profiles (id) on delete cascade,
  conversation_id text not null references public.conversations (id) on delete cascade,
  message_id text not null,
  created_at timestamptz not null default now(),
  primary key (user_id, conversation_id, message_id)
);

create table public.conversation_reports (
  conversation_id text not null references public.conversations (id) on delete cascade,
  reporter_id uuid not null references public.profiles (id) on delete cascade,
  reason text not null check (char_length(reason) between 5 and 1000),
  created_at timestamptz not null default now(),
  primary key (conversation_id, reporter_id)
);

-- =========================================================
-- Discover: groups and events
-- =========================================================

create table public.groups (
  id text primary key check (id ~ '^[A-Za-z0-9_-]{1,100}$'),
  seq bigint generated always as identity unique,
  name text not null check (char_length(name) between 2 and 80),
  name_key text generated always as (lower(name)) stored,
  description text not null check (char_length(description) between 1 and 2000),
  image_url text not null default '',
  category text not null check (char_length(category) between 1 and 60),
  privacy text not null check (privacy in ('public', 'private')),
  owner_id uuid not null references public.profiles (id) on delete cascade,
  owner_name text not null default '',
  owner_profile_image_url text not null default '',
  member_count integer not null default 1 check (member_count >= 0),
  created_at timestamptz not null default now()
);
create index groups_created_idx on public.groups (seq desc);
create index groups_name_idx on public.groups (name_key);

create table public.group_members (
  group_id text not null references public.groups (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  seq bigint generated always as identity unique,
  display_name text not null default '',
  profile_image_url text not null default '',
  role text not null default 'member' check (role in ('owner', 'member')),
  created_at timestamptz not null default now(),
  primary key (group_id, user_id)
);
create index group_members_user_idx on public.group_members (user_id);

create table public.group_join_requests (
  group_id text not null references public.groups (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  seq bigint generated always as identity unique,
  display_name text not null default '',
  profile_image_url text not null default '',
  role text not null default 'member' check (role = 'member'),
  created_at timestamptz not null default now(),
  primary key (group_id, user_id)
);
create index group_join_requests_user_idx on public.group_join_requests (user_id);

create table public.events (
  id text primary key check (id ~ '^[A-Za-z0-9_-]{1,100}$'),
  seq bigint generated always as identity unique,
  title text not null check (char_length(title) between 2 and 100),
  name_key text generated always as (lower(title)) stored,
  description text not null check (char_length(description) between 1 and 3000),
  image_url text not null default '',
  category text not null check (char_length(category) between 1 and 60),
  starts_at timestamptz not null,
  location text not null check (char_length(location) between 1 and 160),
  organizer text not null check (char_length(organizer) between 1 and 100),
  organizer_id uuid not null references public.profiles (id) on delete cascade,
  organizer_name text not null default '',
  organizer_profile_image_url text not null default '',
  participant_limit integer check (participant_limit between 1 and 100000),
  participant_count integer not null default 0 check (participant_count >= 0),
  created_at timestamptz not null default now()
);
create index events_created_idx on public.events (seq desc);
create index events_name_idx on public.events (name_key);
create index events_organizer_idx on public.events (organizer_id, starts_at desc);

create table public.event_participants (
  event_id text not null references public.events (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  seq bigint generated always as identity unique,
  display_name text not null default '',
  profile_image_url text not null default '',
  role text not null default 'member' check (role = 'member'),
  created_at timestamptz not null default now(),
  primary key (event_id, user_id)
);
create index event_participants_user_idx on public.event_participants (user_id);

-- =========================================================
-- Work: opportunities and talent requests
-- =========================================================

create table public.opportunities (
  id text primary key check (id ~ '^[A-Za-z0-9_-]{1,100}$'),
  seq bigint generated always as identity unique,
  seeker_id uuid not null references public.profiles (id) on delete cascade,
  title text not null check (char_length(title) between 2 and 100),
  kind text not null check (kind in ('job', 'audition')),
  description text not null check (char_length(description) between 10 and 3000),
  category text not null check (char_length(category) between 1 and 60),
  location text not null check (char_length(location) between 1 and 160),
  deadline timestamptz not null,
  status text not null default 'open' check (status in ('open', 'closed')),
  created_at timestamptz not null default now()
);
create index opportunities_status_idx on public.opportunities (status, seq desc);
create index opportunities_seeker_idx on public.opportunities (seeker_id, seq desc);

-- Invitations and applications are requests, not payments or contracts.
create table public.talent_requests (
  id text primary key,
  seq bigint generated always as identity unique,
  kind text not null check (kind in ('invitation', 'application')),
  source_id text not null,
  seeker_id uuid not null references public.profiles (id) on delete cascade,
  creator_id uuid not null references public.profiles (id) on delete cascade,
  title text not null check (char_length(title) between 2 and 100),
  details text not null check (char_length(details) between 10 and 2000),
  status text not null default 'pending' check (status in ('pending', 'accepted', 'declined', 'withdrawn')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (seeker_id <> creator_id)
);
create index talent_requests_seeker_idx on public.talent_requests (seeker_id, seq desc);
create index talent_requests_creator_idx on public.talent_requests (creator_id, seq desc);

-- =========================================================
-- Collaboration (AI talent detection)
-- =========================================================

create table public.user_talents (
  user_id uuid not null references public.profiles (id) on delete cascade,
  talent text not null check (char_length(talent) between 1 and 60),
  confidence double precision not null default 1 check (confidence between 0 and 1),
  source text not null default 'profile' check (source in ('profile', 'ai')),
  confirmed boolean not null default true,
  primary key (user_id, talent)
);

-- =========================================================
-- Private helpers (not exposed through the API)
-- =========================================================

create function private.display_name_from(meta jsonb) returns text
language sql immutable set search_path = '' as $$
  select left(coalesce(nullif(meta ->> 'full_name', ''), nullif(meta ->> 'name', ''), ''), 200);
$$;

-- Every auth user gets a profile row, including Google sign-ins.
create function private.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, full_name, display_name, profile_image_url)
  values (new.id, private.display_name_from(new.raw_user_meta_data), private.display_name_from(new.raw_user_meta_data),
    left(coalesce(new.raw_user_meta_data ->> 'avatar_url', new.raw_user_meta_data ->> 'picture', ''), 2000))
  on conflict (id) do nothing;
  return new;
end $$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function private.handle_new_user();

create function private.me() returns uuid
language plpgsql stable set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'Sign in to continue.'; end if;
  return auth.uid();
end $$;

-- The caller's completed profile; most actions need one.
create function private.member() returns public.profiles
language plpgsql stable security definer set search_path = '' as $$
declare p public.profiles;
begin
  select * into p from public.profiles where id = private.me();
  if not found or not p.profile_completed then raise exception 'Finish setting up your profile first.'; end if;
  return p;
end $$;

-- Mirrors Firestore's canReadPost: public, yours, or followers-only while you follow.
create function private.can_view(owner uuid, audience text) returns boolean
language sql stable security definer set search_path = '' as $$
  select auth.uid() is not null and (
    audience = 'public' or owner = auth.uid()
    or (audience = 'followers' and exists (
      select 1 from public.follows f where f.following_id = owner and f.follower_id = auth.uid())));
$$;

create function private.is_blocked(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.blocks
    where (blocker_id = a and blocked_id = b) or (blocker_id = b and blocked_id = a));
$$;

create function private.is_chat_member(conversation text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.conversations c
    where c.id = conversation and auth.uid() = any (c.member_ids));
$$;

create function private.is_group_member(group_ref text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.group_members m where m.group_id = group_ref and m.user_id = auth.uid());
$$;

create function private.is_group_owner(group_ref text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.groups g where g.id = group_ref and g.owner_id = auth.uid());
$$;

create function private.group_is_public(group_ref text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.groups g where g.id = group_ref and g.privacy = 'public');
$$;

create function private.can_see_participants(event_ref text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.events e where e.id = event_ref and e.organizer_id = auth.uid())
    or exists (select 1 from public.event_participants p where p.event_id = event_ref and p.user_id = auth.uid());
$$;

-- Uploaded media must come from SPOT's Cloudinary folder for this owner and item.
create function private.cloudinary_url(url text, kinds text, folder text, owner uuid, item text) returns boolean
language sql immutable set search_path = '' as $$
  select char_length(url) <= 2000
    and url ~ ('^https://res[.]cloudinary[.]com/jcuzzxsz/(' || kinds || ')/upload/(so_0,w_960,c_limit,q_auto/)?(v[0-9]+/)?spot/'
      || folder || '/' || owner::text || '/' || item || '/.+$');
$$;

-- Creates the caller's notification for someone else once (ids are deterministic).
create function private.notify(recipient uuid, notification_id text, kind text, post_ref text, comment_ref text, body text)
returns void language sql security definer set search_path = '' as $$
  insert into public.notifications (recipient_id, id, type, sender_id, sender_name, sender_profile_image_url, post_id, comment_id, message)
  select recipient, notification_id, kind, p.id, p.display_name, p.profile_image_url, post_ref, comment_ref, body
  from public.profiles p
  where p.id = auth.uid() and recipient <> auth.uid()
  on conflict (recipient_id, id) do nothing;
$$;

-- =========================================================
-- Row level security (reads; writes go through functions)
-- =========================================================

do $$
declare t text;
begin
  foreach t in array array[
    'profiles', 'user_settings', 'follows', 'follow_requests', 'blocks', 'posts', 'saved_posts',
    'post_interactions', 'comments', 'comment_likes', 'notifications', 'stories', 'story_reactions',
    'highlights', 'conversations', 'messages', 'chat_settings', 'hidden_messages', 'conversation_reports',
    'groups', 'group_members', 'group_join_requests', 'events', 'event_participants', 'opportunities',
    'talent_requests', 'user_talents'
  ] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on table public.%I from anon, authenticated', t);
    execute format('grant select on table public.%I to authenticated', t);
  end loop;
end $$;

create policy "Members read profiles" on public.profiles for select to authenticated using (true);

create policy "Owners read settings" on public.user_settings for select to authenticated
  using (user_id = (select auth.uid()));
create policy "Owners create settings" on public.user_settings for insert to authenticated
  with check (user_id = (select auth.uid()));
create policy "Owners update settings" on public.user_settings for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
grant insert, update on public.user_settings to authenticated;

create policy "Members read follows" on public.follows for select to authenticated using (true);

create policy "Parties read follow requests" on public.follow_requests for select to authenticated
  using ((select auth.uid()) in (requester_id, target_id));

create policy "Owners read blocks" on public.blocks for select to authenticated
  using (blocker_id = (select auth.uid()));
create policy "Owners block" on public.blocks for insert to authenticated
  with check (blocker_id = (select auth.uid()));
create policy "Owners unblock" on public.blocks for delete to authenticated
  using (blocker_id = (select auth.uid()));
grant insert, delete on public.blocks to authenticated;

create policy "Audience reads posts" on public.posts for select to authenticated
  using (private.can_view(user_id, audience));

-- The posts subquery is itself filtered by RLS, so only visible posts can be saved.
create policy "Owners read saved posts" on public.saved_posts for select to authenticated
  using (user_id = (select auth.uid()));
create policy "Owners save visible posts" on public.saved_posts for insert to authenticated
  with check (user_id = (select auth.uid()) and exists (select 1 from public.posts p where p.id = post_id));
create policy "Owners unsave posts" on public.saved_posts for delete to authenticated
  using (user_id = (select auth.uid()));
grant insert, delete on public.saved_posts to authenticated;

create policy "Owners read interactions" on public.post_interactions for select to authenticated
  using (user_id = (select auth.uid()));

create policy "Post audience reads comments" on public.comments for select to authenticated
  using (exists (select 1 from public.posts p where p.id = post_id));

create policy "Owners read comment likes" on public.comment_likes for select to authenticated
  using (user_id = (select auth.uid()));

create policy "Recipients read notifications" on public.notifications for select to authenticated
  using (recipient_id = (select auth.uid()));
create policy "Recipients mark notifications" on public.notifications for update to authenticated
  using (recipient_id = (select auth.uid())) with check (recipient_id = (select auth.uid()));
grant update (is_read) on public.notifications to authenticated;

create policy "Audience reads stories" on public.stories for select to authenticated
  using (private.can_view(user_id, audience));

create policy "Owners read story reactions" on public.story_reactions for select to authenticated
  using (user_id = (select auth.uid()));

create policy "Audience reads highlights" on public.highlights for select to authenticated
  using (private.can_view(user_id, audience));
create policy "Owners delete highlights" on public.highlights for delete to authenticated
  using (user_id = (select auth.uid()));
grant delete on public.highlights to authenticated;

create policy "Members and invitees read conversations" on public.conversations for select to authenticated
  using ((select auth.uid()) = any (member_ids) or (select auth.uid()) = any (invited_ids));

create policy "Members read messages" on public.messages for select to authenticated
  using (private.is_chat_member(conversation_id));

create policy "Owners read chat settings" on public.chat_settings for select to authenticated
  using (user_id = (select auth.uid()));

create policy "Owners read hidden messages" on public.hidden_messages for select to authenticated
  using (user_id = (select auth.uid()));
create policy "Members hide messages" on public.hidden_messages for insert to authenticated
  with check (user_id = (select auth.uid()) and private.is_chat_member(conversation_id));
grant insert on public.hidden_messages to authenticated;

create policy "Reporters read reports" on public.conversation_reports for select to authenticated
  using (reporter_id = (select auth.uid()));
create policy "Participants report conversations" on public.conversation_reports for insert to authenticated
  with check (reporter_id = (select auth.uid())
    and exists (select 1 from public.conversations c where c.id = conversation_id));
create policy "Reporters update reports" on public.conversation_reports for update to authenticated
  using (reporter_id = (select auth.uid()))
  with check (reporter_id = (select auth.uid())
    and exists (select 1 from public.conversations c where c.id = conversation_id));
grant insert, update on public.conversation_reports to authenticated;

create policy "Members read groups" on public.groups for select to authenticated using (true);

create policy "Visible group members" on public.group_members for select to authenticated
  using (user_id = (select auth.uid()) or private.group_is_public(group_id) or private.is_group_member(group_id));

create policy "Applicants and owners read requests" on public.group_join_requests for select to authenticated
  using (user_id = (select auth.uid()) or private.is_group_owner(group_id));
create policy "Applicants and owners delete requests" on public.group_join_requests for delete to authenticated
  using (user_id = (select auth.uid()) or private.is_group_owner(group_id));
grant delete on public.group_join_requests to authenticated;

create policy "Members read events" on public.events for select to authenticated using (true);

create policy "Organizers and participants read participants" on public.event_participants for select to authenticated
  using (user_id = (select auth.uid()) or private.can_see_participants(event_id));

create policy "Members read opportunities" on public.opportunities for select to authenticated using (true);

create policy "Parties read talent requests" on public.talent_requests for select to authenticated
  using ((select auth.uid()) in (seeker_id, creator_id));

create policy "Members read talents" on public.user_talents for select to authenticated using (true);
create policy "Owners add talents" on public.user_talents for insert to authenticated
  with check (user_id = (select auth.uid()));
create policy "Owners update talents" on public.user_talents for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
grant insert, update on public.user_talents to authenticated;

-- =========================================================
-- Accounts and profiles
-- =========================================================

-- Creates the caller's profile if the signup trigger missed it; returns whether setup is done.
create function public.ensure_profile() returns boolean
language plpgsql security definer set search_path = '' as $$
declare done boolean;
begin
  insert into public.profiles (id, full_name, display_name, profile_image_url)
  select u.id, private.display_name_from(u.raw_user_meta_data), private.display_name_from(u.raw_user_meta_data),
    left(coalesce(u.raw_user_meta_data ->> 'avatar_url', u.raw_user_meta_data ->> 'picture', ''), 2000)
  from auth.users u where u.id = private.me()
  on conflict (id) do nothing;
  select profile_completed into done from public.profiles where id = auth.uid();
  return coalesce(done, false);
end $$;

create function public.save_profile(
  p_account_type text, p_display_name text, p_username text, p_course text, p_talents text[],
  p_organization text, p_seeking_talents text[], p_bio text, p_photo text,
  p_pronouns text, p_links text, p_gender text, p_completing boolean default false
) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  old public.profiles;
  name text := ltrim(lower(trim(coalesce(p_username, ''))), '@');
begin
  perform public.ensure_profile();
  select * into old from public.profiles where id = me for update;
  if p_account_type is null or p_account_type not in ('student', 'talent_seeker') then
    raise exception 'Choose an account type.';
  end if;
  if old.profile_completed and old.account_type is distinct from p_account_type then
    raise exception 'Your account type can''t be changed here.';
  end if;
  if char_length(trim(coalesce(p_display_name, ''))) not between 1 and 60 then
    raise exception 'Keep your display name to 60 characters.';
  end if;
  if name !~ '^[a-z0-9_]{3,30}$' then
    raise exception 'Use 3–30 letters, numbers or underscores for your username.';
  end if;
  if exists (select 1 from public.profiles where username = name and id <> me) then
    raise exception 'That username is already taken.';
  end if;
  if char_length(coalesce(p_pronouns, '')) > 40 or char_length(coalesce(p_links, '')) > 300
    or char_length(coalesce(p_gender, '')) > 40 then
    raise exception 'One of your fields is too long.';
  end if;
  if coalesce(p_links, '') <> '' and p_links not like 'https://%' then
    raise exception 'Use a full https:// link.';
  end if;

  update public.profiles set
    account_type = p_account_type,
    display_name = trim(p_display_name),
    full_name = case when full_name = '' then trim(p_display_name) else full_name end,
    username = name,
    course = case when p_account_type = 'student' then trim(coalesce(p_course, '')) else '' end,
    talent_categories = case when p_account_type = 'student' then coalesce(p_talents, '{}') else '{}' end,
    organization = case when p_account_type = 'talent_seeker' then trim(coalesce(p_organization, '')) else '' end,
    seeking_talents = case when p_account_type = 'talent_seeker' then coalesce(p_seeking_talents, '{}') else '{}' end,
    bio = trim(coalesce(p_bio, '')),
    profile_image_url = coalesce(p_photo, profile_image_url),
    pronouns = trim(coalesce(p_pronouns, '')),
    links = trim(coalesce(p_links, '')),
    gender = trim(coalesce(p_gender, '')),
    profile_completed = profile_completed or p_completing,
    updated_at = now()
  where id = me;
end $$;

-- A private account moves every public post, story and highlight to followers in one step.
create function public.set_private(p_private boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := private.me();
begin
  update public.profiles set is_private = p_private, updated_at = now() where id = me;
  if p_private then
    update public.posts set audience = 'followers' where user_id = me and audience = 'public';
    update public.stories set audience = 'followers' where user_id = me and audience = 'public';
    update public.highlights set audience = 'followers' where user_id = me and audience = 'public';
  end if;
end $$;

-- Prefix search over completed profiles, by display name or @username.
create function public.search_profiles(
  p_term text default '', p_by_username boolean default false, p_account_type text default null,
  p_category text default null, p_limit integer default 30, p_offset integer default 0
) returns setof public.profiles
language sql stable set search_path = '' as $$
  select p.* from public.profiles p
  where p.profile_completed
    and (p_account_type is null or p.account_type = p_account_type)
    and (p_category is null or p_category = any (p.talent_categories))
    and (coalesce(p_term, '') = '' or starts_with(
      case when p_by_username then coalesce(p.username, '') else p.name_key end, lower(p_term)))
  order by case when p_by_username then coalesce(p.username, '') else p.name_key end, p.id
  limit least(greatest(p_limit, 1), 50) offset greatest(p_offset, 0);
$$;

create function public.profile_stats(p_user_id uuid)
returns table (followers bigint, following bigint, posts bigint, likes bigint)
language sql stable set search_path = '' as $$
  select
    (select count(*) from public.follows where following_id = p_user_id),
    (select count(*) from public.follows where follower_id = p_user_id),
    (select count(*) from public.posts where user_id = p_user_id),
    (select coalesce(sum(likes_count), 0)::bigint from public.posts where user_id = p_user_id);
$$;

-- =========================================================
-- Follows
-- =========================================================

create function public.set_following(p_target uuid, p_following boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  target public.profiles;
begin
  if p_target = me then raise exception 'You cannot follow yourself.'; end if;
  if not p_following then
    delete from public.follows where follower_id = me and following_id = p_target;
    return;
  end if;
  if exists (select 1 from public.follows where follower_id = me and following_id = p_target) then return; end if;
  select * into target from public.profiles where id = p_target;
  if not found then raise exception 'This account is no longer available.'; end if;
  if target.is_private then raise exception 'This account is private. Send a follow request instead.'; end if;
  insert into public.follows (follower_id, following_id) values (me, p_target) on conflict do nothing;
  perform private.notify(p_target, 'follow_' || me::text, 'follow', '', '', 'started following you');
end $$;

create function public.request_follow(p_target uuid, p_request boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := private.me();
begin
  if p_target = me then raise exception 'You cannot follow yourself.'; end if;
  if not p_request then
    delete from public.follow_requests where requester_id = me and target_id = p_target;
    return;
  end if;
  if exists (select 1 from public.follows where follower_id = me and following_id = p_target) then return; end if;
  if not exists (select 1 from public.profiles where id = p_target and is_private) then
    raise exception 'This account is public. Follow it directly.';
  end if;
  insert into public.follow_requests (requester_id, target_id) values (me, p_target) on conflict do nothing;
end $$;

create function public.review_follow_request(p_requester uuid, p_accept boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := private.me();
begin
  delete from public.follow_requests where requester_id = p_requester and target_id = me;
  if found and p_accept then
    insert into public.follows (follower_id, following_id) values (p_requester, me) on conflict do nothing;
  end if;
end $$;

-- =========================================================
-- Posts
-- =========================================================

-- Retrying with the same id returns the existing post instead of a duplicate.
create function public.publish_post(
  p_id text, p_caption text, p_categories text[], p_tags text[], p_media jsonb, p_audience text,
  p_location text, p_tagged_people text[], p_ai_caption boolean, p_ai_tagged boolean, p_ai_category_suggested boolean
) returns public.posts
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  result public.posts;
  item jsonb;
begin
  select * into result from public.posts where id = p_id;
  if found then
    if result.user_id <> me.id then raise exception 'Could not restore your post.'; end if;
    return result;
  end if;
  if p_id !~ '^[A-Za-z0-9_-]{1,100}$' then raise exception 'Check your post and retry.'; end if;
  if jsonb_typeof(p_media) <> 'array' or jsonb_array_length(p_media) > 10 then
    raise exception 'Choose up to 10 photos or videos.';
  end if;
  if trim(coalesce(p_caption, '')) = '' and jsonb_array_length(p_media) = 0 then
    raise exception 'Add a photo, video or caption first.';
  end if;
  if p_audience not in ('public', 'followers', 'private') then raise exception 'Choose who can see this post.'; end if;
  for item in select value from jsonb_array_elements(p_media) loop
    if not private.cloudinary_url(item ->> 'url', 'image|video', 'posts', me.id, p_id)
      or coalesce(item ->> 'contentType', '') !~ '^(image|video)/[a-zA-Z0-9.+-]{1,80}$'
      or (coalesce(item ->> 'thumbnailUrl', '') <> ''
        and not private.cloudinary_url(item ->> 'thumbnailUrl', 'image|video', 'posts', me.id, p_id)) then
      raise exception 'Media upload failed. Please retry.';
    end if;
  end loop;

  insert into public.posts (id, user_id, caption, categories, tags, media, media_type, audience, location,
    tagged_people, author_display_name, author_username, author_talent, author_course, author_profile_image_url,
    ai_caption, ai_tagged, ai_category_suggested)
  values (p_id, me.id, trim(coalesce(p_caption, '')), coalesce(p_categories, '{}'), coalesce(p_tags, '{}'), p_media,
    case when p_media -> 0 ->> 'contentType' like 'video/%' then 'video' else 'image' end,
    case when p_audience = 'public' and me.is_private then 'followers' else p_audience end,
    coalesce(p_location, ''), coalesce(p_tagged_people, '{}'), me.display_name, coalesce(me.username, ''),
    coalesce(me.talent_categories[1], ''), me.course, me.profile_image_url,
    coalesce(p_ai_caption, false), coalesce(p_ai_tagged, false), coalesce(p_ai_category_suggested, false))
  returning * into result;
  return result;
end $$;

-- Likes and unique views update counters and the like notification together.
create function public.set_post_interaction(p_post_id text, p_liked boolean default null, p_viewed boolean default false)
returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  post public.posts;
  old_liked boolean;
  old_viewed boolean;
  new_liked boolean;
  new_viewed boolean;
begin
  select * into post from public.posts where id = p_post_id for update;
  if not found or not private.can_view(post.user_id, post.audience) then
    raise exception 'This post is no longer available.';
  end if;
  select liked, viewed into old_liked, old_viewed from public.post_interactions
    where post_id = p_post_id and user_id = me;
  old_liked := coalesce(old_liked, false);
  old_viewed := coalesce(old_viewed, false);
  new_liked := coalesce(p_liked, old_liked);
  new_viewed := old_viewed or (coalesce(p_viewed, false) and post.user_id <> me);
  if new_liked = old_liked and new_viewed = old_viewed then return; end if;

  insert into public.post_interactions (post_id, user_id, liked, viewed) values (p_post_id, me, new_liked, new_viewed)
  on conflict (post_id, user_id) do update set liked = excluded.liked, viewed = excluded.viewed;
  update public.posts set
    likes_count = greatest(0, likes_count + new_liked::int - old_liked::int),
    views_count = greatest(0, views_count + new_viewed::int - old_viewed::int)
  where id = p_post_id;
  if new_liked and not old_liked then
    perform private.notify(post.user_id, 'like_' || p_post_id || '_' || me::text, 'like', p_post_id, '', 'liked your post');
  end if;
end $$;

create function public.add_comment(p_post_id text, p_comment_id text, p_text text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  body text := trim(coalesce(p_text, ''));
  existing public.comments;
  post public.posts;
begin
  if char_length(body) not between 1 and 2000 then raise exception 'Write a comment of 1–2,000 characters.'; end if;
  if p_comment_id !~ '^[A-Za-z0-9_-]{1,100}$' then raise exception 'Comment retry doesn''t match.'; end if;
  select * into existing from public.comments where id = p_comment_id;
  if found then
    if existing.user_id <> me.id or existing.text <> body then raise exception 'Comment retry doesn''t match.'; end if;
    return;
  end if;
  select * into post from public.posts where id = p_post_id for update;
  if not found or not private.can_view(post.user_id, post.audience) then
    raise exception 'This post is no longer available.';
  end if;
  insert into public.comments (id, post_id, user_id, username, display_name, profile_image_url, text)
  values (p_comment_id, p_post_id, me.id, coalesce(me.username, ''), me.display_name, me.profile_image_url, body);
  update public.posts set comments_count = comments_count + 1 where id = p_post_id;
  perform private.notify(post.user_id, 'comment_' || p_comment_id, 'comment', p_post_id, p_comment_id, 'commented on your post');
end $$;

create function public.delete_comment(p_post_id text, p_comment_id text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  existing public.comments;
begin
  select * into existing from public.comments where id = p_comment_id and post_id = p_post_id for update;
  if not found or existing.deleted then return; end if;
  if existing.user_id <> me then raise exception 'You can only delete your own comment.'; end if;
  update public.comments set deleted = true, text = '' where id = p_comment_id;
  update public.posts set comments_count = greatest(0, comments_count - 1) where id = p_post_id;
end $$;

create function public.set_comment_liked(p_comment_id text, p_liked boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  existing public.comments;
  changed integer;
begin
  select * into existing from public.comments where id = p_comment_id for update;
  if not found or existing.deleted
    or not exists (select 1 from public.posts p where p.id = existing.post_id and private.can_view(p.user_id, p.audience)) then
    raise exception 'This comment is no longer available.';
  end if;
  if p_liked then
    insert into public.comment_likes (comment_id, user_id) values (p_comment_id, me) on conflict do nothing;
  else
    delete from public.comment_likes where comment_id = p_comment_id and user_id = me;
  end if;
  get diagnostics changed = row_count;
  if changed > 0 then
    update public.comments set likes_count = greatest(0, likes_count + case when p_liked then 1 else -1 end)
    where id = p_comment_id;
  end if;
end $$;

-- =========================================================
-- Stories and highlights
-- =========================================================

create function public.create_story(p_id text, p_media_url text, p_video boolean, p_audience text, p_composition jsonb default null)
returns void
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  existing public.stories;
begin
  select * into existing from public.stories where id = p_id;
  if found then
    if existing.user_id <> me.id or existing.deleted or existing.media_url <> p_media_url
      or existing.composition is distinct from p_composition then
      raise exception 'This story was already shared. Close the editor to view it, or choose media for a new story.';
    end if;
    return;
  end if;
  if p_audience not in ('public', 'followers', 'private') then raise exception 'Choose who can see this story.'; end if;
  if p_id !~ '^[A-Za-z0-9_-]{1,100}$' or not private.cloudinary_url(p_media_url, 'image|video', 'stories', me.id, p_id) then
    raise exception 'Upload failed. Retry.';
  end if;
  insert into public.stories (id, user_id, media_url, media_type, audience, username, profile_image_url, composition)
  values (p_id, me.id, p_media_url, case when p_video then 'video' else 'image' end,
    case when p_audience = 'public' and me.is_private then 'followers' else p_audience end,
    coalesce(me.username, ''), me.profile_image_url, p_composition);
end $$;

create function public.set_story_liked(p_story_id text, p_liked boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  story public.stories;
  changed integer;
begin
  select * into story from public.stories where id = p_story_id for update;
  if not found or story.deleted or story.created_at <= now() - interval '24 hours'
    or not private.can_view(story.user_id, story.audience) or private.is_blocked(story.user_id, me) then
    raise exception 'This story is no longer available.';
  end if;
  if story.user_id = me then raise exception 'You can''t react to your own story.'; end if;
  if p_liked then
    insert into public.story_reactions (story_id, user_id) values (p_story_id, me) on conflict do nothing;
  else
    delete from public.story_reactions where story_id = p_story_id and user_id = me;
  end if;
  get diagnostics changed = row_count;
  if changed > 0 then
    update public.stories set likes_count = greatest(0, likes_count + case when p_liked then 1 else -1 end)
    where id = p_story_id;
  end if;
end $$;

create function public.remove_story(p_id text) returns void
language sql security definer set search_path = '' as $$
  update public.stories set deleted = true where id = p_id and user_id = private.me();
$$;

-- Each moment must be the caller's own story or post media, exactly as published.
create function public.save_highlight(p_id text, p_title text, p_media jsonb, p_cover integer, p_audience text)
returns void
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles;
  item jsonb;
  owner uuid;
begin
  select * into me from public.profiles where id = private.me();
  if p_id !~ '^[A-Za-z0-9_-]{1,100}$' then raise exception 'Check your highlight and retry.'; end if;
  if char_length(trim(coalesce(p_title, ''))) not between 1 and 32 then raise exception 'Add a title (up to 32 characters).'; end if;
  if jsonb_typeof(p_media) <> 'array' or jsonb_array_length(p_media) not between 1 and 8
    or p_cover not between 0 and jsonb_array_length(p_media) - 1 then
    raise exception 'Choose 1–8 moments and a cover.';
  end if;
  if p_audience not in ('public', 'followers', 'private') then raise exception 'Choose who can see this highlight.'; end if;
  select user_id into owner from public.highlights where id = p_id;
  if found and owner <> me.id then raise exception 'Check your highlight and retry.'; end if;
  for item in select value from jsonb_array_elements(p_media) loop
    if not (
      (item ->> 'source' = 'story' and exists (select 1 from public.stories s
        where s.id = item ->> 'sourceId' and s.user_id = me.id and s.media_url = item ->> 'url'))
      or (item ->> 'source' = 'post' and exists (select 1 from public.posts p
        where p.id = item ->> 'sourceId' and p.user_id = me.id
          and p.media -> ((item ->> 'index')::int) ->> 'url' = item ->> 'url'))
    ) then
      raise exception 'One of these moments is no longer available.';
    end if;
  end loop;
  insert into public.highlights (id, user_id, title, media, cover, audience, updated_at)
  values (p_id, me.id, trim(p_title), p_media, p_cover,
    case when p_audience = 'public' and me.is_private then 'followers' else p_audience end, now())
  on conflict (id) do update set title = excluded.title, media = excluded.media, cover = excluded.cover,
    audience = excluded.audience, updated_at = excluded.updated_at;
end $$;

-- =========================================================
-- Messages
-- =========================================================

create function public.create_conversation(p_id text, p_people uuid[], p_title text, p_note text, p_group boolean)
returns void
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  ids uuid[];
  snapshot jsonb;
begin
  if exists (select 1 from public.conversations where id = p_id) then return; end if;
  if coalesce(cardinality(p_people), 0) not between 1 and 7 or me.id = any (p_people)
    or (select count(distinct x) from unnest(p_people) x) <> cardinality(p_people) then
    raise exception 'Choose one to seven campus connections.';
  end if;
  if not p_group and cardinality(p_people) <> 1 then raise exception 'Choose one person for a direct chat.'; end if;
  if p_group and char_length(trim(coalesce(p_title, ''))) not between 1 and 60 then
    raise exception 'Give your collaboration a name (up to 60 characters).';
  end if;
  if char_length(coalesce(p_note, '')) > 500 then raise exception 'Keep the invitation under 500 characters.'; end if;
  if not p_group and private.is_blocked(me.id, p_people[1]) then raise exception 'You can''t message this person.'; end if;
  ids := array[me.id] || p_people;
  select jsonb_object_agg(p.id::text, jsonb_build_object('displayName', p.display_name,
    'username', coalesce(p.username, ''), 'profileImageUrl', p.profile_image_url))
  into snapshot from public.profiles p where p.id = any (ids) and p.profile_completed;
  if snapshot is null or (select count(*) from jsonb_object_keys(snapshot)) <> cardinality(ids) then
    raise exception 'This person hasn''t activated Messages yet.';
  end if;
  insert into public.conversations (id, kind, title, creator_id, party_ids, member_ids, invited_ids, people, invite_note)
  values (p_id, case when p_group then 'group' else 'direct' end, trim(coalesce(p_title, '')), me.id, ids,
    array[me.id], p_people, snapshot, trim(coalesce(p_note, '')));
end $$;

create function public.respond_conversation(p_id text, p_accept boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  chat public.conversations;
begin
  select * into chat from public.conversations where id = p_id for update;
  if not found or not (me = any (chat.invited_ids)) then return; end if;
  if p_accept and private.is_blocked(me, chat.creator_id) then raise exception 'You can''t join this conversation.'; end if;
  update public.conversations set
    invited_ids = array_remove(invited_ids, me),
    member_ids = case when p_accept and not (me = any (member_ids)) then array_append(member_ids, me) else member_ids end
  where id = p_id;
end $$;

-- Retrying with the same message id is a no-op, so a lost acknowledgement never duplicates.
create function public.send_message(p_conversation_id text, p_message_id text, p_text text, p_attachment jsonb default null)
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
  if not chat.demo and (cardinality(chat.member_ids) < 2
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
      || p_conversation_id || '/' || me::text || '/' || p_message_id || '/[a-zA-Z0-9_.-]+[.]bin$')
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

-- Clearing the attachment also drops its decryption key, so the file can't be opened again.
create function public.unsend_message(p_conversation_id text, p_message_id text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  message public.messages;
begin
  select * into message from public.messages where conversation_id = p_conversation_id and id = p_message_id for update;
  if not found or not private.is_chat_member(p_conversation_id) then raise exception 'This message is no longer available.'; end if;
  if message.sender_id <> me::text then raise exception 'You can only unsend your own messages.'; end if;
  if message.unsent_at is not null then return; end if;
  update public.messages set text = '', attachment = null, unsent_at = now()
  where conversation_id = p_conversation_id and id = p_message_id;
  update public.conversations set preview = 'Message unsent'
  where id = p_conversation_id and last_message_id = p_message_id;
end $$;

create function public.older_messages(p_conversation_id text, p_before_id text, p_limit integer default 50)
returns setof public.messages
language sql stable set search_path = '' as $$
  select m.* from public.messages m
  where m.conversation_id = p_conversation_id
    and m.seq < (select b.seq from public.messages b where b.conversation_id = p_conversation_id and b.id = p_before_id)
  order by m.seq desc
  limit least(greatest(p_limit, 1), 50);
$$;

create function public.leave_conversation(p_id text) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := private.me();
begin
  if not exists (select 1 from public.conversations where id = p_id and kind = 'group' and me = any (member_ids)) then
    raise exception 'Only group conversations can be left.';
  end if;
  update public.conversations set member_ids = array_remove(member_ids, me) where id = p_id;
end $$;

-- nickname and muted take the given values; readAt and hiddenAt use the server's clock.
create function public.set_chat_preferences(p_conversation_id text, p_values jsonb) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := private.me();
begin
  if not private.is_chat_member(p_conversation_id) then raise exception 'Join this conversation first.'; end if;
  if char_length(coalesce(p_values ->> 'nickname', '')) > 60 then raise exception 'Keep the nickname to 60 characters.'; end if;
  insert into public.chat_settings (user_id, conversation_id) values (me, p_conversation_id) on conflict do nothing;
  update public.chat_settings set
    nickname = case when p_values ? 'nickname' then coalesce(p_values ->> 'nickname', '') else nickname end,
    muted = case when p_values ? 'muted' then coalesce((p_values ->> 'muted')::boolean, false) else muted end,
    read_at = case when p_values ? 'readAt' then now() else read_at end,
    hidden_at = case when p_values ? 'hiddenAt' then now() else hidden_at end
  where user_id = me and conversation_id = p_conversation_id;
end $$;

-- Private sample chats never contact other accounts.
create function public.add_sample_conversations() returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  is_group boolean;
  chat text;
  texts text[];
begin
  foreach is_group in array array[false, true] loop
    chat := 'demo_' || me::text || '_' || case when is_group then 'group' else 'chat' end;
    texts := case when is_group then array[
        'Welcome to the Campus Creators sample collaboration.',
        'Try sharing ideas, searching this chat, or changing its nickname.']
      else array[
        'Welcome to Messages! This is a private sample chat, not a real person.',
        'Send a message here to try the experience. Sample chats never contact other accounts.'] end;
    insert into public.conversations (id, kind, demo, title, creator_id, party_ids, member_ids, invited_ids, people,
      invite_note, preview, last_message_id, last_sender_id)
    values (chat, case when is_group then 'group' else 'direct' end, true,
      case when is_group then 'Campus Creators · Demo' else 'SPOT Guide · Demo' end, me, array[me], array[me], '{}', '{}',
      'Private sample conversation', texts[2], 'sample-1', 'demo')
    on conflict (id) do nothing;
    if found then
      insert into public.messages (conversation_id, id, sender_id, text)
      values (chat, 'sample-0', 'demo', texts[1]), (chat, 'sample-1', 'demo', texts[2]);
    end if;
    update public.chat_settings set hidden_at = null where user_id = me and conversation_id = chat;
  end loop;
end $$;

-- =========================================================
-- Groups
-- =========================================================

create function public.search_groups(p_term text default '', p_category text default '', p_offset integer default 0)
returns setof public.groups
language sql stable set search_path = '' as $$
  select g.* from public.groups g
  where (coalesce(p_category, '') = '' or g.category = p_category)
    and (coalesce(p_term, '') = '' or starts_with(g.name_key, lower(trim(p_term))))
  order by case when coalesce(p_term, '') = '' then -g.seq end, g.name_key, g.id
  limit 30 offset greatest(p_offset, 0);
$$;

create function public.create_group(p_id text, p_name text, p_description text, p_image_url text, p_category text, p_privacy text)
returns text
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  owner uuid;
begin
  select owner_id into owner from public.groups where id = p_id;
  if found then
    if owner <> me.id then raise exception 'Your account changed. Please retry.'; end if;
    return p_id;
  end if;
  if coalesce(p_image_url, '') <> '' and not private.cloudinary_url(p_image_url, 'image', 'groups', me.id, p_id) then
    raise exception 'Image upload failed. Please retry.';
  end if;
  if p_privacy not in ('public', 'private') then raise exception 'Choose Public or Private.'; end if;
  insert into public.groups (id, name, description, image_url, category, privacy, owner_id, owner_name, owner_profile_image_url)
  values (p_id, trim(p_name), trim(p_description), coalesce(p_image_url, ''), p_category, p_privacy,
    me.id, me.display_name, me.profile_image_url);
  insert into public.group_members (group_id, user_id, display_name, profile_image_url, role)
  values (p_id, me.id, me.display_name, me.profile_image_url, 'owner');
  return p_id;
end $$;

create function public.request_join_group(p_id text) returns void
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
end $$;

create function public.set_group_joined(p_id text, p_joined boolean) returns void
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
end $$;

create function public.review_group_request(p_id text, p_applicant uuid, p_approve boolean) returns void
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
end $$;

-- =========================================================
-- Events
-- =========================================================

create function public.search_events(p_term text default '', p_category text default '', p_offset integer default 0)
returns setof public.events
language sql stable set search_path = '' as $$
  select e.* from public.events e
  where (coalesce(p_category, '') = '' or e.category = p_category)
    and (coalesce(p_term, '') = '' or starts_with(e.name_key, lower(trim(p_term))))
  order by case when coalesce(p_term, '') = '' then -e.seq end, e.name_key, e.id
  limit 30 offset greatest(p_offset, 0);
$$;

create function public.create_event(
  p_id text, p_title text, p_description text, p_image_url text, p_category text, p_starts_at timestamptz,
  p_location text, p_organizer text, p_participant_limit integer default null
) returns text
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  organizer uuid;
begin
  if me.account_type is distinct from 'talent_seeker' then raise exception 'Only Spotters can create events.'; end if;
  select organizer_id into organizer from public.events where id = p_id;
  if found then
    if organizer <> me.id then raise exception 'Your account changed. Please retry.'; end if;
    return p_id;
  end if;
  if p_starts_at <= now() then raise exception 'Choose a future date and time.'; end if;
  if coalesce(p_image_url, '') <> '' and not private.cloudinary_url(p_image_url, 'image', 'events', me.id, p_id) then
    raise exception 'Image upload failed. Please retry.';
  end if;
  insert into public.events (id, title, description, image_url, category, starts_at, location, organizer,
    organizer_id, organizer_name, organizer_profile_image_url, participant_limit)
  values (p_id, trim(p_title), trim(p_description), coalesce(p_image_url, ''), p_category, p_starts_at,
    trim(p_location), trim(p_organizer), me.id, me.display_name, me.profile_image_url, p_participant_limit);
  return p_id;
end $$;

create function public.set_event_joined(p_id text, p_joined boolean) returns void
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
end $$;

-- =========================================================
-- Work
-- =========================================================

create function public.publish_opportunity(
  p_id text, p_title text, p_kind text, p_description text, p_category text, p_location text, p_deadline timestamptz
) returns void
language plpgsql security definer set search_path = '' as $$
declare me public.profiles := private.member();
begin
  if me.account_type is distinct from 'talent_seeker' then raise exception 'Only Spotters can post opportunities.'; end if;
  if exists (select 1 from public.opportunities where id = p_id) then return; end if;
  if p_deadline <= now() then raise exception 'Choose a future application deadline.'; end if;
  insert into public.opportunities (id, seeker_id, title, kind, description, category, location, deadline)
  values (p_id, me.id, trim(p_title), p_kind, trim(p_description), trim(p_category), trim(p_location), p_deadline);
end $$;

create function public.close_opportunity(p_id text) returns void
language sql security definer set search_path = '' as $$
  update public.opportunities set status = 'closed' where id = p_id and seeker_id = private.me() and status = 'open';
$$;

-- The source decides the seeker and title, so requests can't be forged.
create function public.create_talent_request(p_kind text, p_source_id text, p_creator_id uuid, p_details text)
returns void
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  seeker uuid;
  heading text;
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
  values (p_kind || '_' || p_source_id || '_' || p_creator_id::text, p_kind, p_source_id, seeker, p_creator_id, heading, trim(p_details))
  on conflict (id) do nothing;
  if not found then raise exception 'A request already exists. Open Notifications to see its status.'; end if;
end $$;

create function public.respond_talent_request(p_id text, p_status text) returns void
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
end $$;

-- =========================================================
-- Collaboration suggestions
-- =========================================================

-- Creators with confirmed talents; talents you don't have yourself rank higher.
create function public.get_collaboration_suggestions()
returns table (user_id uuid, display_name text, avatar_url text, talent text, compatibility_score double precision)
language sql stable set search_path = '' as $$
  select t.user_id, p.display_name, nullif(p.profile_image_url, ''), t.talent,
    round((t.confidence * case when exists (select 1 from public.user_talents mine
      where mine.user_id = auth.uid() and mine.talent = t.talent) then 0.5 else 1 end)::numeric, 3)::double precision
  from public.user_talents t
  join public.profiles p on p.id = t.user_id
  where t.user_id <> auth.uid() and t.confirmed and p.profile_completed
    and not private.is_blocked(t.user_id, auth.uid())
  order by 5 desc, p.display_name
  limit 30;
$$;

-- =========================================================
-- API access and realtime
-- =========================================================

-- Only signed-in members may call SPOT's functions; private helpers are not callable at all.
do $$
declare f regprocedure;
begin
  for f in select p.oid::regprocedure from pg_proc p where p.pronamespace = 'public'::regnamespace
    and p.proname in ('ensure_profile', 'save_profile', 'set_private', 'search_profiles', 'profile_stats',
      'set_following', 'request_follow', 'review_follow_request', 'publish_post', 'set_post_interaction',
      'add_comment', 'delete_comment', 'set_comment_liked', 'create_story', 'set_story_liked', 'remove_story',
      'save_highlight', 'create_conversation', 'respond_conversation', 'send_message', 'unsend_message',
      'older_messages', 'leave_conversation', 'set_chat_preferences', 'add_sample_conversations',
      'search_groups', 'create_group', 'request_join_group', 'set_group_joined', 'review_group_request',
      'search_events', 'create_event', 'set_event_joined', 'publish_opportunity', 'close_opportunity',
      'create_talent_request', 'respond_talent_request', 'get_collaboration_suggestions')
  loop
    execute format('revoke all on function %s from public, anon', f);
    execute format('grant execute on function %s to authenticated', f);
  end loop;
  for f in select p.oid::regprocedure from pg_proc p where p.pronamespace = 'private'::regnamespace loop
    execute format('revoke all on function %s from public, anon, authenticated', f);
  end loop;
end $$;

-- RLS policies and invoker-rights functions call these helpers as the signed-in member.
grant execute on function private.can_view(uuid, text), private.is_chat_member(text), private.is_group_member(text),
  private.is_group_owner(text), private.group_is_public(text), private.can_see_participants(text),
  private.is_blocked(uuid, uuid) to authenticated;

-- Live screens refetch when these tables change; Realtime applies the same RLS.
alter publication supabase_realtime add table
  public.profiles, public.user_settings, public.follows, public.follow_requests, public.blocks,
  public.posts, public.saved_posts, public.post_interactions, public.comments, public.comment_likes,
  public.notifications, public.stories, public.story_reactions, public.highlights,
  public.conversations, public.messages, public.chat_settings, public.hidden_messages,
  public.groups, public.group_members, public.group_join_requests, public.events, public.event_participants,
  public.opportunities, public.talent_requests;
