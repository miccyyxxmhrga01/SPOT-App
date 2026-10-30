-- Creators can take a talent back out of collaboration matching from the Collabs tab.
create policy "Owners remove talents" on public.user_talents for delete to authenticated
  using (user_id = (select auth.uid()));
grant delete on public.user_talents to authenticated;
