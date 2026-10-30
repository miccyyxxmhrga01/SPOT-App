-- Behavior tests for supabase/migrations: RLS visibility, counters, idempotent retries,
-- notifications, messaging, groups, events and work requests, acting as three members.
-- Everything runs in one transaction that is rolled back, so no data is left behind.
--
-- Run against a local Supabase stack or a branch database (not production):
--   psql "$DATABASE_URL" -f supabase/tests/spot_behavior_test.sql
-- Or on plain PostgreSQL 15+, in an empty database. The push migrations (spot_message_push,
-- spot_activity_push) need Supabase's pg_net and Vault, and spot_online_presence its realtime schema, so they are
-- left out:
--   psql -d spot -f supabase/tests/plain_postgres_stub.sql
--   psql -d spot -f supabase/migrations/20260927114140_spot_schema.sql
--   psql -d spot -f supabase/migrations/20260928001048_spot_remove_sample_conversations.sql
--   psql -d spot -f supabase/migrations/20260928044945_spot_collab_talent_removal.sql
--   psql -d spot -f supabase/migrations/20260928045849_spot_collab_profile_talents.sql
--   psql -d spot -f supabase/migrations/20260929042000_spot_chat_media_enc.sql
--   psql -d spot -f supabase/migrations/20260929043000_spot_chat_media_any_name.sql
--   psql -d spot -f supabase/migrations/20260930090000_spot_community.sql
--   psql -d spot -f supabase/migrations/20261002090000_spot_event_applications.sql
--   psql -d spot -f supabase/migrations/20261002150000_spot_profile_events.sql
--   psql -d spot -f supabase/migrations/20261003120000_spot_feedback.sql
--   psql -d spot -f supabase/migrations/20261004090000_spot_blocks_everywhere.sql
--   psql -d spot -f supabase/migrations/20261004091000_spot_profile_photo_host.sql
--   psql -d spot -f supabase/migrations/20261004130000_spot_post_reports.sql
--   psql -d spot -f supabase/migrations/20261004131000_spot_post_ai_limits.sql
--   psql -d spot -f supabase/migrations/20261004150000_spot_event_edit_cancel.sql
--   psql -d spot -f supabase/migrations/20261005090000_spot_event_application_fixes.sql
--   psql -d spot -f supabase/migrations/20261005140000_spot_owner_controls.sql
--   psql -d spot -f supabase/migrations/20261005150000_spot_chat_pin_unread.sql
--   psql -d spot -f supabase/migrations/20261008090000_spot_delete_account.sql
--   psql -d spot -f supabase/migrations/20261009090000_spot_read_receipts.sql
--   psql -d spot -f supabase/migrations/20261009092000_spot_alert_switches.sql
--   psql -d spot -f supabase/migrations/20261009093000_spot_search_posts.sql
--   psql -d spot -f supabase/migrations/20261009094000_spot_talent_ai_limits.sql
--   psql -d spot -f supabase/tests/spot_behavior_test.sql
-- It prints ALL BEHAVIOR TESTS PASSED, or stops at the first failing assertion.
\set ON_ERROR_STOP 1
\pset tuples_only on
\pset format unaligned
begin;
create schema test;
grant usage on schema test to authenticated;
create function test.fails(statement text, expected text) returns void language plpgsql as $$
begin
  execute statement;
  raise exception 'EXPECTED FAILURE did not happen: %', statement;
exception when others then
  if sqlerrm like 'EXPECTED FAILURE%' then raise; end if;
  if sqlerrm not like '%' || expected || '%' then raise exception 'WRONG ERROR for %: %', statement, sqlerrm; end if;
end $$;
create function test.ok(condition boolean, label text) returns void language plpgsql as $$
begin if condition is not true then raise exception 'ASSERTION FAILED: %', label; end if; end $$;
create function test.act(uid text) returns void language sql as $$ select set_config('request.jwt.claim.sub', uid, false) $$;
grant execute on all functions in schema test to authenticated;

insert into auth.users (id, email, raw_user_meta_data) values
 ('00000000-0000-0000-0000-00000000000a', 'a@x.test', '{"full_name":"Ana Cruz"}'),
 ('00000000-0000-0000-0000-00000000000b', 'b@x.test', '{"name":"Ben","picture":"https://lh3.googleusercontent.com/p.jpg"}'),
 ('00000000-0000-0000-0000-00000000000c', 'c@x.test', '{}');
select test.ok((select count(*) from profiles) = 3, 'signup trigger creates profiles');
select test.ok((select profile_image_url from profiles where display_name = 'Ben') like 'https://lh3%', 'google photo copied');

set role authenticated;
select test.act('');
select test.fails('select ensure_profile()', 'Sign in to continue.');

-- A: student
select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok(ensure_profile() = false, 'A not completed yet');
select test.fails($q$select publish_post('p1','hi','{}','{}','[]','public','','{}',false,false,false)$q$, 'Finish setting up your profile first.');
select save_profile('student','Ana Cruz','@Ana_C','BS Music', array['Singer','Dancer'], 'ignored', array['x'], 'hi', null, '', '', '', true);
select test.ok(ensure_profile(), 'A completed');
select test.ok((select username from profiles where display_name='Ana Cruz') = 'ana_c', 'username normalized');
select test.ok((select organization from profiles where username='ana_c') = '', 'student drops organization');
select test.fails($q$select save_profile('talent_seeker','Ana','ana_c','','{}','Org','{}','',null,'','','',false)$q$, 'account type can''t be changed');
select test.fails($q$update profiles set bio = 'hack'$q$, 'permission denied');
select test.fails($q$insert into posts (id, user_id, audience) values ('x', auth.uid(), 'public')$q$, 'permission denied');

-- B: seeker, username taken
select test.act('00000000-0000-0000-0000-00000000000b');
select test.fails($q$select save_profile('talent_seeker','Ben','ana_c','','{}','Studio','{}','',null,'','','',true)$q$, 'already taken');
select save_profile('talent_seeker','Ben Reyes','ben','', '{}','Indie Studio', array['Singer'], '', null, 'he/him', 'https://ben.test', '', true);
select test.fails($q$select save_profile('talent_seeker','Ben Reyes','ben','', '{}','x','{}','',null,'','http://bad','',false)$q$, 'https://');

-- C: student, later private
select test.act('00000000-0000-0000-0000-00000000000c');
select save_profile('student','Cara','cara','BS Art', array['Photographer'], '', '{}', '', 'https://res.cloudinary.com/jcuzzxsz/image/upload/v1/spot/profile_images/c.jpg', '', '', '', true);

-- Posts
select test.act('00000000-0000-0000-0000-00000000000a');
select test.fails($q$select publish_post('p1','hi','{}','{}','[{"url":"https://evil.test/x.jpg","contentType":"image/jpeg"}]','public','','{}',false,false,false)$q$, 'Media upload failed');
select (publish_post('p1','Campus show','{Singer}','{music}',
  '[{"url":"https://res.cloudinary.com/jcuzzxsz/video/upload/v1/spot/posts/00000000-0000-0000-0000-00000000000a/p1/a.mp4","contentType":"video/mp4","storagePath":"","thumbnailUrl":"https://res.cloudinary.com/jcuzzxsz/video/upload/so_0,w_960,c_limit,q_auto/v1/spot/posts/00000000-0000-0000-0000-00000000000a/p1/a.jpg"}]',
  'public','Manila','{}',true,false,false)).id;
select test.ok((select media_type from posts where id='p1') = 'video', 'video media type');
select test.ok((select author_talent from posts where id='p1') = 'Singer', 'author snapshot');
select test.ok((select (publish_post('p1','retry','{}','{}','[]','public','','{}',false,false,false)).caption) = 'Campus show', 'publish retry idempotent');
select publish_post('p2','Followers only','{}','{}','[]','followers','','{}',false,false,false);
select publish_post('p3','Just me','{}','{}','[]','private','','{}',false,false,false);
select test.ok((select count(*) from posts) = 3, 'author sees own 3 posts');

select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok((select count(*) from posts) = 1, 'stranger sees only public post');
select set_post_interaction('p1', true, true);
select set_post_interaction('p1', true, true);
select set_post_interaction('p1', null, true);
select test.ok((select likes_count from posts where id='p1') = 1 and (select views_count from posts where id='p1') = 1, 'like+view counted once');
select test.ok((select popularity_score from posts where id='p1') = 6, 'popularity = likes*5+views');
select test.fails($q$select set_post_interaction('p2', true)$q$, 'no longer available');
select add_comment('p1', 'c1', '  Great!  ');
select add_comment('p1', 'c1', 'Great!');
select test.fails($q$select add_comment('p1', 'c1', 'Different')$q$, 'retry doesn''t match');
select test.ok((select comments_count from posts where id='p1') = 1, 'comment counted once');
select set_following('00000000-0000-0000-0000-00000000000a', true);
select set_following('00000000-0000-0000-0000-00000000000a', true);
select test.ok((select count(*) from posts) = 2, 'follower now sees followers-only post');
select test.fails($q$select request_follow('00000000-0000-0000-0000-00000000000c', true)$q$, 'public');
select test.ok((select count(*) from notifications) = 0, 'B cannot read A''s notifications');

