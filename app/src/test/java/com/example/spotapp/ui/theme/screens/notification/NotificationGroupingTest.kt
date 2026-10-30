package com.example.spotapp.ui.theme.screens.notification

import com.example.spotapp.data.SocialNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGroupingTest {
    private fun note(id: String, type: String, sender: String, post: String = "", time: Long, read: Boolean = true, target: String = "") =
        SocialNotification(id, type, sender, sender.replaceFirstChar { it.uppercase() }, "", post, "message", time, read, target)

    @Test fun likesAndCommentsOnOnePostShareARowNewestFirst() {
        val groups = groupNotifications(listOf(
            note("1", "like", "ana", "p1", time = 10),
            note("2", "like", "ben", "p1", time = 30, read = false),
            note("3", "comment", "cara", "p1", time = 20),
            note("4", "like", "ana", "p2", time = 5),
            note("5", "follow", "dan", time = 40),
            note("6", "follow", "eve", time = 1)))
        assertEquals(listOf("5", "2", "3", "4", "6"), groups.map { it.first.id })
        val likes = groups[1]
        assertEquals(listOf("2", "1"), likes.items.map { it.id })
        assertTrue("one unread like makes the row unread", likes.unread)
        assertFalse(groups[2].unread)
    }

    @Test fun peopleAreNamedOnceEachAndCountedBeyondTwo() {
        fun people(vararg senders: String) = senders.mapIndexed { i, s -> note("$i", "like", s, "p", time = 100L - i) }
        assertEquals("Ana", groupNames(people("ana")))
        assertEquals("Ana and Ben", groupNames(people("ana", "ben")))
        assertEquals("Ana, Ben and 1 other", groupNames(people("ana", "ben", "cara")))
        assertEquals("Ana, Ben and 2 others", groupNames(people("ana", "ben", "cara", "dan")))
        val twice = NotificationGroup(people("ana", "ben", "ana"))
        assertEquals("Ana and Ben", groupNames(twice.people))
    }

    @Test fun everyTypeLandsUnderOneFilter() {
        assertEquals(NotificationKind.LIKES, note("1", "like", "a", time = 1).kind())
        assertEquals(NotificationKind.COMMENTS, note("1", "comment", "a", time = 1).kind())
        assertEquals(NotificationKind.FOLLOWS, note("1", "follow", "a", time = 1).kind())
        assertEquals(NotificationKind.EVENTS, note("1", "event_application_accepted", "a", time = 1).kind())
        assertEquals(NotificationKind.GROUPS, note("1", "group_request", "a", time = 1).kind())
        assertEquals(NotificationKind.WORK, note("1", "application_declined", "a", time = 1).kind())
    }
}
