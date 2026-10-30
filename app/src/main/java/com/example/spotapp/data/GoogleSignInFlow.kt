package com.example.spotapp.data

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import kotlinx.coroutines.CancellationException
import java.io.IOException

sealed interface GoogleSignInResult {
    data object SignedIn : GoogleSignInResult
    data object NotCompleted : GoogleSignInResult
    data class Failed(val message: String) : GoogleSignInResult
}

// Authentication finishes here; AuthRepository owns profile loading and creation.
internal suspend fun runGoogleSignIn(
    requestIdToken: suspend () -> String,
    authenticate: suspend (String) -> Unit,
    onFailure: (String, Exception) -> Unit = { _, _ -> }
): GoogleSignInResult {
    var stage = "Google credential"
    return try {
        val token = requestIdToken()
        check(token.isNotBlank()) { "Google returned an empty ID token." }
        stage = "Supabase authentication"
        authenticate(token)
        GoogleSignInResult.SignedIn
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onFailure(stage, error)
        googleSignInFailure(error)
    }
}

internal fun googleSignInFailure(error: Exception): GoogleSignInResult = when {
    // Some Play services versions wrap reauthentication failures as cancellations.
    error.message?.contains("reauth", ignoreCase = true) == true ->
        GoogleSignInResult.Failed("Google couldn't verify that account. Check its sign-in in your phone's Google settings, then retry.")
    error is GetCredentialCancellationException -> GoogleSignInResult.NotCompleted
    error is NoCredentialException ->
        GoogleSignInResult.Failed("No usable Google account was found. Check your phone's Google account, then retry.")
    error is GetCredentialInterruptedException ->
        GoogleSignInResult.Failed("Google sign-in was interrupted. Tap Continue with Google to try again.")
    error is GetCredentialProviderConfigurationException || error is GetCredentialUnsupportedException ->
        GoogleSignInResult.Failed("Google sign-in isn't available on this device. Check Google Play services or use email to sign in.")
    error is IOException ->
        GoogleSignInResult.Failed("Check your internet connection and try Google sign-in again.")
    error is AuthRestException && error.errorCode in setOf(AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit) ->
        GoogleSignInResult.Failed("Too many sign-in attempts. Wait a moment and try again.")
    error is AuthRestException && error.errorCode in setOf(AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists, AuthErrorCode.IdentityAlreadyExists) ->
        GoogleSignInResult.Failed("This email already has a SPOT account. Sign in with your email and password.")
    error is AuthRestException && error.errorCode in setOf(AuthErrorCode.ProviderDisabled, AuthErrorCode.SignupDisabled) ->
        GoogleSignInResult.Failed("Google sign-in is unavailable for SPOT. You can sign in with email for now.")
    else -> GoogleSignInResult.Failed("Google sign-in couldn't finish. Please try again or sign in with email.")
}
