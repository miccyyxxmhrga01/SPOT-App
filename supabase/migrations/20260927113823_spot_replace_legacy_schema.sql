-- Removes the earlier, empty SPOT schema so the migrated app's schema can replace it.
-- Aborts if any of these tables (or auth.users) holds data.
do $$
declare
  t text;
  legacy text[] := array[
    'profiles', 'follows', 'follow_requests', 'blocked_users', 'posts', 'post_interactions', 'comments',
    'comment_likes', 'saved_posts', 'stories', 'story_reactions', 'highlights', 'conversations',
    'conversation_members', 'messages', 'chat_settings', 'conversation_reports', 'notifications', 'groups',
    'group_members', 'group_requests', 'events', 'event_participants', 'opportunities', 'talent_requests'];
  found_rows boolean;
begin
  if exists (select 1 from auth.users) then
    raise exception 'auth.users is not empty; refusing to replace the SPOT schema.';
  end if;
  foreach t in array legacy loop
    execute format('select exists (select 1 from public.%I)', t) into found_rows;
    if found_rows then raise exception 'public.% has rows; refusing to replace the SPOT schema.', t; end if;
  end loop;
  foreach t in array legacy loop
    execute format('drop table public.%I cascade', t);
  end loop;
end $$;

drop function public.spot_create_conversation(text, uuid[], text, text, boolean);
drop function public.spot_update_post_interaction(text, boolean, boolean);
