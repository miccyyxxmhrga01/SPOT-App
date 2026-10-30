-- AI Assist (Generate Caption, Suggest Tags) runs on Gemini's free tier, whose quota the whole app shares. The
-- post-ai Edge Function calls claim_post_ai with the member's own session before each request, which both confirms
-- who is asking and keeps any one account from using up the quota.
create table private.post_ai_requests (
  user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);
create index post_ai_requests_user_idx on private.post_ai_requests (user_id, created_at desc);
revoke all on private.post_ai_requests from anon, authenticated;

create function public.claim_post_ai() returns void
language plpgsql security definer set search_path = '' as $$
declare me public.profiles := private.member();
begin
  -- Rows only matter for a day.
  delete from private.post_ai_requests where user_id = me.id and created_at < now() - interval '1 day';
  if (select count(*) from private.post_ai_requests r where r.user_id = me.id and r.created_at > now() - interval '1 hour') >= 15 then
    raise exception 'You''ve used AI Assist a lot this hour. Try again later.';
  end if;
  if (select count(*) from private.post_ai_requests r where r.user_id = me.id) >= 50 then
    raise exception 'You''ve used AI Assist a lot today. Try again tomorrow.';
  end if;
  insert into private.post_ai_requests (user_id) values (me.id);
end $$;

revoke all on function public.claim_post_ai() from public, anon;
grant execute on function public.claim_post_ai() to authenticated;
