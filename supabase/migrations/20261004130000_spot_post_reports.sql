-- Reports about posts, from a post's ⋯ › Report in the Home feed. Like feedback, members can only file their own,
-- through report_post; nobody reads them through the API. SPOT's team reviews them in the Supabase dashboard's
-- table editor. One report per member per post: reporting again replaces the reason.
create table public.post_reports (
  -- Not a foreign key, so a report stays after the post is deleted.
  post_id text not null check (post_id ~ '^[A-Za-z0-9_-]{1,100}$'),
  reporter_id uuid not null references auth.users (id) on delete cascade,
  -- Who reported whom and what the post said at the time, so the table editor needs no join.
  reporter text not null default '',
  post_owner_id uuid not null,
  post_owner text not null default '',
  post_caption text not null default '',
  reason text not null check (reason in ('spam', 'nudity', 'harassment', 'hate', 'violence', 'false_info', 'stolen', 'other')),
  details text not null default '' check (char_length(details) <= 1000),
  created_at timestamptz not null default now(),
  primary key (post_id, reporter_id)
);
create index post_reports_created_idx on public.post_reports (created_at desc);
create index post_reports_owner_idx on public.post_reports (post_owner_id, created_at desc);
create index post_reports_reporter_idx on public.post_reports (reporter_id, created_at desc);

alter table public.post_reports enable row level security;
-- No policies on purpose: the API can't read or change the table; report_post below is the only way in.
revoke all on public.post_reports from anon, authenticated;

-- Only posts the member can see can be reported, and never their own. "Something else" needs details.
create function public.report_post(p_post_id text, p_reason text, p_details text default '')
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := private.me();
  post public.posts;
  body text := btrim(coalesce(p_details, ''), E' \t\r\n');
  who text;
  owner text;
begin
  select * into post from public.posts where id = p_post_id;
  if not found or not private.can_view(post.user_id, post.audience) then
    raise exception 'This post is no longer available.';
  end if;
  if post.user_id = me then raise exception 'You can''t report your own post.'; end if;
  if p_reason is null or p_reason not in ('spam', 'nudity', 'harassment', 'hate', 'violence', 'false_info', 'stolen', 'other') then
    raise exception 'Choose why you''re reporting this post.';
  end if;
  if p_reason = 'other' and char_length(body) < 5 then raise exception 'Tell us what''s wrong in at least 5 characters.'; end if;
  if char_length(body) > 1000 then raise exception 'Keep the details under 1,000 characters.'; end if;
  -- Plenty for real reports; stops one account from flooding the table.
  if (select count(*) from public.post_reports r where r.reporter_id = me and r.created_at > now() - interval '1 hour') >= 20 then
    raise exception 'You''ve sent a lot of reports in the last hour. Please try again later.';
  end if;
  select left(coalesce(nullif(p.display_name, ''), 'A SPOT member') || coalesce(' (@' || nullif(p.username, '') || ')', ''), 120)
    into who from public.profiles p where p.id = me;
  select left(coalesce(nullif(p.display_name, ''), 'A SPOT member') || coalesce(' (@' || nullif(p.username, '') || ')', ''), 120)
    into owner from public.profiles p where p.id = post.user_id;
  insert into public.post_reports (post_id, reporter_id, reporter, post_owner_id, post_owner, post_caption, reason, details)
  values (post.id, me, coalesce(who, ''), post.user_id, coalesce(owner, ''), left(post.caption, 300), p_reason, body)
  on conflict (post_id, reporter_id) do update set reason = excluded.reason, details = excluded.details,
    reporter = excluded.reporter, post_owner = excluded.post_owner, post_caption = excluded.post_caption, created_at = now();
end $$;

revoke all on function public.report_post(text, text, text) from public, anon;
grant execute on function public.report_post(text, text, text) to authenticated;
