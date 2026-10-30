package com.example.spotapp.data

import org.junit.Assert.*
import org.junit.Test

class FeedbackEmailTest {
    @Test fun reportsCarryTheSenderVersionAndPhone() {
        val body = FeedbackEmail.body("  The event page freezes after I apply.  ", "Ana Cruz (@ana_c)", "2.5 (4)", "Realme RMX2180 · Android 11")
        assertEquals("The event page freezes after I apply.\n\n—\nFrom: Ana Cruz (@ana_c)\nSPOT 2.5 (4) · Realme RMX2180 · Android 11", body)
        assertEquals("SPOT bug report · 2.5 (4)", FeedbackEmail.subject(bug = true, version = "2.5 (4)"))
        assertEquals("SPOT feedback · 2.5 (4)", FeedbackEmail.subject(bug = false, version = "2.5 (4)"))
    }
    @Test fun messagesNeedEnoughDetailButStayShort() {
        assertNotNull(FeedbackEmail.error("   broken   "))
        assertNull(FeedbackEmail.error("The chat list is empty."))
        assertNotNull(FeedbackEmail.error("x".repeat(2001)))
    }
}
