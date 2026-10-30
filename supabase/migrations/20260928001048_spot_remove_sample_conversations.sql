-- Sample chats were retired from the app: remove any that exist and the function that created them.
delete from public.hidden_messages where conversation_id in (select id from public.conversations where demo);
delete from public.conversation_reports where conversation_id in (select id from public.conversations where demo);
delete from public.chat_settings where conversation_id in (select id from public.conversations where demo);
delete from public.messages where conversation_id in (select id from public.conversations where demo);
delete from public.conversations where demo;
drop function if exists public.add_sample_conversations();
