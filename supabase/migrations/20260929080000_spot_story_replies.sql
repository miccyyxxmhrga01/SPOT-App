-- Story replies go straight to the story's owner, like Instagram: the direct chat between the two is used (both
-- become members, even if an invitation was pending) or started, and the reply is sent as "spot://story/<id>"
-- followed by the text. Blocks and who can see the story still apply.
create function public.reply_to_story(p_story_id text, p_message_id text, p_text text) returns text
language plpgsql security definer set search_path = '' as $$
declare
  me public.profiles := private.member();
  story public.stories;
  chat_id text;
  body text := trim(coalesce(p_text, ''));
  snapshot jsonb;
begin
  select * into story from public.stories where id = p_story_id;
  if not found or story.deleted or story.created_at < now() - interval '24 hours'
    or not private.can_view(story.user_id, story.audience) then
    raise exception 'This story isn''t available anymore.';
  end if;
  if story.user_id = me.id then raise exception 'You can''t reply to your own story.'; end if;
  if private.is_blocked(me.id, story.user_id) then raise exception 'You can''t message this person.'; end if;
  if char_length(body) not between 1 and 1000 then raise exception 'Write a reply (up to 1,000 characters).'; end if;

  -- The direct chat between the two, preferring one they're both already in.
  select c.id into chat_id from public.conversations c
  where c.kind = 'direct' and not c.demo and me.id = any (c.party_ids) and story.user_id = any (c.party_ids)
  order by (me.id = any (c.member_ids) and story.user_id = any (c.member_ids)) desc, c.updated_at desc
  limit 1;
  if chat_id is null then
    select jsonb_object_agg(p.id::text, jsonb_build_object('displayName', p.display_name,
      'username', coalesce(p.username, ''), 'profileImageUrl', p.profile_image_url))
    into snapshot from public.profiles p where p.id in (me.id, story.user_id) and p.profile_completed;
    if snapshot is null or (select count(*) from jsonb_object_keys(snapshot)) <> 2 then
      raise exception 'This person hasn''t activated Messages yet.';
    end if;
    chat_id := gen_random_uuid()::text;
    insert into public.conversations (id, kind, title, creator_id, party_ids, member_ids, invited_ids, people, invite_note)
    values (chat_id, 'direct', '', me.id, array[me.id, story.user_id], array[me.id, story.user_id], '{}', snapshot, '');
  else
    update public.conversations set
      member_ids = array(select distinct x from unnest(member_ids || array[me.id, story.user_id]) x),
      invited_ids = array_remove(array_remove(invited_ids, me.id), story.user_id)
    where id = chat_id and not (me.id = any (member_ids) and story.user_id = any (member_ids));
  end if;
  -- send_message checks membership and blocks again, and a retried message id is a no-op.
  perform public.send_message(chat_id, p_message_id, 'spot://story/' || story.id || E'\n' || body, null);
  return chat_id;
end $$;
revoke all on function public.reply_to_story(text, text, text) from public, anon;
grant execute on function public.reply_to_story(text, text, text) to authenticated;

-- Notifications word story replies like the app: "Replied to your story: <reply>".
create or replace function public.message_push_targets(p_secret text, p_conversation_id text, p_message_id text)
returns table (token text, recipient_id uuid, title text, body text)
language plpgsql security definer set search_path = '' as $$
declare
  m public.messages;
  c public.conversations;
  sender text;
  shown text;
begin
  if p_secret is distinct from (select s.decrypted_secret from vault.decrypted_secrets s where s.name = 'message_push_secret') then
    raise exception 'Not allowed.';
  end if;
  select * into m from public.messages where conversation_id = p_conversation_id and id = p_message_id;
  -- Only fresh messages that are still sent, and each only once.
  if not found or m.unsent_at is not null or m.created_at < now() - interval '10 minutes' then return; end if;
  delete from private.message_pushes where created_at < now() - interval '1 hour';
  insert into private.message_pushes (conversation_id, message_id) values (p_conversation_id, p_message_id)
  on conflict do nothing;
  if not found then return; end if;

  select * into c from public.conversations where id = p_conversation_id;
  select coalesce(nullif(p.display_name, ''), nullif(p.username, ''), 'Someone') into sender
  from public.profiles p where p.id::text = m.sender_id;
  sender := coalesce(sender, 'Someone');
  -- Same wording as the app's inbox.
  shown := case
    when m.text ~ '^spot://story/[A-Za-z0-9_-]{1,100}' then 'Replied to your story'
      || coalesce(': ' || nullif(trim(regexp_replace(m.text, '^spot://story/[A-Za-z0-9_-]{1,100}', '')), ''), '')
    when m.text ~ 'spot://post/[A-Za-z0-9_-]{1,100}' then
      coalesce(nullif(trim(regexp_replace(m.text, 'spot://post/[A-Za-z0-9_-]{1,100}', '', 'g')), ''), 'Shared a post')
    when m.text <> '' then m.text
    when m.attachment ->> 'mime' like 'video/%' then 'Sent a video'
    else 'Sent a photo'
  end;

  return query
    select t.token, t.user_id,
      case when c.kind = 'group' and c.title <> '' then c.title else sender end,
      left(case when c.kind = 'group' and c.title <> '' then sender || ': ' else '' end || shown, 200)
    from public.push_tokens t
    where t.user_id = any (c.member_ids) and t.user_id::text <> m.sender_id
      and not exists (select 1 from public.chat_settings s
        where s.user_id = t.user_id and s.conversation_id = c.id and s.muted)
      and not exists (select 1 from public.blocks b
        where b.blocker_id = t.user_id and b.blocked_id::text = m.sender_id);
end $$;