select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok((select string_agg(type, ',' order by seq) from notifications) = 'like,comment,follow', 'A notified once each');
update notifications set is_read = true where is_read = false;
select test.ok((select count(*) from notifications where not is_read) = 0, 'mark all read');
select test.fails($q$update notifications set message = 'x'$q$, 'permission denied');
select set_comment_liked('c1', true);
select set_comment_liked('c1', true);
select test.ok((select likes_count from comments where id='c1') = 1, 'comment like counted once');
-- Neither the writer nor the post's owner.
select test.act('00000000-0000-0000-0000-00000000000c');
select test.fails($q$select delete_comment('p1','c1')$q$, 'only delete your own');
select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok((select followers from profile_stats('00000000-0000-0000-0000-00000000000a')) = 1, 'profile stats');

select test.act('00000000-0000-0000-0000-00000000000c');
select set_private(true);
select test.act('00000000-0000-0000-0000-00000000000a');
select test.fails($q$select set_following('00000000-0000-0000-0000-00000000000c', true)$q$, 'private');
select test.act('00000000-0000-0000-0000-00000000000c');
select test.ok((select count(*) from follow_requests) = 0, 'no request yet');
select test.act('00000000-0000-0000-0000-00000000000b');
select request_follow('00000000-0000-0000-0000-00000000000c', true);
select test.act('00000000-0000-0000-0000-00000000000c');
select test.ok((select count(*) from follow_requests) = 1, 'target sees request');
select review_follow_request('00000000-0000-0000-0000-00000000000b', true);
select test.ok(exists (select 1 from follows where follower_id = '00000000-0000-0000-0000-00000000000b' and following_id = auth.uid()), 'approved request follows');
select publish_post('cp','Public attempt','{}','{}','[]','public','','{}',false,false,false);
select test.ok((select audience from posts where id='cp') = 'followers', 'private account clamps audience');

-- Stories and highlights
select test.act('00000000-0000-0000-0000-00000000000a');
select create_story('s1', 'https://res.cloudinary.com/jcuzzxsz/image/upload/v1/spot/stories/00000000-0000-0000-0000-00000000000a/s1/x.jpg', false, 'public', '{"scale":1}');
select create_story('s1', 'https://res.cloudinary.com/jcuzzxsz/image/upload/v1/spot/stories/00000000-0000-0000-0000-00000000000a/s1/x.jpg', false, 'public', '{"scale":1.0}');
select test.fails($q$select set_story_liked('s1', true)$q$, 'own story');
select save_highlight('h1', 'Best', '[{"source":"story","sourceId":"s1","index":0,"url":"https://res.cloudinary.com/jcuzzxsz/image/upload/v1/spot/stories/00000000-0000-0000-0000-00000000000a/s1/x.jpg","type":"image"},{"source":"post","sourceId":"p1","index":0,"url":"https://res.cloudinary.com/jcuzzxsz/video/upload/v1/spot/posts/00000000-0000-0000-0000-00000000000a/p1/a.mp4","type":"video"}]', 1, 'public');
select test.fails($q$select save_highlight('h2', 'Bad', '[{"source":"post","sourceId":"p1","index":0,"url":"https://other","type":"image"}]', 0, 'public')$q$, 'no longer available');
select test.act('00000000-0000-0000-0000-00000000000b');
select set_story_liked('s1', true);
select set_story_liked('s1', true);
select test.ok((select likes_count from stories where id='s1') = 1, 'story like once');
select test.ok((select count(*) from highlights) = 1, 'public highlight visible');

-- Messages
select test.act('00000000-0000-0000-0000-00000000000a');
select create_conversation('chat1', array['00000000-0000-0000-0000-00000000000b']::uuid[], '', 'hey', false);
select test.fails($q$select send_message('chat1', 'm0', 'too early')$q$, 'invitation was accepted');
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok((select count(*) from conversations) = 1, 'invitee sees conversation');
select respond_conversation('chat1', true);
select send_message('chat1', 'm1', ' hello ');
select send_message('chat1', 'm1', ' hello ');
select test.ok((select preview from conversations where id='chat1') = 'hello', 'preview updated');
select set_chat_preferences('chat1', '{"readAt":true,"nickname":"Ana!"}');
select test.ok((select nickname from chat_settings) = 'Ana!' and (select read_at from chat_settings) is not null, 'chat prefs');
select test.fails($q$select send_message('chat1', 'm2', '', '{"path":"chat_media/chat1/x/m2","mime":"image/png","name":"a","size":10}')$q$, 'invalid attachment');
select send_message('chat1', 'm3', '', '{"path":"chat_media/chat1/00000000-0000-0000-0000-00000000000b/m3","mime":"video/mp4","name":"clip","size":10,"url":"https://res.cloudinary.com/jcuzzxsz/raw/upload/v12/spot/chat_media/chat1/00000000-0000-0000-0000-00000000000b/m3/f.enc","key":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","iv":"AAAAAAAAAAAAAAAA"}');
select test.ok((select preview from conversations where id='chat1') = 'Video', 'attachment preview');
select unsend_message('chat1', 'm3');
select test.ok((select preview from conversations where id='chat1') = 'Message unsent' and (select attachment from messages where id='m3') is null, 'unsend clears');
select test.ok((select count(*) from older_messages('chat1', 'm3')) = 1, 'older messages before m3');
insert into hidden_messages (user_id, conversation_id, message_id) values (auth.uid(), 'chat1', 'm1');
insert into blocks (blocker_id, blocked_id) values (auth.uid(), '00000000-0000-0000-0000-00000000000a');
select test.fails($q$select send_message('chat1', 'm4', 'blocked?')$q$, 'invitation was accepted');
delete from blocks;
select test.act('00000000-0000-0000-0000-00000000000c');
select test.ok((select count(*) from conversations) = 0, 'outsider sees no chats');
select test.fails($q$select send_message('chat1', 'x1', 'intrude')$q$, 'Join this conversation');

-- Groups and events
select test.act('00000000-0000-0000-0000-00000000000a');
select create_group('g1', 'Choir', 'We sing', '', 'Singer', 'private');
select test.act('00000000-0000-0000-0000-00000000000b');
select test.fails($q$select set_group_joined('g1', true)$q$, 'Request to join');
select request_join_group('g1');
select test.ok((select count(*) from group_members) = 0, 'private roster hidden from outsider');
select test.act('00000000-0000-0000-0000-00000000000a');
select review_group_request('g1', '00000000-0000-0000-0000-00000000000b', true);
select test.ok((select member_count from groups where id='g1') = 2, 'approved member counted');
select test.ok((select message from notifications where type = 'group_request' and target_id = 'g1') = 'asked to join Choir', 'owner hears about join requests');
select test.fails($q$select create_event('e1','Gig','desc','', 'Singer', now() + interval '1 day','Hall','Ana',null)$q$, 'Only Spotters');
select test.act('00000000-0000-0000-0000-00000000000b');
select test.fails($q$select create_event('e0','Gig','desc','', 'Singer', now() + interval '1 day','Hall','Ben', 1, 'gratis')$q$, 'For Free Only');
select create_event('e1','Gig Night','desc','', 'Singer', now() + interval '1 day','Hall','Ben', 1, 'paid', ' 1,500 per act ');
select create_event('e2','Open Mic','desc','', 'Music', now() + interval '2 days','Hall','Ben', null, 'free', 'ignored');
select test.ok((select talent_fee || ':' || talent_fee_budget from events where id = 'e1') = 'paid:1,500 per act'
  and (select talent_fee || ':' || talent_fee_budget from events where id = 'e2') = 'free:', 'talent fee saved; only paid events carry a budget');
select test.fails($q$select apply_to_event('e1')$q$, 'Only Creators');
-- Creators apply; nobody takes a slot or enters the chat until the organizer accepts.
select test.act('00000000-0000-0000-0000-00000000000a');
select test.fails($q$select set_event_joined('e1', true)$q$, 'Update SPOT');
select apply_to_event('e1', ' I sing ballads ');
select apply_to_event('e1');
select test.ok((select participant_count from events where id = 'e1') = 0
  and (select status || ':' || note from event_applications) = 'pending:I sing ballads', 'an application waits for the organizer');
