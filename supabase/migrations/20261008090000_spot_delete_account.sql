-- Members delete their own account from Settings › Sign-in & password › Delete account (required by Google Play
-- for apps with sign-up). Deleting the auth user cascades to the profile and everything it owns: posts, comments,
-- stories, highlights, groups they own, events they organize, follows, requests, applications, reports, feedback and
-- push tokens. Before that, this function undoes what the member added to other people's things, which a cascade
-- can't: like, comment and member counts, their place in other people's chats, and the messages they sent there.
-- Photos and videos stay on Cloudinary: uploads are unsigned, so SPOT has no key that can delete them. Chat media is
-- encrypted with a key kept on the message row, so it can't be read once the message is gone.

create function public.delete_account() returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  chat_id text;
  latest public.messages;
begin
  -- Their likes, comments and joins stop counting on other people's things.
  update public.posts p set likes_count = greatest(0, p.likes_count - 1)
  from public.post_interactions i where i.post_id = p.id and i.user_id = me and i.liked and p.user_id <> me;
  update public.posts p set comments_count = greatest(0, p.comments_count - c.n)
  from (select post_id, count(*) n from public.comments where user_id = me and not deleted group by post_id) c
  where p.id = c.post_id and p.user_id <> me;
  update public.comments c set likes_count = greatest(0, c.likes_count - 1)
  from public.comment_likes l where l.comment_id = c.id and l.user_id = me and c.user_id <> me;
  update public.stories s set likes_count = greatest(0, s.likes_count - 1)
  from public.story_reactions r where r.story_id = s.id and r.user_id = me and s.user_id <> me;
  update public.group_posts g set likes_count = greatest(0, g.likes_count - 1)
  from public.group_post_likes l where l.post_id = g.id and l.user_id = me and g.user_id <> me;
  update public.groups g set member_count = greatest(0, g.member_count - 1)
  from public.group_members m where m.group_id = g.id and m.user_id = me and g.owner_id <> me;
  update public.events e set participant_count = greatest(0, e.participant_count - 1)
  from public.event_participants p where p.event_id = e.id and p.user_id = me and e.organizer_id <> me;

  -- A direct chat ends for both people: there is no one left to reply to.
  delete from public.conversations where kind = 'direct' and me = any (party_ids);
  -- Group chats they started stay with the people in them. With no one else in it, the chat goes with the account.
  update public.conversations c set creator_id = (array_remove(c.member_ids, me))[1]
  where c.creator_id = me and c.community_kind is null and cardinality(array_remove(c.member_ids, me)) > 0;

  -- They and their messages leave every chat that stays.
  delete from public.messages where sender_id = me::text;
  update public.conversations c set
    member_ids = array_remove(c.member_ids, me), party_ids = array_remove(c.party_ids, me),
    invited_ids = array_remove(c.invited_ids, me), people = c.people - me::text
  where c.creator_id <> me and (me = any (c.member_ids) or me = any (c.party_ids) or me = any (c.invited_ids));
  -- Where theirs was the last message, the inbox shows the one before it, worded as send_message words it.
  for chat_id in select id from public.conversations where last_sender_id = me::text and creator_id <> me loop
    latest := null;
    select * into latest from public.messages where conversation_id = chat_id order by seq desc limit 1;
    update public.conversations set
      preview = case
        when latest.id is null then ''
        when latest.unsent_at is not null then 'Message unsent'
        else coalesce(nullif(left(latest.text, 160), ''),
          case when latest.attachment ->> 'mime' like 'video/%' then 'Video' else 'Photo' end)
      end,
      last_message_id = coalesce(latest.id, ''), last_sender_id = coalesce(latest.sender_id, '')
    where id = chat_id;
  end loop;

  delete from auth.users where id = me;
end $$;

revoke all on function public.delete_account() from public, anon;
grant execute on function public.delete_account() to authenticated;
