-- Feedback and bug reports from Settings › Send feedback, kept in the database so they arrive even when the
-- member has no email app set up. Members can only add their own, through submit_feedback; nobody reads them
-- through the API. SPOT's team reads them in the Supabase dashboard's table editor.
create table public.feedback (
  id uuid primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  -- Who sent it, as they appeared when sending, so the table editor needs no join.
  sender text not null default '',
  kind text not null check (kind in ('feedback', 'bug')),
  message text not null check (char_length(message) between 10 and 2000),
  screenshot_url text check (screenshot_url is null or char_length(screenshot_url) <= 2000),
  app_version text not null default '' check (char_length(app_version) <= 40),
  device text not null default '' check (char_length(device) <= 120),
  created_at timestamptz not null default now()
);
create index feedback_user_created_idx on public.feedback (user_id, created_at desc);
create index feedback_created_idx on public.feedback (created_at desc);

alter table public.feedback enable row level security;
-- No policies on purpose: the API can't read or change the table; submit_feedback below is the only way in.
revoke all on public.feedback from anon, authenticated;

-- [p_id] comes from the app, so a retry after a dropped connection doesn't file the same report twice.
create function public.submit_feedback(p_id uuid, p_kind text, p_message text, p_screenshot_url text default null,
  p_app_version text default '', p_device text default '')
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := private.me();
  body text := btrim(coalesce(p_message, ''), E' \t\r\n');
  who text;
begin
  if p_id is null then raise exception 'Feedback id is missing.'; end if;
  -- Already filed by this member: a retry, so there's nothing more to do.
  if exists (select 1 from public.feedback f where f.id = p_id and f.user_id = me) then return; end if;
  if p_kind is null or p_kind not in ('feedback', 'bug') then raise exception 'Choose feedback or bug.'; end if;
  if char_length(body) < 10 then raise exception 'Write at least 10 characters so we can understand it.'; end if;
  if char_length(body) > 2000 then raise exception 'Keep it under 2,000 characters.'; end if;
  if coalesce(p_screenshot_url, '') <> ''
      and not private.cloudinary_url(p_screenshot_url, 'image', 'feedback', me, p_id::text) then
    raise exception 'That screenshot couldn''t be attached.';
  end if;
  -- Plenty for real reports; stops a stuck retry loop from flooding the table.
  if (select count(*) from public.feedback f where f.user_id = me and f.created_at > now() - interval '1 hour') >= 10 then
    raise exception 'You''ve sent a lot of reports in the last hour. Please try again later.';
  end if;
  select left(coalesce(nullif(p.display_name, ''), 'A SPOT member') || coalesce(' (@' || nullif(p.username, '') || ')', ''), 120)
    into who from public.profiles p where p.id = me;
  insert into public.feedback (id, user_id, sender, kind, message, screenshot_url, app_version, device)
  values (p_id, me, coalesce(who, ''), p_kind, body, nullif(p_screenshot_url, ''),
    left(coalesce(p_app_version, ''), 40), left(coalesce(p_device, ''), 120))
  on conflict (id) do nothing;
  -- Someone else's id: refuse instead of silently dropping the report.
  if not found then raise exception 'Feedback retry doesn''t match.'; end if;
end $$;

revoke all on function public.submit_feedback(uuid, text, text, text, text, text) from public, anon;
grant execute on function public.submit_feedback(uuid, text, text, text, text, text) to authenticated;
