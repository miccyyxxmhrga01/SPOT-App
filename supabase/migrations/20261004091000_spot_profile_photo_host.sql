-- Profile photos must be on SPOT's Cloudinary, or Google's photo server for Google sign-ins. A photo on any other
-- server would show its owner the IP address of every phone that displays it. Posts, stories, events, groups and
-- feedback already check their media this way.

-- Profile setup uploads to spot/profile_images; Edit profile to spot/profiles/<member>/<upload>.
create function private.profile_photo_url(url text, owner uuid) returns boolean
language sql immutable set search_path = '' as $$
  select char_length(url) <= 2000
    and url ~ ('^https://res[.]cloudinary[.]com/jcuzzxsz/image/upload/(v[0-9]+/)?spot/(profile_images|profiles/'
      || owner::text || ')/.+$');
$$;

-- The photo a new account starts with. The app can put anything in sign-up metadata, so only Google's own photo
-- server is trusted; anything else starts without a photo.
create function private.photo_from(meta jsonb) returns text
language sql immutable set search_path = '' as $$
  select case when url ~ '^https://lh[0-9]+[.]googleusercontent[.]com/' then url else '' end
  from (select left(coalesce(meta ->> 'avatar_url', meta ->> 'picture', ''), 2000) as url) photo;
$$;

revoke all on function private.profile_photo_url(text, uuid), private.photo_from(jsonb) from public, anon, authenticated;

create or replace function private.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, full_name, display_name, profile_image_url)
  values (new.id, private.display_name_from(new.raw_user_meta_data), private.display_name_from(new.raw_user_meta_data),
    private.photo_from(new.raw_user_meta_data))
  on conflict (id) do nothing;
  return new;
end $$;

create or replace function public.ensure_profile() returns boolean
language plpgsql security definer set search_path = '' as $$
declare done boolean;
begin
  insert into public.profiles (id, full_name, display_name, profile_image_url)
  select u.id, private.display_name_from(u.raw_user_meta_data), private.display_name_from(u.raw_user_meta_data),
    private.photo_from(u.raw_user_meta_data)
  from auth.users u where u.id = private.me()
  on conflict (id) do nothing;
  select profile_completed into done from public.profiles where id = auth.uid();
  return coalesce(done, false);
end $$;

-- Edit profile sends the current photo back unchanged, so only a new photo is checked; an empty one removes it.
create or replace function public.save_profile(p_account_type text, p_display_name text, p_username text, p_course text,
  p_talents text[], p_organization text, p_seeking_talents text[], p_bio text, p_photo text, p_pronouns text,
  p_links text, p_gender text, p_completing boolean default false)
returns void language plpgsql security definer set search_path = '' as $$
declare
  me uuid := private.me();
  old public.profiles;
  name text := ltrim(lower(trim(coalesce(p_username, ''))), '@');
begin
  perform public.ensure_profile();
  select * into old from public.profiles where id = me for update;
  if p_account_type is null or p_account_type not in ('student', 'talent_seeker') then
    raise exception 'Choose an account type.';
  end if;
  if old.profile_completed and old.account_type is distinct from p_account_type then
    raise exception 'Your account type can''t be changed here.';
  end if;
  if char_length(trim(coalesce(p_display_name, ''))) not between 1 and 60 then
    raise exception 'Keep your display name to 60 characters.';
  end if;
  if name !~ '^[a-z0-9_]{3,30}$' then
    raise exception 'Use 3–30 letters, numbers or underscores for your username.';
  end if;
  if exists (select 1 from public.profiles where username = name and id <> me) then
    raise exception 'That username is already taken.';
  end if;
  if char_length(coalesce(p_pronouns, '')) > 40 or char_length(coalesce(p_links, '')) > 300
    or char_length(coalesce(p_gender, '')) > 40 then
    raise exception 'One of your fields is too long.';
  end if;
  if coalesce(p_links, '') <> '' and p_links not like 'https://%' then
    raise exception 'Use a full https:// link.';
  end if;
  if coalesce(p_photo, '') <> '' and p_photo is distinct from old.profile_image_url
    and not private.profile_photo_url(p_photo, me) then
    raise exception 'Your photo couldn''t be saved. Choose it again.';
  end if;

  update public.profiles set
    account_type = p_account_type,
    display_name = trim(p_display_name),
    full_name = case when full_name = '' then trim(p_display_name) else full_name end,
    username = name,
    course = case when p_account_type = 'student' then trim(coalesce(p_course, '')) else '' end,
    talent_categories = case when p_account_type = 'student' then coalesce(p_talents, '{}') else '{}' end,
    organization = case when p_account_type = 'talent_seeker' then trim(coalesce(p_organization, '')) else '' end,
    seeking_talents = case when p_account_type = 'talent_seeker' then coalesce(p_seeking_talents, '{}') else '{}' end,
    bio = trim(coalesce(p_bio, '')),
    profile_image_url = coalesce(p_photo, profile_image_url),
    pronouns = trim(coalesce(p_pronouns, '')),
    links = trim(coalesce(p_links, '')),
    gender = trim(coalesce(p_gender, '')),
    profile_completed = profile_completed or p_completing,
    updated_at = now()
  where id = me;
end $$;
