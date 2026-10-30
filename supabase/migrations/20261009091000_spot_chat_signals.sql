-- "typing…" and "just read" signals for an open chat. Each chat has a private Realtime channel "chat:<conversation id>"
-- carrying Broadcast messages only between phones; nothing is stored. Only the chat's members may join, listen or send
-- (Realtime Authorization), the same way spot_online_presence guards "spot-online".
-- Needs Supabase's realtime schema, so the plain-PostgreSQL behavior tests leave it out.

create policy "Members hear their chats' signals" on realtime.messages for select to authenticated
  using ((select realtime.topic()) like 'chat:%' and extension = 'broadcast'
    and private.is_chat_member(substring((select realtime.topic()) from 6)));
create policy "Members send signals in their chats" on realtime.messages for insert to authenticated
  with check ((select realtime.topic()) like 'chat:%' and extension = 'broadcast'
    and private.is_chat_member(substring((select realtime.topic()) from 6)));
