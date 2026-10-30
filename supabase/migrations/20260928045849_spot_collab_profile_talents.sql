-- Talents picked in onboarding or Edit profile count for collaborations too. They live in
-- user_talents with source 'profile'. Only talents added to or removed from the profile sync,
-- so one taken out of the Collabs tab stays out on later profile saves, and talents confirmed
-- with SPOT AI (source 'ai') stay when the same talent leaves the profile.
create function private.sync_profile_talents() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'UPDATE' then
    delete from public.user_talents
    where user_id = new.id and source = 'profile'
      and talent = any (old.talent_categories) and talent <> all (new.talent_categories);
  end if;
  insert into public.user_talents (user_id, talent, confidence, source, confirmed)
  select distinct new.id, t, 1, 'profile', true from unnest(new.talent_categories) t
  where char_length(t) between 1 and 60
    and (tg_op = 'INSERT' or t <> all (old.talent_categories))
  on conflict (user_id, talent) do nothing;
  return null;
end $$;
revoke all on function private.sync_profile_talents() from public, anon, authenticated;

create trigger profiles_sync_talents
  after insert or update of talent_categories on public.profiles
  for each row execute function private.sync_profile_talents();

insert into public.user_talents (user_id, talent, confidence, source, confirmed)
select distinct p.id, t, 1, 'profile', true from public.profiles p, unnest(p.talent_categories) t
where char_length(t) between 1 and 60
on conflict (user_id, talent) do nothing;

-- A profile can list up to 20 talents, so the limit counts creators rather than talent rows.
create or replace function public.get_collaboration_suggestions()
returns table (user_id uuid, display_name text, avatar_url text, talent text, compatibility_score double precision)
language sql stable set search_path = '' as $$
  with scored as (
    select t.user_id as creator, p.display_name as name, nullif(p.profile_image_url, '') as photo, t.talent as skill,
      round((t.confidence * case when exists (select 1 from public.user_talents mine
        where mine.user_id = auth.uid() and mine.talent = t.talent) then 0.5 else 1 end)::numeric, 3)::double precision as score
    from public.user_talents t
    join public.profiles p on p.id = t.user_id
    where t.user_id <> auth.uid() and t.confirmed and p.profile_completed
      and not private.is_blocked(t.user_id, auth.uid())
  ), ranked as (
    select s.*, dense_rank() over (order by s.best desc, s.name, s.creator) as creator_rank
    from (select scored.*, max(scored.score) over (partition by scored.creator) as best from scored) s
  )
  select r.creator, r.name, r.photo, r.skill, r.score from ranked r
  where r.creator_rank <= 30
  order by r.creator_rank, r.score desc, r.skill;
$$;