select test.fails($q$select open_community_chat('event', 'e1')$q$, 'Join this event');
select test.act('00000000-0000-0000-0000-00000000000c');
select apply_to_event('e1');
select test.ok((select count(*) from event_applications) = 1, 'applicants see only their own application');
select test.fails($q$select review_event_application('e1', '00000000-0000-0000-0000-00000000000c', true)$q$, 'Only the event''s organizer');
select test.fails($q$select open_community_chat('event', 'e1')$q$, 'Join this event');
select test.fails($q$select open_community_chat('group', 'g1')$q$, 'Join this group');
select test.ok((select count(*) from conversations where community_kind is not null) = 0, 'outsider sees no community chats');
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok((select string_agg(display_name, ',' order by seq) from event_applications where event_id = 'e1') = 'Ana Cruz,Cara',
  'organizer sees each application under the Creator''s name');
select review_event_application('e1', '00000000-0000-0000-0000-00000000000a', true);
select test.fails($q$select review_event_application('e1', '00000000-0000-0000-0000-00000000000a', false)$q$, 'already been reviewed');
select test.fails($q$select review_event_application('e1', '00000000-0000-0000-0000-00000000000c', true)$q$, 'All slots are filled');
select review_event_application('e1', '00000000-0000-0000-0000-00000000000c', false);
select test.ok((select participant_count from events where id = 'e1') = 1, 'an accepted application takes one slot');

-- Community chats, group pages and their notifications
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok((select string_agg(type, ',' order by seq) from notifications where target_id in ('g1', 'e1'))
  = 'group_approved,event_application,event_application', 'applicant hears about approval, organizer about applications');
select test.ok((select message from notifications where type = 'event_application' and sender_id = '00000000-0000-0000-0000-00000000000a')
  = 'applied to perform at Gig Night', 'application notification names the event');
select test.ok((select cardinality(member_ids) from conversations where community_kind = 'event' and community_id = 'e1') = 2
  and (select title from conversations where community_kind = 'event') = 'Gig Night', 'an accepted application joins the event chat');
select send_message(open_community_chat('event', 'e1'), 'em1', 'Welcome to the gig');
select test.ok(open_community_chat('group', 'g1') = (select id from conversations where community_kind = 'group'), 'group chat opens once');
select test.ok((select member_ids from conversations where community_kind = 'group')
  = array['00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b']::uuid[], 'group chat holds owner and members');
select test.fails($q$select leave_conversation((select id from conversations where community_kind = 'event'))$q$, 'Leave the event');
select create_group_post('gp1', 'g1', ' First rehearsal is on Friday ');
select create_group_post('gp1', 'g1', 'retry');
select test.fails($q$select create_group_post('gp2', 'g1', '  ')$q$, 'Write something');
select test.fails($q$select create_group_post('gp2', 'g1', 'x', 'https://evil.test/a.jpg')$q$, 'Image upload failed');
select create_group_post('gp2', 'g1', '', 'https://res.cloudinary.com/jcuzzxsz/image/upload/v1/spot/group_posts/00000000-0000-0000-0000-00000000000b/gp2/a.jpg');
select set_group_post_liked('gp1', true);
select set_group_post_liked('gp1', true);
select test.ok((select text from group_posts where id = 'gp1') = 'First rehearsal is on Friday'
  and (select likes_count from group_posts where id = 'gp1') = 1 and (select count(*) from group_posts) = 2, 'group post saved once, liked once');
select set_group_post_liked('gp1', false);
select test.ok((select likes_count from group_posts where id = 'gp1') = 0 and (select count(*) from group_post_likes) = 0, 'group post unliked');
select test.act('00000000-0000-0000-0000-00000000000c');
select test.ok((select count(*) from group_posts) = 0, 'private group page hidden from outsiders');
select test.fails($q$select create_group_post('gp3', 'g1', 'hi')$q$, 'Join this group');
select test.fails($q$select set_group_post_liked('gp1', true)$q$, 'Join this group');
select test.fails($q$select delete_group_post('gp1')$q$, 'only delete your own');
select test.ok((select message from notifications where type = 'event_application_declined') = 'declined your application for Gig Night',
  'a declined Creator hears the answer');
select test.fails($q$select apply_to_event('e1')$q$, 'declined your application');
select test.ok((select count(*) from conversations where community_kind = 'event') = 0, 'a declined Creator stays out of the event chat');
select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok((select count(*) from notifications where type = 'group_post' and target_id = 'g1') = 2, 'owner hears about posts');
select test.ok((select preview from conversations where community_kind = 'event') = 'Welcome to the gig', 'participant reads the event chat');
select test.ok((select message from notifications where type = 'event_application_accepted') = 'accepted your application for Gig Night',
  'an accepted Creator hears the answer');
select delete_group_post('gp2');
select test.ok((select count(*) from group_posts) = 1, 'owner removes a member''s post');
select set_event_joined('e1', false);
select test.ok((select count(*) from conversations where community_kind = 'event') = 0, 'leaving an event leaves its chat');
select test.ok((select participant_count from events where id = 'e1') = 0 and (select count(*) from event_applications) = 0,
  'leaving frees the slot and clears the application');
select apply_to_event('e1');
select test.act('00000000-0000-0000-0000-00000000000b');
select review_event_application('e1', '00000000-0000-0000-0000-00000000000a', true);
select test.ok((select count(*) from notifications where type = 'event_application' and sender_id = '00000000-0000-0000-0000-00000000000a') = 1,
  'applying again doesn''t notify twice');
select test.ok((select party_ids from conversations where community_kind = 'event')
  = array['00000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-00000000000a']::uuid[], 'rejoining returns to the chat once');
select test.ok((select count(*) from search_groups('cho', '', 0)) = 1 and (select count(*) from search_events('', 'Singer', 0)) = 1, 'directory search');
select test.ok((select count(*) from search_profiles('ana', false, 'student', null, 30, 0)) = 1, 'profile search by name');
select test.ok((select count(*) from search_profiles('be', true, null, null, 30, 0)) = 1, 'profile search by username');

-- Work
select test.act('00000000-0000-0000-0000-00000000000b');
select publish_opportunity('o1', 'Singer wanted', 'audition', 'Looking for a lead vocalist', 'Singer', 'Online', now() + interval '3 days');
select create_talent_request('invitation', 'e1', '00000000-0000-0000-0000-00000000000a', 'Please perform at our gig');
select test.fails($q$select create_talent_request('invitation', 'e1', '00000000-0000-0000-0000-00000000000a', 'Please perform at our gig')$q$, 'already exists');
select test.ok((select preview from conversations where id = 'chat1') = E'spot://event/e1\nPlease perform at our gig', 'invitation arrives in the direct chat');
select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok((select message from notifications where type = 'invitation') = 'invited you to Gig Night'
  and (select target_id from notifications where type = 'invitation') = 'invitation_e1_00000000-0000-0000-0000-00000000000a', 'creator hears about the invitation');
select create_talent_request('application', 'o1', auth.uid(), 'I would love to audition');
select test.fails($q$select respond_talent_request('application_o1_00000000-0000-0000-0000-00000000000a', 'accepted')$q$, 'no longer available');
select respond_talent_request('invitation_e1_00000000-0000-0000-0000-00000000000a', 'accepted');
select test.ok((select participant_count from events where id = 'e1') = 1, 'an invitee who already joined is counted once');
select test.act('00000000-0000-0000-0000-00000000000c');
select test.ok((select count(*) from talent_requests) = 0, 'outsider sees no requests');
-- An invited creator has no chat with the Spotter yet, and the event is already full.
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok((select string_agg(message || ' > ' || target_id, ',' order by seq) from notifications where type in ('application', 'invitation_accepted'))
  = 'applied to Singer wanted > o1,accepted your invitation to Gig Night > invitation_e1_00000000-0000-0000-0000-00000000000a',
  'Spotter hears about applications and answers');
select respond_talent_request('application_o1_00000000-0000-0000-0000-00000000000a', 'declined');
select create_talent_request('invitation', 'e1', '00000000-0000-0000-0000-00000000000c', 'Please take photos at our gig');
select test.act('00000000-0000-0000-0000-00000000000c');
select test.ok((select count(*) from conversations where kind = 'direct' and auth.uid() = any (member_ids)
  and preview like 'spot://event/e1%') = 1, 'invitation starts a direct chat with both in it');
select respond_talent_request('invitation_e1_00000000-0000-0000-0000-00000000000c', 'accepted');
select test.ok((select participant_count from events where id = 'e1') = 2
  and (select cardinality(member_ids) from conversations where community_kind = 'event') = 3, 'accepting an invitation joins the event and its chat');
