-- Profiles can be shared into chats as "spot://shared-profile/<uid>" and an optional note, like events. The message
-- notification says "Shared a profile" (or the note) instead of showing the link.
-- Needs spot_shared_event_push.

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
  elsif m.text ~ '^spot://shared-event/[A-Za-z0-9_-]{1,100}' then
    note := btrim(regexp_replace(m.text, '^spot://shared-event/[A-Za-z0-9_-]{1,100}', ''), E' \t\r\n');
    shown := coalesce(nullif(note, ''), 'Shared an event');
  elsif m.text ~ '^spot://shared-profile/[A-Za-z0-9_-]{1,100}' then
    note := btrim(regexp_replace(m.text, '^spot://shared-profile/[A-Za-z0-9_-]{1,100}', ''), E' \t\r\n');
    shown := coalesce(nullif(note, ''), 'Shared a profile');
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
