package com.example.spotapp.data

import org.junit.Assert.*
import org.junit.Test

class SpotMessagesTest {
    @Test fun draftsRejectEmptyAndOversizedMessagesButAcceptMediaOnly() {
        assertNotNull(ChatDraft(text = "  ").error())
        assertNotNull(ChatDraft(text = "a".repeat(4001)).error())
        assertNull(ChatDraft(text = "a".repeat(4000)).error())
        assertNull(ChatDraft(attachment = ChatAttachment("path", "image/jpeg", "photo", 120)).error())
        assertNotNull(ChatDraft(attachment = ChatAttachment("path", "text/html", "page", 120)).error())
        assertNotNull(ChatDraft(attachment = ChatAttachment("path", "video/mp4", "video", 26L * 1024 * 1024)).error())
    }
    @Test fun storyRepliesShowTheReplyWithoutTheLink() {
        val reply = "spot://story/story-1_a\nLove this 🔥"
        assertEquals("story-1_a", StoryReplyLink.storyId(reply))
        assertEquals("Love this 🔥", StoryReplyLink.note(reply))
        assertEquals("Replied to a story: Love this 🔥", messagePreview(reply))
        // Only a link at the start is a story reply; a pasted link in normal text isn't.
        assertNull(StoryReplyLink.storyId("look spot://story/story-1_a"))
        assertEquals("Shared a post", messagePreview(SharedPostLink.message("post-1")))
        assertEquals("hi", messagePreview("hi"))
    }
    @Test fun sharedPostsTravelAsALinkWithAnOptionalNote() {
        val bare = SharedPostLink.message("post-abc_1")
        assertEquals("post-abc_1", SharedPostLink.postId(bare))
        assertEquals("", SharedPostLink.note(bare))
        assertEquals("Shared a post", SharedPostLink.preview(bare))
        val noted = SharedPostLink.message("post-abc_1", "  You have to see this  ")
        assertEquals("post-abc_1", SharedPostLink.postId(noted))
        assertEquals("You have to see this", SharedPostLink.note(noted))
        assertEquals("You have to see this", SharedPostLink.preview(noted))
        // The server keeps 160 characters for the preview; the link comes first, so it survives.
        assertEquals("post-abc_1", SharedPostLink.postId(SharedPostLink.message("post-abc_1", "x".repeat(400)).take(160)))
        assertNull(SharedPostLink.postId("Plain message"))
        assertEquals("Plain message", SharedPostLink.preview("Plain message"))
    }
    @Test fun eventInvitationsShowTheDetailsWithoutTheLink() {
        val invite = "spot://event/event-1_a\nPlease perform at our gig"
        assertEquals("event-1_a", EventInviteLink.eventId(invite))
        assertEquals("Please perform at our gig", EventInviteLink.note(invite))
        assertEquals("Event invitation: Please perform at our gig", messagePreview(invite))
        // The server's 160-character inbox preview keeps the link whole, because it comes first.
        assertEquals("event-1_a", EventInviteLink.eventId(("spot://event/event-1_a\n" + "x".repeat(400)).take(160)))
        assertNull(EventInviteLink.eventId("see spot://event/event-1_a"))
    }
    @Test fun communityChatsAreOpenToWhoeverIsThere() {
        val alone = chat.copy(kind = "group", memberIds = listOf("me"), partyIds = listOf("me"))
        assertFalse(alone.canMessage)
        assertTrue(alone.copy(communityKind = "event", communityId = "e1").canMessage)
        assertTrue(chat.canMessage)
    }
    @Test fun notificationsOpenWhatTheyAreAbout() {
        fun opens(type: String, post: String = "", target: String = "") =
            SocialNotification("n", type, "sender", "Sam", "", post, "", 0, false, target).opens
        assertEquals(NotificationTarget.POST, opens("like", post = "p1"))
        assertEquals(NotificationTarget.PROFILE, opens("follow"))
        assertEquals(NotificationTarget.EVENT, opens("event_join", target = "e1"))
        assertEquals(NotificationTarget.GROUP, opens("group_request", target = "g1"))
        assertEquals(NotificationTarget.GROUP, opens("group_post", target = "g1"))
        assertEquals(NotificationTarget.INVITATIONS, opens("invitation_accepted", target = "invitation_e1_u"))
        assertEquals(NotificationTarget.OPPORTUNITY, opens("application", target = "o1"))
        assertEquals(NotificationTarget.OPPORTUNITY, opens("application_declined", target = "o1"))
    }
    private val chat = CampusConversation("chat", "", creatorId = "me", partyIds = listOf("me", "friend"), memberIds = listOf("me", "friend"),
        people = mapOf("friend" to ChatPerson("friend", "Sam")), preview = "Hello", lastSenderId = "friend", updatedAt = 200)
    @Test fun nicknamesArePersonalAndIdentityComesFromUid() {
        assertEquals("Sam", chat.nameFor("me"))
        assertEquals("Study partner", chat.nameFor("me", ChatPreferences(nickname = "Study partner")))
        assertEquals("friend", chat.otherUid("me"))
    }
    @Test fun readAndHiddenConversationsReappearOnlyForNewMessages() {
        assertTrue(chat.unread("me", ChatPreferences(readAt = 100)))
        assertFalse(chat.unread("me", ChatPreferences(readAt = 200)))
        assertFalse(chat.copy(lastSenderId = "me").unread("me", ChatPreferences()))
        assertFalse(chat.visible(ChatPreferences(hiddenAt = 200)))
        assertTrue(chat.copy(updatedAt = 201).visible(ChatPreferences(hiddenAt = 200)))
    }
    @Test fun sharedEventsAndProfilesAreCardsNotInvitations() {
        val event = SharedEventLink.message("event-1_a", "  Come watch!  ")
        assertEquals("spot://shared-event/event-1_a\nCome watch!", event)
        assertEquals("event-1_a", SharedEventLink.eventId(event))
        assertNull(EventInviteLink.eventId(event))
        assertEquals("Come watch!", SharedEventLink.note(event))
        assertEquals("Come watch!", messagePreview(event))
        assertEquals("Shared an event", messagePreview(SharedEventLink.message("event-1_a")))

        val uid = "0b9f1c2e-1111-4a2b-9c3d-000000000abc"
        val profile = SharedProfileLink.message(uid)
        assertEquals(uid, SharedProfileLink.uid(profile))
        assertEquals("Shared a profile", messagePreview(profile))
        assertEquals("Follow her", messagePreview(SharedProfileLink.message(uid, "Follow her")))
        assertNull(SharedProfileLink.uid("look at spot://shared-profile/$uid"))
    }
}
