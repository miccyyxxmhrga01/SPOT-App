-- Joining a private channel also checks that the member may read its broadcasts, so a presence-only read policy
-- refused every phone ("You do not have permissions to read from this Channel topic: spot-online"). Members may read
-- the whole topic; nothing can be broadcast there, since sending stays limited to presence.
drop policy "Members see who is online" on realtime.messages;
create policy "Members see who is online" on realtime.messages for select to authenticated
  using ((select realtime.topic()) = 'spot-online');
