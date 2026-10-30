-- Direct chats show "Seen" under your last message once the other person has read it. Read receipts are a two-way
-- switch in Settings, as on Instagram: members who turn them off don't share when they've read a chat and don't see
-- when others have. Unread counts keep working either way, since read_at is still recorded for its owner.
-- "typing…" and the "just read" nudge that refreshes Seen travel on each chat's private Realtime channel
-- (spot_chat_signals).

alter table public.user_settings add column read_receipts boolean not null default true;

-- When the other person in a direct chat last read it. Null in group chats, when either of you has read receipts
-- off, and between blocked people.
create function public.chat_seen(p_conversation_id text) returns timestamptz
language sql stable security definer set search_path = '' as $$
  select s.read_at
  from public.conversations c
  join public.chat_settings s on s.conversation_id = c.id and s.user_id <> auth.uid() and s.user_id = any (c.member_ids)
  where c.id = p_conversation_id and c.kind = 'direct' and auth.uid() = any (c.member_ids)
    and coalesce((select u.read_receipts from public.user_settings u where u.user_id = auth.uid()), true)
    and coalesce((select u.read_receipts from public.user_settings u where u.user_id = s.user_id), true)
    and not private.is_blocked(auth.uid(), s.user_id);
$$;

revoke all on function public.chat_seen(text) from public, anon;
grant execute on function public.chat_seen(text) to authenticated;