select test.fails($q$select respond_talent_request('invitation_e1_00000000-0000-0000-0000-00000000000c', 'declined')$q$, 'already been updated');
select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok((select message || ' > ' || target_id from notifications where type = 'application_declined')
  = 'declined your application for Singer wanted > o1', 'creator hears the answer to an application');

-- Collaboration: onboarding talents count, SPOT AI talents add to them, and removals stick.
select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok((select array_agg(talent order by talent) from user_talents where user_id = auth.uid() and source = 'profile')
  = array['Dancer', 'Singer'], 'onboarding talents count for collabs');
insert into user_talents (user_id, talent, confidence, source) values (auth.uid(), 'Singing', 0.9, 'ai');
select save_profile('student', 'Ana Cruz', 'ana_c', 'BS Music', array['Singer', 'Guitar'], '', '{}', 'hi', null, '', '', '', false);
select test.ok((select array_agg(talent order by talent) from user_talents where user_id = auth.uid())
  = array['Guitar', 'Singer', 'Singing'], 'profile edits sync and SPOT AI talents stay');
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok((select count(*) from get_collaboration_suggestions() where user_id = '00000000-0000-0000-0000-00000000000a') = 3, 'suggestions');
delete from user_talents where user_id = '00000000-0000-0000-0000-00000000000a';
select test.ok((select count(*) from user_talents where user_id = '00000000-0000-0000-0000-00000000000a') = 3, 'cannot remove someone else''s talent');
select test.act('00000000-0000-0000-0000-00000000000a');
delete from user_talents where user_id = auth.uid() and talent in ('Singing', 'Guitar');
select save_profile('student', 'Ana Cruz', 'ana_c', 'BS Music', array['Singer', 'Guitar'], '', '{}', 'hi', null, '', '', '', false);
select test.ok((select array_agg(talent) from user_talents where user_id = auth.uid()) = array['Singer'], 'removed talents stay removed');
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok((select count(*) from get_collaboration_suggestions() where user_id = '00000000-0000-0000-0000-00000000000a') = 1, 'removed talents leave suggestions');

-- Suggested collaborators: a reason for each, preferences, dismissals and blocks.
select test.act('00000000-0000-0000-0000-00000000000c');
insert into user_talents (user_id, talent, confidence, source) values (auth.uid(), 'Photography', 0.8, 'ai');
select test.act('00000000-0000-0000-0000-00000000000a');
insert into user_talents (user_id, talent, confidence, source) values (auth.uid(), 'Dancing', 0.9, 'ai');
select set_collab_preferences(' Manila ', '{}', true);
select test.ok((select reason from suggest_collaborators() where user_id = '00000000-0000-0000-0000-00000000000c')
  = 'Photography complements your Dancing', 'complementary talents explain the match');
select test.ok(not exists (select 1 from suggest_collaborators() where user_id = auth.uid()), 'never suggests yourself');
select test.fails($q$select set_collab_preferences('', array['a','b','c','d','e','f','g','h','i'], true)$q$, 'up to 8');
select test.act('00000000-0000-0000-0000-00000000000b');
select set_collab_preferences('manila', array['Photography'], true);
select test.ok((select string_agg(username || ':' || score::int || ':' || reason, ' | ' order by score desc) from suggest_collaborators())
  = 'ana_c:6:Singer is a talent you''re looking for · Also in Manila | cara:3:Photography is a talent you''re looking for',
  'sought talents, location and shared interests rank and explain suggestions');
insert into collab_dismissals (user_id, dismissed_id) values (auth.uid(), '00000000-0000-0000-0000-00000000000c');
select test.ok(not exists (select 1 from suggest_collaborators() where user_id = '00000000-0000-0000-0000-00000000000c')
  and (select count(*) from get_collaboration_suggestions() where user_id = '00000000-0000-0000-0000-00000000000c') = 0,
  'dismissed suggestions stay hidden');
delete from collab_dismissals;
select test.ok(exists (select 1 from suggest_collaborators() where user_id = '00000000-0000-0000-0000-00000000000c'), 'undoing a dismissal');
select test.act('00000000-0000-0000-0000-00000000000c');
insert into blocks (blocker_id, blocked_id) values (auth.uid(), '00000000-0000-0000-0000-00000000000a');
select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok(not exists (select 1 from suggest_collaborators() where user_id = '00000000-0000-0000-0000-00000000000c'),
  'blocked accounts aren''t suggested');
select test.act('00000000-0000-0000-0000-00000000000c');
delete from blocks;
select set_collab_preferences('', '{}', false);
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok(not exists (select 1 from suggest_collaborators() where user_id = '00000000-0000-0000-0000-00000000000c'),
  'people who aren''t open to collaborating aren''t suggested');

-- Profiles list the events a member performed at, as far as the viewer may see that member.
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok((select string_agg(id, ',') from profile_events('00000000-0000-0000-0000-00000000000a')) = 'e1', 'a public Creator''s events show');
select test.ok((select string_agg(id, ',') from profile_events('00000000-0000-0000-0000-00000000000c')) = 'e1', 'followers see a private Creator''s events');
select test.ok(not exists (select 1 from profile_events('00000000-0000-0000-0000-00000000000b')), 'organizing isn''t performing');
select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok(not exists (select 1 from profile_events('00000000-0000-0000-0000-00000000000c')), 'a private Creator''s events stay hidden from others');
select test.ok((select string_agg(id, ',') from profile_events(auth.uid())) = 'e1', 'you see your own events');
select test.act('00000000-0000-0000-0000-00000000000c');
insert into blocks (blocker_id, blocked_id) values (auth.uid(), '00000000-0000-0000-0000-00000000000b');
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok(not exists (select 1 from profile_events('00000000-0000-0000-0000-00000000000c')), 'blocks hide a member''s events');
select test.act('00000000-0000-0000-0000-00000000000c');
delete from blocks;
select test.act('');
select test.ok(not exists (select 1 from profile_events('00000000-0000-0000-0000-00000000000a')), 'signed-out callers see nothing');

-- Feedback: one row per report (retries included), checked input, and no direct access to the table.
select test.act('00000000-0000-0000-0000-00000000000a');
select submit_feedback('00000000-0000-0000-0000-0000000000f1', 'bug', '  The chat list stays empty.  ', null, '3.1 (7)', 'Realme RMX2180');
select submit_feedback('00000000-0000-0000-0000-0000000000f1', 'bug', 'The chat list stays empty.', null, '3.1 (7)', 'Realme RMX2180');
select test.fails($q$select submit_feedback('00000000-0000-0000-0000-0000000000f2', 'bug', 'short', null, '', '')$q$, 'at least 10 characters');
select test.fails($q$select submit_feedback('00000000-0000-0000-0000-0000000000f2', 'praise', 'A long enough message.', null, '', '')$q$, 'Choose feedback or bug');
select test.fails($q$select submit_feedback('00000000-0000-0000-0000-0000000000f2', 'feedback', 'A long enough message.', 'https://evil.test/x.png', '', '')$q$, 'couldn''t be attached');
select submit_feedback('00000000-0000-0000-0000-0000000000f3', 'feedback', 'Love the new profile setup!',
  'https://res.cloudinary.com/jcuzzxsz/image/upload/v1/spot/feedback/00000000-0000-0000-0000-00000000000a/00000000-0000-0000-0000-0000000000f3/s.jpg', '', '');
select test.fails($q$select count(*) from feedback$q$, 'permission denied');
select test.fails($q$insert into feedback (id, user_id, kind, message) values (gen_random_uuid(), auth.uid(), 'bug', 'Sneaky direct insert.')$q$, 'permission denied');
select test.act('00000000-0000-0000-0000-00000000000b');
select test.fails($q$select submit_feedback('00000000-0000-0000-0000-0000000000f1', 'bug', 'Reusing someone else''s id.', null, '', '')$q$, 'doesn''t match');
select test.act('');
select test.fails($q$select submit_feedback(gen_random_uuid(), 'bug', 'Signed-out callers can''t send.', null, '', '')$q$, 'Sign in to continue.');
reset role;
select test.ok((select count(*) from feedback) = 2, 'one row per report, retries included');
select test.ok((select message from feedback where id = '00000000-0000-0000-0000-0000000000f1') = 'The chat list stays empty.', 'feedback message trimmed');
select test.ok((select sender from feedback where id = '00000000-0000-0000-0000-0000000000f1') = 'Ana Cruz (@ana_c)', 'feedback sender recorded');
set role authenticated;
select test.act('00000000-0000-0000-0000-00000000000c');
select count(submit_feedback(gen_random_uuid(), 'feedback', 'Report number ' || n || ' is here.', null, '', '')) from generate_series(1, 10) n;
select test.fails($q$select submit_feedback(gen_random_uuid(), 'feedback', 'One report too many.', null, '', '')$q$, 'a lot of reports');

