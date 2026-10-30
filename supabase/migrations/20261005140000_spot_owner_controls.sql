-- People manage what they share, as on Instagram and Facebook. A post's owner can edit it, pin it to their profile,
-- hide its like count, turn its comments off, or delete it. Comments can be edited by their writer, and a post's
-- owner can remove any comment on it. A group's owner can edit or delete the group and remove members, and group
-- posts can be edited by their authors.

alter table public.posts
  add column edited_at timestamptz,
  add column pinned_at timestamptz,
  add column likes_hidden boolean not null default false,
  add column comments_off boolean not null default false;
-- Profiles list pinned posts first.
create index posts_pinned_idx on public.posts (user_id, pinned_at desc) where pinned_at is not null;

alter table public.comments add column edited_at timestamptz;
alter table public.group_posts add column edited_at timestamptz;

-- =========================================================
-- Posts
-- =========================================================

-- The same rules as publish_post. Media stays as published. "Edited" shows once the words change, not the audience.
create function public.update_post(p_id text, p_caption text, p_categories text[], p_tags text[], p_location text,
  p_audience text)
returns public.posts
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  post public.posts;
  new_caption text := trim(coalesce(p_caption, ''));
  new_categories text[] := coalesce(p_categories, '{}');
  -- Without # and repeats, in the order they were written.
  new_tags text[] := array(select tag from (
      select ltrim(trim(t), '#') as tag, min(n) as first_seen from unnest(coalesce(p_tags, '{}')) with ordinality u(t, n)
      group by 1) written
    where tag <> '' order by first_seen);
  new_location text := trim(coalesce(p_location, ''));
begin
  select * into post from public.posts where id = p_id for update;
  if not found or post.user_id <> me.id then raise exception 'You can only edit your own posts.'; end if;
  if new_caption = '' and jsonb_array_length(post.media) = 0 then
    raise exception 'A post without photos or videos needs a caption.';
  end if;
  if char_length(new_caption) > 2000 then raise exception 'Keep your caption to 2,000 characters.'; end if;
  if cardinality(new_categories) > 8 or exists (select 1 from unnest(new_categories) c where char_length(c) > 60) then
    raise exception 'Choose up to 8 categories.';
  end if;
  if cardinality(new_tags) > 20 or exists (select 1 from unnest(new_tags) t where char_length(t) > 60) then
    raise exception 'Use up to 20 hashtags, each under 60 characters.';
  end if;
  if char_length(new_location) > 160 then raise exception 'Keep your location to 160 characters.'; end if;
  if p_audience is null or p_audience not in ('public', 'followers', 'private') then
    raise exception 'Choose who can see this post.';
  end if;
  update public.posts set
    caption = new_caption, categories = new_categories, tags = new_tags, location = new_location,
    audience = case when p_audience = 'public' and me.is_private then 'followers' else p_audience end,
    edited_at = case when new_caption is distinct from post.caption or new_categories is distinct from post.categories
      or new_tags is distinct from post.tags or new_location is distinct from post.location then now() else post.edited_at end
  where id = p_id
  returning * into post;
  return post;
end $$;

-- Pinning (up to 3 on a profile), hiding the like count and turning comments off. A null leaves that setting alone.
create function public.set_post_options(p_id text, p_pinned boolean default null, p_likes_hidden boolean default null,
  p_comments_off boolean default null)
returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  post public.posts;
begin
  -- Locks the profile so two pins at once can't pass the limit together.
  perform 1 from public.profiles where id = me for update;
  select * into post from public.posts where id = p_id for update;
  if not found or post.user_id <> me then raise exception 'You can only change your own posts.'; end if;
  if p_pinned and post.pinned_at is null
    and (select count(*) from public.posts where user_id = me and pinned_at is not null) >= 3 then
    raise exception 'You can pin up to 3 posts. Unpin one first.';
  end if;
  update public.posts set
    pinned_at = case when p_pinned is null then pinned_at when p_pinned then coalesce(pinned_at, now()) end,
    likes_hidden = coalesce(p_likes_hidden, likes_hidden),
    comments_off = coalesce(p_comments_off, comments_off)
  where id = p_id;
end $$;

