package com.example.spotapp.data

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class GoogleSignInFlowTest {
    @Test fun credentialFailureNeverStartsSupabaseAuthentication() = runBlocking {
        var authenticationCalls = 0
        var failureStage = ""
        val result = runGoogleSignIn(
            requestIdToken = { throw GetCredentialUnknownException("[16] Account reauth failed.") },
            authenticate = { authenticationCalls++ },
            onFailure = { stage, _ -> failureStage = stage }
        )
        assertEquals(0, authenticationCalls)
        assertEquals("Google credential", failureStage)
        assertTrue((result as GoogleSignInResult.Failed).message.contains("Google settings"))
        assertFalse(result.message.contains("[16]"))
    }

    @Test fun ordinaryDismissalIsNotAnAuthenticationError() = runBlocking {
        val result = runGoogleSignIn(
            requestIdToken = { throw GetCredentialCancellationException("Activity is cancelled by the user") },
            authenticate = { fail("Dismissed requests must not authenticate") }
        )
        assertEquals(GoogleSignInResult.NotCompleted, result)
    }

    @Test fun reauthWrappedAsCancellationStillProvidesRecoveryGuidance() {
        val result = googleSignInFailure(GetCredentialCancellationException("[16] Account reauth failed."))
        assertTrue(result is GoogleSignInResult.Failed)
    }

    @Test fun coroutineCancellationPropagatesWithoutReportingFailure() = runBlocking {
        var reported = false
        val cancellation = CancellationException("Screen closed")
        try {
            runGoogleSignIn({ throw cancellation }, { fail("Unexpected authentication") }, { _, _ -> reported = true })
            fail("Cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertFalse(reported)
    }

    @Test fun signedInIsReturnedOnlyAfterSupabaseAcceptsTheCredential() = runBlocking {
        val events = mutableListOf<String>()
        val result = runGoogleSignIn(
            { events.add("credential"); "test-token" },
            { token -> assertEquals("test-token", token); events.add("supabase") }
        )
        assertEquals(listOf("credential", "supabase"), events)
        assertEquals(GoogleSignInResult.SignedIn, result)
    }

    @Test fun supabaseFailureNeverReportsSuccess() = runBlocking {
        var failureStage = ""
        val result = runGoogleSignIn(
            { "test-token" }, { throw IOException("Offline") },
            { stage, _ -> failureStage = stage }
        )
        assertEquals("Supabase authentication", failureStage)
        assertTrue(result is GoogleSignInResult.Failed)
    }

    @Test fun emptyCredentialsNeverReachSupabase() = runBlocking {
        val result = runGoogleSignIn({ "" }, { fail("Empty token must not authenticate") })
        assertTrue(result is GoogleSignInResult.Failed)
    }
}