-- Blocks reach beyond Messages: posts, stories, highlights, comments, likes, follows and notifications, both ways.
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok(not exists (select 1 from follows where follower_id = auth.uid() and following_id = '00000000-0000-0000-0000-00000000000a'),
  'blocking in Messages ended the follow');
select set_following('00000000-0000-0000-0000-00000000000a', true);
select publish_post('bp', 'Casting call', '{}', '{}', '[]', 'public', '', '{}', false, false, false);
select test.ok((select count(*) from posts where user_id = '00000000-0000-0000-0000-00000000000a') = 2
  and (select count(*) from comments where post_id = 'p1') = 1, 'a follower sees public and followers-only posts and comments');
select test.act('00000000-0000-0000-0000-00000000000a');
select set_following('00000000-0000-0000-0000-00000000000b', true);
insert into blocks (blocker_id, blocked_id) values (auth.uid(), '00000000-0000-0000-0000-00000000000b');
select test.ok(not exists (select 1 from follows where auth.uid() in (follower_id, following_id)
  and '00000000-0000-0000-0000-00000000000b' in (follower_id, following_id)), 'blocking ends follows both ways');
select test.ok(not exists (select 1 from posts where id = 'bp'), 'the blocker stops seeing the blocked member''s posts');
select test.ok(exists (select 1 from posts where id = 'p1') and not exists (select 1 from comments where id = 'c1'),
  'or their comments, even on the blocker''s own posts');
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok(not exists (select 1 from posts where user_id = '00000000-0000-0000-0000-00000000000a')
  and not exists (select 1 from stories where user_id = '00000000-0000-0000-0000-00000000000a')
  and not exists (select 1 from highlights where user_id = '00000000-0000-0000-0000-00000000000a'),
  'the blocked member sees none of the blocker''s posts, stories or highlights');
select test.fails($q$select add_comment('p1', 'c9', 'Still here?')$q$, 'no longer available');
select test.fails($q$select set_post_interaction('p1', false)$q$, 'no longer available');
select test.fails($q$select set_following('00000000-0000-0000-0000-00000000000a', true)$q$, 'can''t follow');
reset role;
select private.notify('00000000-0000-0000-0000-00000000000a', 'blocked_like', 'like', 'p1', '', 'liked your post');
select test.ok(not exists (select 1 from notifications where id = 'blocked_like'), 'blocked members aren''t notified about each other');
set role authenticated;
select request_follow('00000000-0000-0000-0000-00000000000c', true);
select test.act('00000000-0000-0000-0000-00000000000c');
select test.ok((select count(*) from follow_requests) = 1, 'pending follow request');
insert into blocks (blocker_id, blocked_id) values (auth.uid(), '00000000-0000-0000-0000-00000000000b');
select test.ok((select count(*) from follow_requests) = 0, 'blocking clears follow requests');
select test.act('00000000-0000-0000-0000-00000000000b');
select test.fails($q$select request_follow('00000000-0000-0000-0000-00000000000c', true)$q$, 'can''t follow');
select test.act('00000000-0000-0000-0000-00000000000c');
delete from blocks;
select test.act('00000000-0000-0000-0000-00000000000a');
delete from blocks;
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok((select string_agg(id, ',') from posts where user_id = '00000000-0000-0000-0000-00000000000a') = 'p1'
  and exists (select 1 from comments where id = 'c1'), 'unblocking shows public posts again but doesn''t restore follows');

-- Profile photos stay on SPOT's Cloudinary, or Google's photo server for Google sign-ins.
select test.act('00000000-0000-0000-0000-00000000000a');
select test.fails($q$select save_profile('student','Ana Cruz','ana_c','BS Music', array['Singer'], '', '{}', 'hi', 'https://evil.test/me.jpg', '', '', '', false)$q$, 'photo couldn''t be saved');
select test.fails($q$select save_profile('student','Ana Cruz','ana_c','BS Music', array['Singer'], '', '{}', 'hi', 'https://res.cloudinary.com/jcuzzxsz/image/fetch/https://evil.test/me.jpg', '', '', '', false)$q$, 'photo couldn''t be saved');
select test.fails($q$select save_profile('student','Ana Cruz','ana_c','BS Music', array['Singer'], '', '{}', 'hi', 'https://res.cloudinary.com.evil.test/jcuzzxsz/image/upload/v1/spot/profile_images/me.jpg', '', '', '', false)$q$, 'photo couldn''t be saved');
select test.fails($q$select save_profile('student','Ana Cruz','ana_c','BS Music', array['Singer'], '', '{}', 'hi', 'https://res.cloudinary.com/jcuzzxsz/image/upload/v1/spot/profiles/00000000-0000-0000-0000-00000000000c/u1/me.jpg', '', '', '', false)$q$, 'photo couldn''t be saved');
select save_profile('student','Ana Cruz','ana_c','BS Music', array['Singer'], '', '{}', 'hi', 'https://res.cloudinary.com/jcuzzxsz/image/upload/v1712/spot/profiles/00000000-0000-0000-0000-00000000000a/u1/me.jpg', '', '', '', false);
select test.ok((select profile_image_url from profiles where id = auth.uid()) like '%/spot/profiles/%/u1/me.jpg', 'Edit profile photo saves');
select save_profile('student','Ana Cruz','ana_c','BS Music', array['Singer'], '', '{}', 'hi', 'https://res.cloudinary.com/jcuzzxsz/image/upload/v1712/spot/profile_images/abc.jpg', '', '', '', false);
select test.ok((select profile_image_url from profiles where id = auth.uid()) like '%/spot/profile_images/abc.jpg', 'profile setup photo saves');
select save_profile('student','Ana Cruz','ana_c','BS Music', array['Singer'], '', '{}', 'hi', '', '', '', '', false);
select test.ok((select profile_image_url from profiles where id = auth.uid()) = '', 'a photo can be removed');
select test.act('00000000-0000-0000-0000-00000000000b');
select save_profile('talent_seeker','Ben Reyes','ben','', '{}','Indie Studio', array['Singer'], '', 'https://lh3.googleusercontent.com/p.jpg', 'he/him', 'https://ben.test', '', false);
select test.ok((select profile_image_url from profiles where id = auth.uid()) = 'https://lh3.googleusercontent.com/p.jpg', 'an unchanged Google photo saves');
reset role;
insert into auth.users (id, email, raw_user_meta_data) values
 ('00000000-0000-0000-0000-00000000000d', 'd@x.test', '{"name":"Dee","avatar_url":"https://evil.test/d.jpg"}'),
 ('00000000-0000-0000-0000-00000000000e', 'e@x.test', '{"name":"Eli","picture":"https://lh5.googleusercontent.com/a/e.jpg"}');
select test.ok((select profile_image_url from profiles where id = '00000000-0000-0000-0000-00000000000d') = ''
  and (select profile_image_url from profiles where id = '00000000-0000-0000-0000-00000000000e') = 'https://lh5.googleusercontent.com/a/e.jpg',
  'sign-up keeps only Google photos');
delete from profiles where id = '00000000-0000-0000-0000-00000000000d';
set role authenticated;
select test.act('00000000-0000-0000-0000-00000000000d');
select ensure_profile();
select test.ok((select profile_image_url from profiles where id = auth.uid()) = '', 'a restored profile keeps only Google photos too');

-- Post reports: only posts you can see, never your own, "Something else" needs details, one per member per post.
select test.act('00000000-0000-0000-0000-00000000000b');
select test.fails($q$select report_post('p3', 'spam')$q$, 'no longer available');
select test.fails($q$select report_post('bp', 'spam')$q$, 'your own post');
select test.fails($q$select report_post('p1', 'boring')$q$, 'Choose why');
select test.fails($q$select report_post('p1', 'other', ' meh ')$q$, 'at least 5 characters');
select report_post('p1', 'spam');
select report_post('p1', 'stolen', '  This is my friend''s video.  ');
select test.fails($q$select count(*) from post_reports$q$, 'permission denied');
select test.fails($q$insert into post_reports (post_id, reporter_id, post_owner_id, reason) values ('p1', auth.uid(), auth.uid(), 'spam')$q$, 'permission denied');
reset role;
select test.ok((select count(*) from post_reports) = 1
  and (select reason || ':' || details from post_reports) = 'stolen:This is my friend''s video.', 'reporting again replaces the reason');
select test.ok((select reporter || ' > ' || post_owner || ': ' || post_caption from post_reports) = 'Ben Reyes (@ben) > Ana Cruz (@ana_c): Campus show',
  'a report names both sides and keeps the caption');
set role authenticated;

