-- Phone notifications for new chat messages. A trigger on messages asks the message-push Edge Function
-- (supabase/functions/message-push) to notify the other members; the function gets the phones to notify
-- from message_push_targets and sends through Firebase Cloud Messaging.

create extension if not exists pg_net with schema extensions;

-- Each phone's Firebase address, owned by the account signed in on that phone.
create table public.push_tokens (
  token text primary key check (char_length(token) between 20 and 4096),
  user_id uuid not null references public.profiles (id) on delete cascade,
  updated_at timestamptz not null default now()
);
create index push_tokens_user_idx on public.push_tokens (user_id);
-- No policies: only the functions below and the Edge Function (service role) touch it.
alter table public.push_tokens enable row level security;
revoke all on public.push_tokens from anon, authenticated;

-- One notification per message, even if a request is retried. Rows only matter for a few minutes.
create table private.message_pushes (
  conversation_id text not null,
  message_id text not null,
  created_at timestamptz not null default now(),
  primary key (conversation_id, message_id)
);

-- Signing in on a phone moves its address to that account.
create function public.save_push_token(p_token text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'Sign in to get message notifications.'; end if;
  if coalesce(char_length(p_token), 0) not between 20 and 4096 then
    raise exception 'This phone''s notification address is invalid.';
  end if;
  insert into public.push_tokens (token, user_id) values (p_token, auth.uid())
  on conflict (token) do update set user_id = excluded.user_id, updated_at = now();
end $$;

create function public.delete_push_token(p_token text) returns void
language sql security definer set search_path = '' as $$
  delete from public.push_tokens where token = p_token and user_id = auth.uid();
$$;

revoke all on function public.save_push_token(text), public.delete_push_token(text) from public, anon;
grant execute on function public.save_push_token(text), public.delete_push_token(text) to authenticated;

-- Shared by the trigger and message_push_targets, so only this database's own requests send notifications.
select vault.create_secret(replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', ''),
  'message_push_secret', 'Authenticates the messages trigger to the message-push Edge Function');

-- Who to notify about a message: the other members' phones, except muted chats and people who blocked the sender.
create function public.message_push_targets(p_secret text, p_conversation_id text, p_message_id text)
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
  -- Same wording as the app's inbox: a shared post shows its note or "Shared a post".
  shown := case
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
revoke all on function public.message_push_targets(text, text, text) from public, anon, authenticated;
grant execute on function public.message_push_targets(text, text, text) to service_role;

-- pg_net queues the request and sends it after the message is saved, so a slow or failed push never
-- delays or fails sending the message.
create function private.push_new_message() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform net.http_post(
    url := 'https://ogzaptsbcerltfwwxhhe.supabase.co/functions/v1/message-push',
    body := jsonb_build_object('conversation_id', new.conversation_id, 'message_id', new.id),
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-spot-push-secret',
      (select s.decrypted_secret from vault.decrypted_secrets s where s.name = 'message_push_secret')),
    timeout_milliseconds := 10000);
  return null;
end $$;
revoke all on function private.push_new_message() from public, anon, authenticated;

create trigger messages_push after insert on public.messages
  for each row execute function private.push_new_message();
