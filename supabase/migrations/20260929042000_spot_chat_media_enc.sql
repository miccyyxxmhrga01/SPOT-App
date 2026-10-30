-- Cloudinary refuses raw uploads with a ".bin" extension ("resources with extension bin are not allowed"),
-- so no chat photo or video could be sent. Encrypted chat media is now uploaded as ".enc".
-- Same function as before; only the attachment URL's extension changes. create or replace keeps its grants.
create or replace function public.send_message(p_conversation_id text, p_message_id text, p_text text, p_attachment jsonb default null)
returns void
language plpgsql
security definer
set search_path to ''
as $function$
declare
  me uuid := private.me();
  chat public.conversations;
  body text := trim(coalesce(p_text, ''));
  a jsonb := case when jsonb_typeof(p_attachment) = 'object' then p_attachment end;
begin
  if exists (select 1 from public.messages where conversation_id = p_conversation_id and id = p_message_id) then return; end if;
  select * into chat from public.conversations where id = p_conversation_id for update;
  if not found or not (me = any (chat.member_ids)) then
    raise exception 'Join this conversation before sending a message.';
  end if;
  if not chat.demo and (cardinality(chat.member_ids) < 2
    or (chat.kind = 'direct' and private.is_blocked(chat.party_ids[1], chat.party_ids[2]))) then
    raise exception 'Messages couldn''t access this conversation. Check that your invitation was accepted and try again.';
  end if;
  if p_message_id !~ '^[A-Za-z0-9_-]{1,100}$' then raise exception 'Please check your message and retry.'; end if;
  if char_length(body) > 4000 then raise exception 'Keep your message under 4,000 characters.'; end if;
  if body = '' and a is null then raise exception 'Write a message or choose a photo or video.'; end if;
  if a is not null and not (
    a ->> 'path' = 'chat_media/' || p_conversation_id || '/' || me::text || '/' || p_message_id
    and coalesce(a ->> 'mime', '') ~ '^(image|video)/[a-zA-Z0-9.+-]{1,80}$'
    and char_length(coalesce(a ->> 'name', '')) <= 120
    and coalesce((a ->> 'size')::bigint, 0) between 1 and 25 * 1024 * 1024
    and coalesce(a ->> 'url', '') ~ ('^https://res[.]cloudinary[.]com/jcuzzxsz/raw/upload/v[0-9]+/spot/chat_media/'
      || p_conversation_id || '/' || me::text || '/' || p_message_id || '/[a-zA-Z0-9_.-]+[.]enc$')
    and coalesce(a ->> 'key', '') ~ '^[a-zA-Z0-9+/]{43}=$'
    and coalesce(a ->> 'iv', '') ~ '^[a-zA-Z0-9+/]{16}$'
  ) then
    raise exception 'The media service returned an invalid attachment. Retry.';
  end if;
  insert into public.messages (conversation_id, id, sender_id, text, attachment)
  values (p_conversation_id, p_message_id, me::text, body, a);
  update public.conversations set
    preview = coalesce(nullif(left(body, 160), ''), case when a ->> 'mime' like 'video/%' then 'Video' else 'Photo' end),
    last_sender_id = me::text, last_message_id = p_message_id, updated_at = now()
  where id = p_conversation_id;
end $function$;