-- AI Assist requests are limited per member, and only finished profiles can use it.
select test.act('');
select test.fails($q$select claim_post_ai()$q$, 'Sign in to continue.');
select test.act('00000000-0000-0000-0000-00000000000d');
select test.fails($q$select claim_post_ai()$q$, 'Finish setting up your profile first.');
select test.act('00000000-0000-0000-0000-00000000000c');
select count(claim_post_ai()) from generate_series(1, 15);
select test.fails($q$select claim_post_ai()$q$, 'a lot this hour');
select test.fails($q$select count(*) from private.post_ai_requests$q$, 'permission denied');

-- Editing events: organizer only, before the event starts; changing the time or place tells the people in it.
select test.act('00000000-0000-0000-0000-00000000000a');
select test.fails($q$select update_event('e1','Gig Night','desc','','Singer', now() + interval '2 days','Hall','Ben', null, 'paid', '')$q$, 'Only the event''s organizer');
select test.act('00000000-0000-0000-0000-00000000000b');
-- An accepted invitation took e1 past its 1 slot (2 performers); edits that keep the saved slots still save.
select update_event('e1','Gig Night','desc fixed','','Singer', (select starts_at from events where id = 'e1'),'Hall','Ben', 1, 'paid', '1,500 per act');
select test.ok((select description || '|' || participant_limit || '|' || participant_count from events where id = 'e1') = 'desc fixed|1|2',
  'an event past its slots can still be edited');
select test.fails($q$select update_event('e1','Gig Night','desc','','Singer', now() - interval '1 hour','Hall','Ben', null, 'paid', '')$q$, 'future date');
select test.fails($q$select update_event('e1','Gig Night','desc','https://evil.test/p.jpg','Singer', now() + interval '2 days','Hall','Ben', null, 'paid', '')$q$, 'Image upload failed');
select update_event('e1', ' Gig Night Live ', 'New details', '', 'Singer', now() + interval '2 days', 'Main Hall', 'Ben', 5, 'paid', '1,500 per act');
select test.ok((select title || '|' || location || '|' || participant_limit from events where id = 'e1') = 'Gig Night Live|Main Hall|5',
  'the organizer edits the event');
select test.fails($q$select update_event('e1','Gig Night Live','New details','','Singer', (select starts_at from events where id = 'e1'),'Main Hall','Ben', 1, 'paid', '1,500 per act')$q$,
  'keep at least 2 slots');
select test.ok((select title from conversations where community_kind = 'event' and community_id = 'e1') = 'Gig Night Live', 'the event chat is renamed');
select update_event('e1', 'Gig Night Live', 'Only the description changed', '', 'Singer',
  (select starts_at from events where id = 'e1'), 'Main Hall', 'Ben', 5, 'paid', '1,500 per act');
select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok((select string_agg(message, ',') from notifications where type = 'event_updated') = 'changed the time and place of Gig Night Live',
  'performers hear about a new time and place, not other edits');

-- Deleting: only while nobody is involved. Otherwise the event is cancelled, which closes it but keeps it for its people.
select test.act('00000000-0000-0000-0000-00000000000b');
select test.fails($q$select delete_event('e1')$q$, 'cancel the event instead');
select delete_event('e2');
select test.ok(not exists (select 1 from events where id = 'e2'), 'an event nobody joined is deleted');
select create_event('e3', 'Showcase', 'desc', '', 'Singer', now() + interval '3 days', 'Hall', 'Ben', null, 'free');
select create_talent_request('invitation', 'e3', '00000000-0000-0000-0000-00000000000c', 'Please join our showcase');
select test.fails($q$select delete_event('e3')$q$, 'cancel the event instead');
select test.act('00000000-0000-0000-0000-00000000000a');
select apply_to_event('e3', 'Count me in');
select test.act('00000000-0000-0000-0000-00000000000b');
select cancel_event('e3');
select cancel_event('e3');
select test.ok((select cancelled_at from events where id = 'e3') is not null
  and (select status from talent_requests where source_id = 'e3') = 'withdrawn', 'cancelling withdraws pending invitations');
select test.ok(not exists (select 1 from event_applications where event_id = 'e3'), 'cancelling clears pending applications');
select test.ok(not exists (select 1 from search_events('show', '', 0)), 'cancelled events leave search');
select test.fails($q$select update_event('e3','Showcase','desc','','Singer', now() + interval '3 days','Hall','Ben', null, 'free', '')$q$, 'was cancelled');
select test.fails($q$select create_talent_request('invitation', 'e3', '00000000-0000-0000-0000-00000000000a', 'Please join our showcase')$q$, 'was cancelled');
select test.act('00000000-0000-0000-0000-00000000000c');
select test.ok((select message from notifications where type = 'event_cancelled') = 'cancelled Showcase', 'invitees hear about a cancellation');
select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok((select message from notifications where type = 'event_cancelled' and target_id = 'e3') = 'cancelled Showcase'
  and not exists (select 1 from event_applications where event_id = 'e3'), 'applicants hear about a cancellation and aren''t left pending');
select test.fails($q$select apply_to_event('e3')$q$, 'was cancelled');
select test.act('00000000-0000-0000-0000-00000000000b');
select cancel_event('e1');
select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok((select message from notifications where type = 'event_cancelled' and target_id = 'e1') = 'cancelled Gig Night Live'
  and exists (select 1 from events where id = 'e1'), 'performers hear about it and still see the event');
select test.ok(not exists (select 1 from profile_events(auth.uid())), 'cancelled events leave profiles');

-- Applying and then accepting an invitation to the same event settles the application, so nobody reviews it again.
select test.act('00000000-0000-0000-0000-00000000000b');
select create_event('e4', 'Jam', 'desc', '', 'Music', now() + interval '4 days', 'Hall', 'Ben', 1, 'free');
select test.act('00000000-0000-0000-0000-00000000000c');
select apply_to_event('e4', 'I can take photos');
select test.act('00000000-0000-0000-0000-00000000000b');
select create_talent_request('invitation', 'e4', '00000000-0000-0000-0000-00000000000c', 'Please take photos at the jam');
select test.act('00000000-0000-0000-0000-00000000000c');
select respond_talent_request('invitation_e4_00000000-0000-0000-0000-00000000000c', 'accepted');
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok((select status from event_applications where event_id = 'e4') = 'accepted'
  and (select participant_count from events where id = 'e4') = 1, 'accepting an invitation settles an application to the same event');
select test.fails($q$select review_event_application('e4', '00000000-0000-0000-0000-00000000000c', false)$q$, 'already been reviewed');
select test.act('00000000-0000-0000-0000-00000000000c');
select set_event_joined('e4', false);
select test.ok((select participant_count from events where id = 'e4') = 0 and not exists (select 1 from event_applications where event_id = 'e4'),
  'a performer who came by invitation can leave and apply again');

-- Owners manage their posts: edit, pin (up to 3), hide likes, turn comments off, delete.
select test.act('00000000-0000-0000-0000-00000000000a');
select publish_post('q1', 'Open mic tonight', '{Singer}', '{music}', '[]', 'public', 'Hall', '{}', false, false, false);
select test.act('00000000-0000-0000-0000-00000000000b');
select test.fails($q$select update_post('q1', 'mine now', '{}', '{}', '', 'public')$q$, 'only edit your own');
select test.fails($q$select set_post_options('q1', true)$q$, 'only change your own');
select test.fails($q$select delete_post('q1')$q$, 'only delete your own');
select add_comment('q1', 'k1', 'See you there');
select test.act('00000000-0000-0000-0000-00000000000a');
select update_post('q1', 'Open mic tonight', '{Singer}', '{music}', 'Hall', 'followers');
select test.ok((select edited_at from posts where id = 'q1') is null and (select audience from posts where id = 'q1') = 'followers',
  'changing only who can see a post isn''t an edit');
select test.ok((update_post('q1', ' Open mic at 7 ', '{Singer,Dancer}', '{#music, live ,music}', ' Main hall ', 'public')).caption
  = 'Open mic at 7', 'an edit returns the post');
select test.ok((select edited_at from posts where id = 'q1') is not null and (select tags from posts where id = 'q1') = '{music,live}'
  and (select location from posts where id = 'q1') = 'Main hall', 'an edit tidies hashtags and marks the post edited');
select test.fails($q$select update_post('q1', '  ', '{}', '{}', '', 'public')$q$, 'needs a caption');
select test.fails($q$select update_post('q1', 'x', '{}', '{}', '', 'friends')$q$, 'Choose who can see');
select set_post_options('q1', true);
select set_post_options('p1', true);
select set_post_options('p2', true);
select test.fails($q$select set_post_options('p3', true)$q$, 'pin up to 3');
select set_post_options('q1', true);
select set_post_options('p2', false);
select set_post_options('p3', true);
select test.ok((select count(*) from posts where user_id = auth.uid() and pinned_at is not null) = 3
  and (select pinned_at from posts where id = 'p2') is null, 'unpinning frees a pin');
