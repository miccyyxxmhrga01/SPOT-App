-- SPOT AI's talent check in the post composer now runs on Gemini through the talent-ai Edge Function (it was a model
-- on Render, which slept between uses). It runs on its own each time a member picks or edits media, so it gets its own
-- allowance instead of using up AI Assist's: claim_talent_ai counts these checks separately.

alter table private.post_ai_requests add column kind text not null default 'assist' check (kind in ('assist', 'talent'));

-- As in spot_post_ai_limits, counting AI Assist requests only.
create or replace function public.claim_post_ai() returns void
language plpgsql security definer set search_path = '' as $$
declare me public.profiles := private.member();
begin
  -- Rows only matter for a day.
  delete from private.post_ai_requests where user_id = me.id and created_at < now() - interval '1 day';
  if (select count(*) from private.post_ai_requests r
      where r.user_id = me.id and r.kind = 'assist' and r.created_at > now() - interval '1 hour') >= 15 then
    raise exception 'You''ve used AI Assist a lot this hour. Try again later.';
  end if;
  if (select count(*) from private.post_ai_requests r where r.user_id = me.id and r.kind = 'assist') >= 50 then
    raise exception 'You''ve used AI Assist a lot today. Try again tomorrow.';
  end if;
  insert into private.post_ai_requests (user_id, kind) values (me.id, 'assist');
end $$;

create function public.claim_talent_ai() returns void
language plpgsql security definer set search_path = '' as $$
declare me public.profiles := private.member();
begin
  delete from private.post_ai_requests where user_id = me.id and created_at < now() - interval '1 day';
  if (select count(*) from private.post_ai_requests r
      where r.user_id = me.id and r.kind = 'talent' and r.created_at > now() - interval '1 hour') >= 30 then
    raise exception 'SPOT AI has checked a lot of media for you this hour. Choose a category yourself, or try again later.';
  end if;
  if (select count(*) from private.post_ai_requests r where r.user_id = me.id and r.kind = 'talent') >= 100 then
    raise exception 'SPOT AI has checked a lot of media for you today. Choose a category yourself, or try again tomorrow.';
  end if;
  insert into private.post_ai_requests (user_id, kind) values (me.id, 'talent');
end $$;

revoke all on function public.claim_talent_ai() from public, anon;
grant execute on function public.claim_talent_ai() to authenticated;