-- Comments, likes, saves and views go with the post; so do the like and comment notifications that point to it.
-- Reports stay, since they don't reference the post.
create function public.delete_post(p_id text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  post public.posts;
begin
  select * into post from public.posts where id = p_id for update;
  if not found then return; end if;
  if post.user_id <> me then raise exception 'You can only delete your own posts.'; end if;
  delete from public.notifications where post_id = p_id;
  delete from public.posts where id = p_id;
end $$;

-- =========================================================
-- Comments
-- =========================================================

-- As in spot_schema, and refused while the post's comments are off.
create or replace function public.add_comment(p_post_id text, p_comment_id text, p_text text) returns void
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
  if post.comments_off then raise exception 'Comments are turned off for this post.'; end if;
  insert into public.comments (id, post_id, user_id, username, display_name, profile_image_url, text)
  values (p_comment_id, p_post_id, me.id, coalesce(me.username, ''), me.display_name, me.profile_image_url, body);
  update public.posts set comments_count = comments_count + 1 where id = p_post_id;
  perform private.notify(post.user_id, 'comment_' || p_comment_id, 'comment', p_post_id, p_comment_id, 'commented on your post');
end $$;

create function public.edit_comment(p_comment_id text, p_text text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  existing public.comments;
  body text := trim(coalesce(p_text, ''));
begin
  select * into existing from public.comments where id = p_comment_id for update;
  if not found or existing.deleted
    or not exists (select 1 from public.posts p where p.id = existing.post_id and private.can_view(p.user_id, p.audience)) then
    raise exception 'This comment is no longer available.';
  end if;
  if existing.user_id <> me then raise exception 'You can only edit your own comments.'; end if;
  if char_length(body) not between 1 and 2000 then raise exception 'Write a comment of 1–2,000 characters.'; end if;
  if body = existing.text then return; end if;
  update public.comments set text = body, edited_at = now() where id = p_comment_id;
end $$;

-- As in spot_schema, and a post's owner can also remove other people's comments on it.
create or replace function public.delete_comment(p_post_id text, p_comment_id text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  existing public.comments;
begin
  select * into existing from public.comments where id = p_comment_id and post_id = p_post_id for update;
  if not found or existing.deleted then return; end if;
  if existing.user_id <> me and not exists (select 1 from public.posts p where p.id = p_post_id and p.user_id = me) then
    raise exception 'You can only delete your own comments, or comments on your posts.';
  end if;
  update public.comments set deleted = true, text = '' where id = p_comment_id;
  update public.posts set comments_count = greatest(0, comments_count - 1) where id = p_post_id;
end $$;

-- =========================================================
-- Groups
-- =========================================================

-- The same checks as create_group. The group chat takes the new name.
create function public.update_group(p_id text, p_name text, p_description text, p_image_url text, p_category text,
  p_privacy text)
returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  g public.groups;
  new_name text := trim(coalesce(p_name, ''));
  new_description text := trim(coalesce(p_description, ''));
begin
  select * into g from public.groups where id = p_id for update;
  if not found or g.owner_id <> me then raise exception 'Only the group''s owner can edit it.'; end if;
  if char_length(new_name) not between 2 and 80 then raise exception 'Give your group a name of 2–80 characters.'; end if;
  if char_length(new_description) not between 1 and 2000 then
    raise exception 'Describe your group in up to 2,000 characters.';
  end if;
  if char_length(coalesce(p_category, '')) not between 1 and 60 then raise exception 'Choose a category.'; end if;
  if p_privacy is null or p_privacy not in ('public', 'private') then raise exception 'Choose Public or Private.'; end if;
  if coalesce(p_image_url, '') <> '' and p_image_url is distinct from g.image_url
    and not private.cloudinary_url(p_image_url, 'image', 'groups', me, p_id) then
    raise exception 'Image upload failed. Please retry.';
  end if;
  update public.groups set name = new_name, description = new_description, image_url = coalesce(p_image_url, ''),
    category = p_category, privacy = p_privacy
  where id = p_id;
  update public.conversations set title = left(new_name, 100) where community_kind = 'group' and community_id = p_id;
end $$;

-- Takes its members, requests, posts and chat with it, and the notifications that point to it.
create function public.delete_group(p_id text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  g public.groups;
begin
  select * into g from public.groups where id = p_id for update;
  if not found then return; end if;
  if g.owner_id <> me then raise exception 'Only the group''s owner can delete it.'; end if;
  delete from public.notifications
  where target_id = p_id and type in ('group_join', 'group_request', 'group_approved', 'group_post');
  delete from public.conversations where community_kind = 'group' and community_id = p_id;
  delete from public.groups where id = p_id;
end $$;

-- The owner removes a member; they leave the group chat too, and can ask to join again later.
create function public.remove_group_member(p_id text, p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  g public.groups;
begin
  select * into g from public.groups where id = p_id for update;
  if not found or g.owner_id <> me then raise exception 'Only the group''s owner can remove members.'; end if;
  if p_user = me then raise exception 'The owner stays in the group as its admin.'; end if;
  delete from public.group_members where group_id = p_id and user_id = p_user;
  if not found then return; end if;
  update public.groups set member_count = greatest(0, member_count - 1) where id = p_id;
  perform private.community_chat_member('group', p_id, p_user, false);
end $$;

-- Authors edit their own group posts; the photo stays as posted.
create function public.update_group_post(p_id text, p_text text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  post public.group_posts;
  body text := trim(coalesce(p_text, ''));
begin
  select * into post from public.group_posts where id = p_id for update;
  if not found or not private.is_group_member(post.group_id) then raise exception 'This post is no longer available.'; end if;
  if post.user_id <> me then raise exception 'You can only edit your own posts.'; end if;
  if char_length(body) > 3000 then raise exception 'Keep your post under 3,000 characters.'; end if;
  if body = '' and post.image_url = '' then raise exception 'Write something or add a photo.'; end if;
  if body = post.text then return; end if;
  update public.group_posts set text = body, edited_at = now() where id = p_id;
end $$;

revoke all on function public.update_post(text, text, text[], text[], text, text),
  public.set_post_options(text, boolean, boolean, boolean), public.delete_post(text),
  public.edit_comment(text, text), public.update_group(text, text, text, text, text, text),
  public.delete_group(text), public.remove_group_member(text, uuid), public.update_group_post(text, text)
  from public, anon;
grant execute on function public.update_post(text, text, text[], text[], text, text),
  public.set_post_options(text, boolean, boolean, boolean), public.delete_post(text),
  public.edit_comment(text, text), public.update_group(text, text, text, text, text, text),
  public.delete_group(text), public.remove_group_member(text, uuid), public.update_group_post(text, text)
  to authenticated;