select set_post_options('q1', null, true, true);
select test.ok((select likes_hidden and comments_off from posts where id = 'q1')
  and (select pinned_at from posts where id = 'q1') is not null, 'each option changes on its own');
select test.act('00000000-0000-0000-0000-00000000000b');
select test.fails($q$select add_comment('q1', 'k2', 'Another')$q$, 'Comments are turned off');
select edit_comment('k1', ' See you at 7 ');
select test.ok((select text from comments where id = 'k1') = 'See you at 7'
  and (select edited_at from comments where id = 'k1') is not null, 'the writer edits a comment');
select test.fails($q$select edit_comment('k1', '  ')$q$, '2,000 characters');
select set_post_interaction('q1', true);
insert into saved_posts (user_id, post_id) values (auth.uid(), 'q1');
select test.act('00000000-0000-0000-0000-00000000000a');
select test.fails($q$select edit_comment('k1', 'mine')$q$, 'only edit your own');
select delete_comment('q1', 'k1');
select test.ok((select deleted from comments where id = 'k1') and (select comments_count from posts where id = 'q1') = 0,
  'a post''s owner removes a comment on it');
select test.ok((select count(*) from notifications where post_id = 'q1') = 2, 'the post''s like and comment notifications');
select delete_post('q1');
select delete_post('q1');
select test.ok(not exists (select 1 from posts where id = 'q1') and not exists (select 1 from comments where post_id = 'q1')
  and not exists (select 1 from notifications where post_id = 'q1'), 'deleting a post takes its comments and notifications');
select test.act('00000000-0000-0000-0000-00000000000b');
select test.ok(not exists (select 1 from saved_posts where post_id = 'q1'), 'and other people''s saves');

-- Group owners edit and delete their groups and remove members; authors edit their group posts.
select test.act('00000000-0000-0000-0000-00000000000a');
select create_group('g9', 'Band', 'We jam', '', 'Musician', 'public');
select test.act('00000000-0000-0000-0000-00000000000b');
select set_group_joined('g9', true);
select open_community_chat('group', 'g9');
select create_group_post('gp9', 'g9', 'Jam on Sunday');
select update_group_post('gp9', ' Jam on Saturday ');
select test.ok((select text from group_posts where id = 'gp9') = 'Jam on Saturday'
  and (select edited_at from group_posts where id = 'gp9') is not null, 'an author edits a group post');
select test.fails($q$select update_group('g9', 'Mine', 'x', '', 'Musician', 'public')$q$, 'Only the group''s owner can edit');
select test.fails($q$select delete_group('g9')$q$, 'Only the group''s owner can delete');
select test.fails($q$select remove_group_member('g9', '00000000-0000-0000-0000-00000000000a')$q$, 'Only the group''s owner can remove');
select test.act('00000000-0000-0000-0000-00000000000a');
select test.fails($q$select update_group_post('gp9', 'edited by the owner')$q$, 'only edit your own');
select test.fails($q$select update_group('g9', 'B', 'x', '', 'Musician', 'public')$q$, '2–80 characters');
select test.fails($q$select update_group('g9', 'Band', 'x', 'https://evil.test/a.jpg', 'Musician', 'public')$q$, 'Image upload failed');
select update_group('g9', ' Sunday Band ', 'We jam weekly', '', 'Musician', 'private');
select test.ok((select name from groups where id = 'g9') = 'Sunday Band' and (select privacy from groups where id = 'g9') = 'private'
  and (select title from conversations where community_kind = 'group' and community_id = 'g9') = 'Sunday Band',
  'the owner edits the group, and its chat takes the new name');
select test.fails($q$select remove_group_member('g9', auth.uid())$q$, 'stays in the group');
select remove_group_member('g9', '00000000-0000-0000-0000-00000000000b');
select test.ok((select member_count from groups where id = 'g9') = 1
  and not exists (select 1 from group_members where group_id = 'g9' and user_id = '00000000-0000-0000-0000-00000000000b')
  and not exists (select 1 from conversations where community_kind = 'group' and community_id = 'g9'
    and '00000000-0000-0000-0000-00000000000b' = any (member_ids)), 'a removed member leaves the group and its chat');
select test.ok(exists (select 1 from notifications where target_id = 'g9'), 'the group''s join and post notifications');
select delete_group('g9');
select test.ok(not exists (select 1 from groups where id = 'g9') and not exists (select 1 from group_posts where id = 'gp9')
  and not exists (select 1 from conversations where community_kind = 'group' and community_id = 'g9')
  and not exists (select 1 from notifications where target_id = 'g9'), 'deleting a group takes its posts, chat and notifications');

-- Chats: pin up to 3 and mark one unread; reading clears the mark, and removing a chat from the inbox unpins it.
select test.act('00000000-0000-0000-0000-00000000000a');
select create_conversation('pc' || n, array['00000000-0000-0000-0000-00000000000b']::uuid[], 'Band ' || n, '', true)
from generate_series(1, 4) n;
select set_chat_preferences('pc1', '{"pinned": true}');
select set_chat_preferences('pc2', '{"pinned": true}');
select set_chat_preferences('pc3', '{"pinned": true}');
select test.fails($q$select set_chat_preferences('pc4', '{"pinned": true}')$q$, 'pin up to 3 chats');
select set_chat_preferences('pc1', '{"pinned": true, "unread": true}');
select test.ok((select marked_unread from chat_settings where conversation_id = 'pc1')
  and (select count(*) from chat_settings where pinned_at is not null) = 3, 'three pins and an unread mark');
select set_chat_preferences('pc1', '{"readAt": true}');
select test.ok(not (select marked_unread from chat_settings where conversation_id = 'pc1'), 'reading clears the unread mark');
select set_chat_preferences('pc2', '{"hiddenAt": true}');
select set_chat_preferences('pc3', '{"pinned": false}');
select test.ok((select count(*) from chat_settings where pinned_at is not null) = 1, 'removing and unpinning free the pins');
select set_chat_preferences('pc4', '{"pinned": true}');

-- Deleting an account takes what the member made, and undoes what they added to other people's things.
reset role;
delete from blocks;
insert into auth.users (id, email, raw_user_meta_data) values ('00000000-0000-0000-0000-00000000000f', 'f@x.test', '{"full_name":"Fay"}');
select test.ok(not has_function_privilege('anon', 'public.delete_account()', 'execute'), 'signed-out callers can''t delete accounts');
set role authenticated;
select test.act('');
select test.fails('select delete_account()', 'Sign in to continue.');
select test.act('00000000-0000-0000-0000-00000000000f');
select save_profile('student','Fay','fay','BS Music', array['Singer'], '', '{}', '', null, '', '', '', true);
select test.act('00000000-0000-0000-0000-00000000000a');
select publish_post('dp1','Rehearsal','{}','{}','[]','public','','{}',false,false,false);
select add_comment('dp1', 'dc0', 'Come watch');
select create_story('ds1', 'https://res.cloudinary.com/jcuzzxsz/image/upload/v1/spot/stories/00000000-0000-0000-0000-00000000000a/ds1/x.jpg', false, 'public', '{"scale":1}');
select create_group('dg1', 'Duo', 'We sing', '', 'Singer', 'public');
select create_group_post('dgp1', 'dg1', 'Practice tonight');
select create_conversation('dtrio', array['00000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-00000000000f']::uuid[], 'Trio', '', true);
select test.act('00000000-0000-0000-0000-00000000000b');
select respond_conversation('dtrio', true);
select create_event('de1', 'Duo Night', 'desc', '', 'Singer', now() + interval '4 days', 'Hall', 'Ben', null, 'free');
select test.act('00000000-0000-0000-0000-00000000000f');
select set_post_interaction('dp1', true, true);
select add_comment('dp1', 'dc1', 'Wow');
select set_comment_liked('dc0', true);
select set_story_liked('ds1', true);
select set_group_joined('dg1', true);
select set_group_post_liked('dgp1', true);
select apply_to_event('de1');
select respond_conversation('dtrio', true);
select send_message('dtrio', 'dm1', 'Hi both');
select create_conversation('ddirect', array['00000000-0000-0000-0000-00000000000a']::uuid[], '', '', false);
select create_conversation('dmine', array['00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b']::uuid[], 'Fay crew', '', true);
select create_conversation('dlonely', array['00000000-0000-0000-0000-00000000000b']::uuid[], 'Just us', '', true);
select publish_post('dp2','Mine','{}','{}','[]','public','','{}',false,false,false);
select create_group('dg2', 'Fay fans', 'Fans', '', 'Singer', 'public');
select test.act('00000000-0000-0000-0000-00000000000b');
select review_event_application('de1', '00000000-0000-0000-0000-00000000000f', true);
select set_group_joined('dg2', true);
select test.act('00000000-0000-0000-0000-00000000000a');
select respond_conversation('ddirect', true);
select respond_conversation('dmine', true);
select send_message('ddirect', 'dm2', 'Hey Fay');
select send_message('dtrio', 'dm3', 'Hi Fay');
select set_post_interaction('dp2', true, true);
select add_comment('dp2', 'dc2', 'Nice');
select test.act('00000000-0000-0000-0000-00000000000f');
select send_message('dtrio', 'dm4', 'Bye');
select send_message('dmine', 'dm5', 'Welcome');
select test.ok((select likes_count || ':' || comments_count from posts where id = 'dp1') = '1:2'
  and (select member_count from groups where id = 'dg1') = 2 and (select participant_count from events where id = 'de1') = 1
  and (select preview from conversations where id = 'dtrio') = 'Bye', 'before: Fay''s like, comment, joins and last message count');
