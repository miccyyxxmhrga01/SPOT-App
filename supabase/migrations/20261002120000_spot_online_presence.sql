-- Who has SPOT open right now. While the app is on screen, each phone joins the private Realtime channel
-- "spot-online" and shares its member id there through Realtime Presence; leaving the app or losing the connection
-- removes it. Only signed-in members may join (Realtime Authorization), and each member chooses whether to share:
-- someone who turns it off is not shown and does not see others either.
-- Needs Supabase's realtime schema, so the plain-PostgreSQL behavior tests leave it out.

alter table public.user_settings add column show_activity boolean not null default true;

create policy "Members see who is online" on realtime.messages for select to authenticated
  using ((select realtime.topic()) = 'spot-online' and extension = 'presence');
create policy "Members share that they are online" on realtime.messages for insert to authenticated
  with check ((select realtime.topic()) = 'spot-online' and extension = 'presence');
