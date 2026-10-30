-- SPOT moved to Supabase project srwvqtsgwpusgzyxjnkd: the messages and notifications triggers now call this
-- project's message-push Edge Function. Rewrites the two functions in place, so owners, grants and triggers stay.
do $$
declare
  f regprocedure;
  definition text;
begin
  foreach f in array array['private.push_new_message()'::regprocedure, 'private.push_new_notification()'::regprocedure] loop
    definition := pg_get_functiondef(f);
    if position('https://ogzaptsbcerltfwwxhhe.supabase.co/' in definition) = 0 then
      raise exception '% does not call the old project', f;
    end if;
    execute replace(definition, 'https://ogzaptsbcerltfwwxhhe.supabase.co/', 'https://srwvqtsgwpusgzyxjnkd.supabase.co/');
  end loop;
end $$;