select delete_account();
reset role;
select test.ok(not exists (select 1 from auth.users where id = '00000000-0000-0000-0000-00000000000f')
  and not exists (select 1 from profiles where id = '00000000-0000-0000-0000-00000000000f'), 'the account and its profile are gone');
select test.ok((select likes_count || ':' || comments_count from posts where id = 'dp1') = '0:1'
  and (select likes_count from comments where id = 'dc0') = 0 and (select likes_count from stories where id = 'ds1') = 0
  and (select likes_count from group_posts where id = 'dgp1') = 0, 'their likes and comments stop counting');
select test.ok((select member_count from groups where id = 'dg1') = 1 and (select participant_count from events where id = 'de1') = 0,
  'they leave groups and free their event slot');
select test.ok(not exists (select 1 from conversations where '00000000-0000-0000-0000-00000000000f' = any (member_ids || party_ids || invited_ids)
  or people ? '00000000-0000-0000-0000-00000000000f'), 'they leave every chat, community chats included');
select test.ok(not exists (select 1 from messages where sender_id = '00000000-0000-0000-0000-00000000000f'), 'their messages are gone');
select test.ok(not exists (select 1 from conversations where id in ('ddirect', 'dlonely')), 'direct chats and chats with no one left are deleted');
select test.ok((select preview || '|' || last_message_id || '|' || last_sender_id from conversations where id = 'dtrio')
  = 'Hi Fay|dm3|00000000-0000-0000-0000-00000000000a', 'the inbox shows the message before theirs');
select test.ok((select creator_id from conversations where id = 'dmine') = '00000000-0000-0000-0000-00000000000a'
  and (select preview || last_message_id || last_sender_id from conversations where id = 'dmine') = ''
  and (select invited_ids from conversations where id = 'dmine') = array['00000000-0000-0000-0000-00000000000b']::uuid[],
  'a group chat they started passes to a member, and invitations to it stand');
select test.ok(not exists (select 1 from posts where id = 'dp2') and not exists (select 1 from comments where id = 'dc2')
  and not exists (select 1 from groups where id = 'dg2') and not exists (select 1 from conversations where community_id = 'dg2'),
  'their posts and groups go, with other people''s comments and the group chat');
select test.ok(not exists (select 1 from notifications where '00000000-0000-0000-0000-00000000000f' in (sender_id, recipient_id)),
  'their notifications are gone');

-- Seen: a direct chat shows when the other person read it, unless either of them turned read receipts off.
set role authenticated;
select test.act('00000000-0000-0000-0000-00000000000a');
select create_conversation('rr1', array['00000000-0000-0000-0000-00000000000b']::uuid[], '', '', false);
select test.act('00000000-0000-0000-0000-00000000000b');
select respond_conversation('rr1', true);
select test.act('00000000-0000-0000-0000-00000000000a');
select send_message('rr1', 'rrm1', 'Read me');
select test.ok(chat_seen('rr1') is null, 'nothing is seen before the other person opens the chat');
select test.act('00000000-0000-0000-0000-00000000000b');
select set_chat_preferences('rr1', '{"readAt": true}');
select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok(chat_seen('rr1') is not null, 'the sender sees when it was read');
select test.ok(chat_seen('dtrio') is null, 'group chats don''t show Seen');
select test.act('00000000-0000-0000-0000-00000000000c');
select test.ok(chat_seen('rr1') is null, 'outsiders learn nothing');
select test.act('00000000-0000-0000-0000-00000000000b');
insert into user_settings (user_id, read_receipts) values (auth.uid(), false)
  on conflict (user_id) do update set read_receipts = false;
select test.act('00000000-0000-0000-0000-00000000000a');
select test.ok(chat_seen('rr1') is null, 'a reader with read receipts off isn''t shown');
select test.act('00000000-0000-0000-0000-00000000000b');
update user_settings set read_receipts = true where user_id = auth.uid();
select test.act('00000000-0000-0000-0000-00000000000a');
insert into user_settings (user_id, read_receipts) values (auth.uid(), false)
  on conflict (user_id) do update set read_receipts = false;
select test.ok(chat_seen('rr1') is null, 'turning read receipts off also hides others'' Seen');

-- Phone alerts follow Settings › Notify me about; members who never changed a switch get everything.
update user_settings set likes = false, community = false where user_id = auth.uid();
reset role;
select test.ok(not private.wants_push('00000000-0000-0000-0000-00000000000a', 'like')
  and private.wants_push('00000000-0000-0000-0000-00000000000a', 'comment')
  and not private.wants_push('00000000-0000-0000-0000-00000000000a', 'event_application_accepted')
  and not private.wants_push('00000000-0000-0000-0000-00000000000a', 'group_post')
  and private.wants_push('00000000-0000-0000-0000-00000000000a', 'invitation')
  and private.wants_push('00000000-0000-0000-0000-00000000000a', 'message'), 'switched-off kinds stay off the phone');
select test.ok(private.wants_push('00000000-0000-0000-0000-00000000000c', 'like')
  and private.wants_push(gen_random_uuid(), 'message'), 'without settings every alert is on');
select test.ok(not has_function_privilege('authenticated', 'private.wants_push(uuid, text)', 'execute'),
  'members can''t probe each other''s alert settings');

-- Search › Posts finds captions, hashtags, categories and authors, within what the searcher may see.
insert into auth.users (id, email, raw_user_meta_data) values ('00000000-0000-0000-0000-000000000010', 'g@x.test', '{"full_name":"Gio"}');
set role authenticated;
select test.act('00000000-0000-0000-0000-000000000010');
select save_profile('student','Gio','gio','BS Music', array['Singer'], '', '{}', '', null, '', '', '', true);
select test.act('00000000-0000-0000-0000-00000000000a');
select publish_post('sp1','Acoustic set at the quad','{Singer}','{guitar}','[]','public','','{}',false,false,false);
select publish_post('sp2','Only followers see this acoustic one','{}','{}','[]','followers','','{}',false,false,false);
select publish_post('sp3','My acoustic diary','{}','{}','[]','private','','{}',false,false,false);
select test.ok((select count(*) from search_posts('acoustic') where id like 'sp%') = 3, 'authors find all their own posts');
select test.act('00000000-0000-0000-0000-000000000010');
select test.ok((select array_agg(id) from search_posts('ACOUSTIC') where id like 'sp%') = array['sp1'],
  'others find only what they may see, in any letter case');
select test.ok(exists (select 1 from search_posts('#guit') where id = 'sp1') and exists (select 1 from search_posts('singer') where id = 'sp1')
  and exists (select 1 from search_posts('ana cr') where id = 'sp1') and exists (select 1 from search_posts('@ana_') where id = 'sp1'),
  'hashtags, categories, names and usernames match');
select test.ok(not exists (select 1 from search_posts('a')) and not exists (select 1 from search_posts('%%')),
  'one letter finds nothing, and % is only a character');

-- SPOT AI's talent checks have their own allowance, so they never use up AI Assist's.
select claim_talent_ai() from generate_series(1, 30);
select test.fails('select claim_talent_ai()', 'checked a lot of media for you this hour');
select claim_post_ai();
reset role;
select test.ok((select count(*) from private.post_ai_requests where user_id = '00000000-0000-0000-0000-000000000010' and kind = 'talent') = 30
  and (select count(*) from private.post_ai_requests where user_id = '00000000-0000-0000-0000-000000000010' and kind = 'assist') = 1,
  'talent checks and AI Assist are counted apart');

select 'ALL BEHAVIOR TESTS PASSED';
rollback;
