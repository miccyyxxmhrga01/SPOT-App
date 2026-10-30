-- Search › Posts: finds posts by caption words, hashtags, talent categories and the author's name or @username,
-- newest first, 30 at a time. It runs as the caller (security invoker), so the posts table's row level security
-- decides what each member may see, exactly as in the feed: audiences, private accounts and blocks all apply.

create function public.search_posts(p_term text default '', p_offset integer default 0)
returns setof public.posts language sql stable set search_path = '' as $$
  with q as (
    select lower(trim(coalesce(p_term, ''))) as term,
      -- The term as a LIKE pattern: a typed % or _ matches only itself.
      replace(replace(replace(lower(trim(coalesce(p_term, ''))), '\', '\\'), '%', '\%'), '_', '\_') as pattern
  )
  select p.* from public.posts p, q
  where char_length(q.term) >= 2
    and (p.caption ilike '%' || q.pattern || '%'
      or exists (select 1 from unnest(p.tags) t where lower(t) like ltrim(q.pattern, '#') || '%')
      or exists (select 1 from unnest(p.categories) c where lower(c) like q.pattern || '%')
      or lower(p.author_display_name) like '%' || q.pattern || '%'
      or lower(p.author_username) like ltrim(q.pattern, '@') || '%')
  order by p.seq desc
  limit 30 offset greatest(coalesce(p_offset, 0), 0);
$$;

revoke all on function public.search_posts(text, integer) from public, anon;
grant execute on function public.search_posts(text, integer) to authenticated;
