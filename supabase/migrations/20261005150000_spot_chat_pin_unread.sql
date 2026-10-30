-- Long-pressing a chat in Messages offers what Messenger does: pin it to the top of the list (up to 3), or mark it
-- unread to come back to later. Both are private to the person who sets them.
alter table public.chat_settings
  add column pinned_at timestamptz,
  add column marked_unread boolean not null default false;

-- As in spot_schema, plus "pinned" (true pins, false unpins) and "unread" (true marks the chat unread; reading it,
-- "readAt", clears that). Removing a chat from the inbox, "hiddenAt", also unpins it.
create or replace function public.set_chat_preferences(p_conversation_id text, p_values jsonb) returns void
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  pin boolean := case when p_values ? 'pinned' then coalesce((p_values ->> 'pinned')::boolean, false) end;
begin
  if not private.is_chat_member(p_conversation_id) then raise exception 'Join this conversation first.'; end if;
  if char_length(coalesce(p_values ->> 'nickname', '')) > 60 then raise exception 'Keep the nickname to 60 characters.'; end if;
  insert into public.chat_settings (user_id, conversation_id) values (me, p_conversation_id) on conflict do nothing;
  if pin and not exists (select 1 from public.chat_settings
      where user_id = me and conversation_id = p_conversation_id and pinned_at is not null)
    and (select count(*) from public.chat_settings where user_id = me and pinned_at is not null) >= 3 then
    raise exception 'You can pin up to 3 chats. Unpin one first.';
  end if;
  update public.chat_settings set
    nickname = case when p_values ? 'nickname' then coalesce(p_values ->> 'nickname', '') else nickname end,
    muted = case when p_values ? 'muted' then coalesce((p_values ->> 'muted')::boolean, false) else muted end,
    read_at = case when p_values ? 'readAt' then now() else read_at end,
    hidden_at = case when p_values ? 'hiddenAt' then now() else hidden_at end,
    pinned_at = case when p_values ? 'hiddenAt' then null when pin is null then pinned_at when pin then coalesce(pinned_at, now()) end,
    marked_unread = case when p_values ? 'unread' then coalesce((p_values ->> 'unread')::boolean, false)
      when p_values ? 'readAt' then false else marked_unread end
  where user_id = me and conversation_id = p_conversation_id;
end $$;
