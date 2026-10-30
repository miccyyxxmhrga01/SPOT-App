package com.example.spotapp.data

import org.junit.Assert.*
import org.junit.Test

class EventApplicationTest {
    private val now = 1_800_000_000_000L
    private fun application(status: String, startsAt: Long) =
        EventApplication("e1", "u1", "Ana", "", "", status, now - 60_000, "Gig Night", startsAt, "o1")

    @Test fun pendingApplicationsWaitUntilTheEventStarts() {
        assertTrue(application("pending", now + 60_000).awaitingReview(now))
        assertFalse(application("pending", now).awaitingReview(now))
        assertFalse(application("pending", now - 60_000).awaitingReview(now))
    }
    @Test fun answeredApplicationsAreNotWaiting() {
        assertFalse(application("accepted", now + 60_000).awaitingReview(now))
        assertFalse(application("declined", now + 60_000).awaitingReview(now))
    }
}
