-- Events a member performed at or is going to, for the "Performed at" section of their profile.
-- Who takes part in an event is otherwise readable only by its organizer and participants, so this shares just the
-- events, and only when the viewer may see the member's profile content: their own, a public account, or a private
-- one they follow. Blocks either way hide everything, as do events whose organizer and viewer have blocked each other.
create or replace function public.profile_events(p_user uuid)
returns setof public.events
language sql
stable
security definer
set search_path = ''
as $$
  select e.*
  from public.event_participants p
  join public.events e on e.id = p.event_id
  join public.profiles owner on owner.id = p.user_id
  where p.user_id = p_user
    and (select auth.uid()) is not null
    and not private.is_blocked(p_user, (select auth.uid()))
    and not private.is_blocked(e.organizer_id, (select auth.uid()))
    and (p_user = (select auth.uid()) or not owner.is_private
      or exists (select 1 from public.follows f where f.following_id = p_user and f.follower_id = (select auth.uid())))
  order by e.starts_at desc, e.id desc
  limit 30;
$$;

revoke all on function public.profile_events(uuid) from public, anon;
grant execute on function public.profile_events(uuid) to authenticated;
