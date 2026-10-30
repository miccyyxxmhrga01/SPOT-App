-- Phone notifications for what happens around your events, groups and requests: someone joined your event,
-- asked to join your group, applied to your opportunity or answered your invitation. A trigger on notifications
-- asks the message-push Edge Function to notify the recipient's phones, the same way new messages do.
-- Needs spot_message_push (pg_net, the push secret and push_tokens) and spot_community.

-- Who to notify about an activity notification: the recipient's phones, while it is fresh and unread.
create function public.activity_push_targets(p_secret text, p_recipient_id uuid, p_notification_id text)
returns table (token text, recipient_id uuid, title text, body text)
language plpgsql security definer set search_path = '' as $$
declare n public.notifications;
begin
  if p_secret is distinct from (select s.decrypted_secret from vault.decrypted_secrets s where s.name = 'message_push_secret') then
    raise exception 'Not allowed.';
  end if;
  select * into n from public.notifications x where x.recipient_id = p_recipient_id and x.id = p_notification_id;
  if not found or n.is_read or n.created_at < now() - interval '10 minutes' then return; end if;
  return query
    select t.token, t.user_id, 'SPOT'::text, left(coalesce(nullif(n.sender_name, ''), 'Someone') || ' ' || n.message, 200)
    from public.push_tokens t where t.user_id = p_recipient_id;
end $$;
revoke all on function public.activity_push_targets(text, uuid, text) from public, anon, authenticated;
grant execute on function public.activity_push_targets(text, uuid, text) to service_role;

-- pg_net sends the request after the notification is saved, so a slow or failed push never blocks the action.
create function private.push_new_notification() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform net.http_post(
    url := 'https://ogzaptsbcerltfwwxhhe.supabase.co/functions/v1/message-push',
    body := jsonb_build_object('recipient_id', new.recipient_id, 'notification_id', new.id),
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-spot-push-secret',
      (select s.decrypted_secret from vault.decrypted_secrets s where s.name = 'message_push_secret')),
    timeout_milliseconds := 10000);
  return null;
end $$;
revoke all on function private.push_new_notification() from public, anon, authenticated;

-- Likes, comments and follows stay in the app. A new invitation already reaches the phone as a chat message.
create trigger notifications_push after insert on public.notifications
  for each row when (new.type not in ('like', 'comment', 'follow', 'invitation'))
  execute function private.push_new_notification();

-- Message notifications word an event invitation like the app's inbox does.
create or replace function public.message_push_targets(p_secret text, p_conversation_id text, p_message_id text)
returns table (token text, recipient_id uuid, title text, body text)
language plpgsql security definer set search_path = '' as $$
declare
  m public.messages;
  c public.conversations;
  sender text;
  shown text;
  note text;
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
  if m.text ~ '^spot://story/[A-Za-z0-9_-]{1,100}' then
    note := btrim(regexp_replace(m.text, '^spot://story/[A-Za-z0-9_-]{1,100}', ''), E' \t\r\n');
    shown := 'Replied to your story' || coalesce(': ' || nullif(note, ''), '');
  elsif m.text ~ '^spot://event/[A-Za-z0-9_-]{1,100}' then
    note := btrim(regexp_replace(m.text, '^spot://event/[A-Za-z0-9_-]{1,100}', ''), E' \t\r\n');
    shown := 'Invited you to an event' || coalesce(': ' || nullif(note, ''), '');
  elsif m.text ~ 'spot://post/[A-Za-z0-9_-]{1,100}' then
    note := btrim(regexp_replace(m.text, 'spot://post/[A-Za-z0-9_-]{1,100}', '', 'g'), E' \t\r\n');
    shown := coalesce(nullif(note, ''), 'Shared a post');
  elsif m.text <> '' then
    shown := m.text;
  elsif m.attachment ->> 'mime' like 'video/%' then
    shown := 'Sent a video';
  else
    shown := 'Sent a photo';
  end if;

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
