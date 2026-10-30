package com.example.spotapp.data

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RedactedLogTest {
    @Test fun requestUrlQueriesAreDroppedButTheReasonStays() {
        val error = IOException("HTTP request to https://x.supabase.co/rest/v1/posts?user_id=eq.1234&select=* (GET) failed with message: timeout")
        val logged = redacted(error)
        assertEquals("IOException: HTTP request to https://x.supabase.co/rest/v1/posts (GET) failed with message: timeout", logged.message)
        assertFalse(logged.message!!.contains("eq.1234"))
    }

    @Test fun causesAreRedactedAndStackTracesKept() {
        val cause = IOException("GET https://x.supabase.co/auth/v1/user?token=secret-value")
        val error = IllegalStateException("Could not sign in.", cause)
        val logged = redacted(error)
        assertEquals("IllegalStateException: Could not sign in.", logged.message)
        assertFalse(logged.cause!!.message!!.contains("secret-value"))
        assertArrayEquals(error.stackTrace, logged.stackTrace)
        assertArrayEquals(cause.stackTrace, logged.cause!!.stackTrace)
    }

    @Test fun selfReferencingCausesStop() {
        val error = object : Exception("loop") { override val cause: Throwable get() = this }
        assertNull(redacted(error).cause)
    }
}
