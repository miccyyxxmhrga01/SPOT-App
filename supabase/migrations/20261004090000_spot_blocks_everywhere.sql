-- Blocking used to cover only Messages, collaboration and events. Now two people who have blocked each other (either
-- way) also stop seeing each other's posts, stories, highlights and comments, can't like, comment on or follow each
-- other, and aren't notified about each other. Blocking ends follows and follow requests both ways; unblocking
-- doesn't bring them back. Profiles stay readable, so the Blocked accounts list still shows names and photos.

-- Public, yours, or followers-only while you follow, and never between people who have blocked each other.
-- Posts, stories, highlights and comments read through this, as do add_comment, set_post_interaction,
-- set_comment_liked, set_story_liked, reply_to_story and saving a post.
create or replace function private.can_view(owner uuid, audience text) returns boolean
language sql stable security definer set search_path = '' as $$
  select auth.uid() is not null and (
    owner = auth.uid()
    or ((audience = 'public'
        or (audience = 'followers' and exists (
          select 1 from public.follows f where f.following_id = owner and f.follower_id = auth.uid())))
      and not exists (select 1 from public.blocks b
        where (b.blocker_id = owner and b.blocked_id = auth.uid())
           or (b.blocker_id = auth.uid() and b.blocked_id = owner))));
$$;

-- Comments by someone you've blocked, or who blocked you, are hidden too, including on other people's posts.
drop policy "Post audience reads comments" on public.comments;
create policy "Post audience reads comments" on public.comments for select to authenticated
  using (exists (select 1 from public.posts p where p.id = comments.post_id)
    and not private.is_blocked(user_id, (select auth.uid())));

-- Like private.notify_about: blocked people aren't notified.
create or replace function private.notify(recipient uuid, notification_id text, kind text, post_ref text, comment_ref text, body text)
returns void language sql security definer set search_path = '' as $$
  insert into public.notifications (recipient_id, id, type, sender_id, sender_name, sender_profile_image_url, post_id, comment_id, message)
  select recipient, notification_id, kind, p.id, p.display_name, p.profile_image_url, post_ref, comment_ref, body
  from public.profiles p
  where p.id = auth.uid() and recipient <> auth.uid() and not private.is_blocked(recipient, p.id)
  on conflict (recipient_id, id) do nothing;
$$;

create or replace function public.set_following(p_target uuid, p_following boolean)
returns void language plpgsql security definer set search_path = '' as $$
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
  if private.is_blocked(me, p_target) then raise exception 'You can''t follow this account.'; end if;
  if target.is_private then raise exception 'This account is private. Send a follow request instead.'; end if;
  insert into public.follows (follower_id, following_id) values (me, p_target) on conflict do nothing;
  perform private.notify(p_target, 'follow_' || me::text, 'follow', '', '', 'started following you');
end $$;

create or replace function public.request_follow(p_target uuid, p_request boolean)
returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := private.me();
begin
  if p_target = me then raise exception 'You cannot follow yourself.'; end if;
  if not p_request then
    delete from public.follow_requests where requester_id = me and target_id = p_target;
    return;
  end if;
  if exists (select 1 from public.follows where follower_id = me and following_id = p_target) then return; end if;
  if private.is_blocked(me, p_target) then raise exception 'You can''t follow this account.'; end if;
  if not exists (select 1 from public.profiles where id = p_target and is_private) then
    raise exception 'This account is public. Follow it directly.';
  end if;
  insert into public.follow_requests (requester_id, target_id) values (me, p_target) on conflict do nothing;
end $$;

-- The app adds blocks straight to the table, so a trigger ends the follows and pending requests between the two.
create function private.end_follows_on_block() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  delete from public.follows
  where (follower_id = new.blocker_id and following_id = new.blocked_id)
     or (follower_id = new.blocked_id and following_id = new.blocker_id);
  delete from public.follow_requests
  where (requester_id = new.blocker_id and target_id = new.blocked_id)
     or (requester_id = new.blocked_id and target_id = new.blocker_id);
  return null;
end $$;
revoke all on function private.end_follows_on_block() from public, anon, authenticated;

create trigger blocks_end_follows after insert on public.blocks
  for each row execute function private.end_follows_on_block();
