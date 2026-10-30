package com.example.spotapp.data

import android.content.Context
import android.util.Patterns
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

object AuthRepository {

    private const val TAG = "SpotAuth"

    const val MIN_PASSWORD_LENGTH = 8

    private fun isSymbol(c: Char) = !c.isLetterOrDigit() && !c.isWhitespace()

    /** Each new-password rule beside whether [password] meets it, for the live checklist under new-password fields. */
    fun passwordChecks(password: String): List<Pair<String, Boolean>> = listOf(
        "$MIN_PASSWORD_LENGTH+ characters" to (password.length >= MIN_PASSWORD_LENGTH),
        "A letter" to password.any(Char::isLetter),
        "A number" to password.any(Char::isDigit),
        "A symbol" to password.any(::isSymbol)
    )

    /**
     * Why [password] is too weak to use, or null when it is fine. New passwords need a letter, a number
     * and a symbol; signing in never checks this, so older passwords keep working.
     */
    fun passwordError(password: String): String? {
        if (password.length < MIN_PASSWORD_LENGTH) return "Use at least $MIN_PASSWORD_LENGTH characters for your password."
        val missing = listOfNotNull(
            "a letter".takeIf { password.none(Char::isLetter) },
            "a number".takeIf { password.none(Char::isDigit) },
            "a symbol (like ! or #)".takeIf { password.none(::isSymbol) }
        )
        if (missing.isEmpty()) return null
        val list = if (missing.size == 1) missing[0] else missing.dropLast(1).joinToString(", ") + " and " + missing.last()
        return "Add $list to your password."
    }

    private val auth get() = Supabase.auth

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Supabase starts no session until the email is confirmed. The new account's email stays in memory,
    // so the code from the email can be checked against it (verifySignupCode).
    private var pendingSignup: String? = null

    // Profile pictures are uploaded to Cloudinary first; the database only stores the URL.


    // =========================================================
    // LOGIN
    // =========================================================

    fun logIn(
        email: String,
        password: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val cleanEmail = email.trim()

        if (!validEmail(cleanEmail)) {
            callback(false, "Enter a valid email address.")
            return
        }

        if (password.isEmpty()) {
            callback(false, "Enter your password.")
            return
        }

        run(callback, "Could not sign in.") {
            try {
                signIn(cleanEmail, password)
            } catch (error: AuthRestException) {
                if (error.errorCode != AuthErrorCode.EmailNotConfirmed) throw error
                // The first code may have expired: send a fresh one. Login then opens the code screen
                // (currentUserNeedsEmailVerification), and the code signs the account in.
                pendingSignup = cleanEmail
                runCatching { auth.resendEmail(OtpType.Email.SIGNUP, cleanEmail) }
                throw IllegalStateException("Verify your email first. We sent a code to $cleanEmail.")
            }
        }
    }


    // =========================================================
    // PASSWORD RESET
    // =========================================================

    // Supabase has no hosted reset page, so the reset email carries a 6-digit code
    // (see the "Reset Password" email template) that completePasswordReset() redeems.
    fun sendPasswordResetEmail(
        email: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val cleanEmail = email.trim()

        if (!validEmail(cleanEmail)) {
            callback(false, "Enter a valid email address first.")
            return
        }

        run(callback, "Could not send the reset code.", success = "If this email has an account, a reset code will arrive shortly.") {
            auth.resetPasswordForEmail(cleanEmail)
        }
    }

    fun completePasswordReset(
        email: String,
        code: String,
        newPassword: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val cleanCode = code.trim()

        when {
            !Regex("[0-9]{6,10}").matches(cleanCode) -> {
                callback(false, "Enter the code from the reset email.")
                return
            }
            passwordError(newPassword) != null -> {
                callback(false, passwordError(newPassword))
                return
            }
        }

        run(callback, "Could not reset your password.") {
            try {
                auth.verifyEmailOtp(OtpType.Email.RECOVERY, email.trim(), cleanCode)
            } catch (error: AuthRestException) {
                if (error.errorCode == AuthErrorCode.OtpExpired) throw IllegalStateException("That code is incorrect or has expired. Request a new one.")
                throw error
            }
            auth.updateUser { password = newPassword; data = buildJsonObject { put(HAS_PASSWORD, true) } }
        }
    }

    /**
     * Sets a new password for the signed-in member once [currentPassword] checks out.
     * Pass null for accounts with no password yet (Google sign-up), see [currentUserHasPassword].
     */
    fun changePassword(
        currentPassword: String?,
        newPassword: String,
        callback: (Boolean, String?) -> Unit
    ) {
        if (currentPassword != null && currentPassword.isEmpty()) { callback(false, "Enter your current password."); return }
        passwordError(newPassword)?.let { callback(false, it); return }
        if (newPassword == currentPassword) { callback(false, "Choose a password different from your current one."); return }
        val email = runCatching { auth.currentUserOrNull()?.email }.getOrNull()
        if (auth.currentSessionOrNull() == null || (currentPassword != null && email.isNullOrBlank())) {
            callback(false, "Sign in again to change your password.")
            return
        }
        run(callback, "Could not change your password.", success = "Your password was changed.") {
            if (currentPassword != null) {
                // Signing in again proves the current password; it is the same account, so nothing reroutes.
                try {
                    signIn(email.orEmpty(), currentPassword)
                } catch (error: AuthRestException) {
                    if (error.errorCode == AuthErrorCode.InvalidCredentials) throw IllegalStateException("Your current password is incorrect.")
                    throw error
                }
            }
            try {
                // Supabase keeps listing a Google account as Google-only once it has a password,
                // so this flag makes the next change ask for the current one.
                auth.updateUser { password = newPassword; data = buildJsonObject { put(HAS_PASSWORD, true) } }
            } catch (error: AuthRestException) {
                if (error.errorCode == AuthErrorCode.SamePassword) throw IllegalStateException("Choose a password different from your current one.")
                throw error
            }
        }
    }

    private const val HAS_PASSWORD = "has_password"

    /** False for accounts that only sign in with Google and never set a password: there is no current one to confirm. */
    fun currentUserHasPassword(): Boolean = metadata(HAS_PASSWORD) == "true" || runCatching {
        val providers = auth.currentUserOrNull()?.appMetadata?.get("providers") as? JsonArray
        providers?.any { (it as? JsonPrimitive)?.content == "email" }
    }.getOrNull() ?: true


    // =========================================================
    // CREATE ACCOUNT + EMAIL VERIFICATION
    // =========================================================

    // Supabase emails the verification code as part of signup.
    fun createAccountAndSendVerification(
        fullName: String,
        email: String,
        password: String,
        callback: (Boolean, String?) -> Unit
    ) = createAccountOnly(fullName, email, password, callback)


    // =========================================================
    // CREATE ACCOUNT
    // =========================================================

    fun createAccountOnly(
        fullName: String,
        email: String,
        password: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val cleanName = fullName.trim()
        val cleanEmail = email.trim()

        when {
            !validEmail(cleanEmail) -> {
                callback(false, "Enter a valid email address.")
                return
            }

            passwordError(password) != null -> {
                callback(false, passwordError(password))
                return
            }
        }

        run(callback, "Could not create your account.") {
            val user = try {
                auth.signUpWith(Email) {
                    this.email = cleanEmail
                    this.password = password
                    if (cleanName.isNotBlank()) data = buildJsonObject { put("full_name", cleanName) }
                }
            } catch (error: AuthRestException) {
                if (error.errorCode != AuthErrorCode.UserAlreadyExists && error.errorCode != AuthErrorCode.EmailExists) throw error
                null
            }

            when {
                // Email confirmation is off for this project: signup already signed in.
                auth.currentSessionOrNull() != null -> pendingSignup = null

                // A new account waits for the code from its email.
                user != null && !user.identities.isNullOrEmpty() -> pendingSignup = cleanEmail

                // The email already has an account (possibly from an interrupted signup).
                else -> try {
                    signIn(cleanEmail, password)
                } catch (error: AuthRestException) {
                    if (error.errorCode == AuthErrorCode.EmailNotConfirmed) {
                        pendingSignup = cleanEmail
                        runCatching { auth.resendEmail(OtpType.Email.SIGNUP, cleanEmail) }
                    } else {
                        throw IllegalStateException("This email already has an account. Sign in or reset your password.")
                    }
                }
            }
        }
    }


    // =========================================================
    // RESEND EMAIL VERIFICATION
    // =========================================================

    fun resendVerificationEmail(
        callback: (Boolean, String?) -> Unit
    ) {
        if (auth.currentSessionOrNull() != null) {
            callback(true, null)
            return
        }

        val pending = pendingSignup

        if (pending == null) {
            callback(false, "Sign in before verifying your email.")
            return
        }

        run(callback, "Could not send the code.") {
            auth.resendEmail(OtpType.Email.SIGNUP, pending)
        }
    }


    // =========================================================
    // VERIFY EMAIL WITH THE CODE
    // =========================================================

    // The "Confirm signup" email carries a code ({{ .Token }} in its Supabase template) instead of a link.
    // Redeeming it confirms the email and signs the new account in.
    fun verifySignupCode(
        code: String,
        callback: (Boolean, String?) -> Unit
    ) {
        if (auth.currentSessionOrNull() != null) {
            callback(true, null)
            return
        }

        val pending = pendingSignup
        val cleanCode = code.trim()

        if (pending == null) {
            callback(false, "Sign in again to verify your email.")
            return
        }
        if (!Regex("[0-9]{6,10}").matches(cleanCode)) {
            callback(false, "Enter the code from the email.")
            return
        }

        run(callback, "Could not verify your email. Check your connection.") {
            try {
                auth.verifyEmailOtp(OtpType.Email.SIGNUP, pending, cleanCode)
            } catch (error: AuthRestException) {
                if (error.errorCode == AuthErrorCode.OtpExpired) throw IllegalStateException("That code is incorrect or has expired. Tap Resend code for a new one.")
                throw error
            }
            pendingSignup = null
        }
    }


    // =========================================================
    // COMPLETE PROFILE
    // =========================================================
    //
    // profileImageUrl is a Cloudinary URL, uploaded before this
    // function is called.
    // =========================================================

    fun completeProfile(
        profile: ProfileDetails,
        profileImageUrl: String?,
        callback: (Boolean, String?) -> Unit
    ) {
        val uid = currentUid()
        if (uid == null) { callback(false, "Sign in and verify your email first."); return }
        profile.validationError()?.let { callback(false, it); return }
        scope.launch {
            try {
                withTimeout(30_000) { ProfileRepository.shared.save(profile, profileImageUrl, "", "", "", completing = true) }
                if (sameUser(uid)) callback(true, null) else callback(false, "Your account changed. Sign in again.")
            } catch (_: TimeoutCancellationException) { callback(false, "Profile save timed out. Retry.") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { callback(false, if (e is IllegalArgumentException || e is IllegalStateException) e.message else errorText(e, "Couldn't save your profile. Retry.")) }
        }
    }


    // =========================================================
    // DETERMINE WHERE USER SHOULD GO
    // =========================================================

    fun checkUserDestination(
        callback: (String, String?) -> Unit
    ) {
        val uid = currentUid()

        if (uid == null) {
            callback("LOGIN", null)
            return
        }

        scope.launch {
            try {
                // Creates the profile row if the signup trigger has not (older accounts).
                val complete = rpc("ensure_profile").decodeAs<Boolean>()

                when {
                    !sameUser(uid) -> callback("ERROR", "The signed-in account changed. Please try again.")
                    complete -> callback("HOME", null)
                    else -> callback("PROFILE_SETUP", null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                callback("ERROR", errorText(error, "Could not load your profile. Please retry."))
            }
        }
    }


    // =========================================================
    // CURRENT USER HELPERS
    // =========================================================

    fun currentUid(): String? = Supabase.uid()

    fun currentUserEmail(): String =
        runCatching { auth.currentUserOrNull()?.email }.getOrNull()
            ?: pendingSignup.orEmpty()

    fun currentUserDisplayName(): String = metadata("full_name").ifBlank { metadata("name") }

    fun currentUserPhotoUrl(): String = metadata("avatar_url").ifBlank { metadata("picture") }

    fun currentUserNeedsEmailVerification(): Boolean =
        pendingSignup != null && runCatching { auth.currentSessionOrNull() == null }.getOrDefault(true)

    fun isUserLoggedIn(): Boolean = currentUid() != null


    // =========================================================
    // DELETE ACCOUNT
    // =========================================================

    // Shown once on Login after this phone deleted its account.
    private var accountNotice: String? = null

    fun takeAccountNotice(): String? = accountNotice.also { accountNotice = null }

    /**
     * Deletes the signed-in account and everything in it (see supabase delete_account), then signs this phone out.
     * Accounts with a password confirm it first; Google-only accounts pass null, see [currentUserHasPassword].
     */
    fun deleteAccount(
        context: Context,
        password: String?,
        callback: (Boolean, String?) -> Unit
    ) {
        if (password != null && password.isEmpty()) { callback(false, "Enter your password."); return }
        val uid = currentUid()
        val email = runCatching { auth.currentUserOrNull()?.email }.getOrNull()
        if (uid == null || auth.currentSessionOrNull() == null || (password != null && email.isNullOrBlank())) {
            callback(false, "Sign in again to delete your account.")
            return
        }
        val app = context.applicationContext
        run(callback, "Could not delete your account.") {
            if (password != null) {
                // Signing in again proves the password; it is the same account, so nothing reroutes.
                try {
                    signIn(email.orEmpty(), password)
                } catch (error: AuthRestException) {
                    if (error.errorCode == AuthErrorCode.InvalidCredentials) throw IllegalStateException("Your password is incorrect.")
                    throw error
                }
            }
            rpc("delete_account")
            accountNotice = "Your SPOT account was deleted."
            // Nothing this phone kept for the account is needed any more.
            withContext(Dispatchers.IO) {
                runCatching { PostDraftStore.forget(app, uid) }
                runCatching { PostMediaExporter.clearAccount(app, uid) }
                runCatching { chatMediaFolder(app, uid).deleteRecursively() }
                runCatching { StartupCache.forget(uid) }
            }
            withTimeoutOrNull(4_000) { PushNotifications.unregister() }
            runCatching { auth.clearSession() }
        }
    }


    // =========================================================
    // LOG OUT
    // =========================================================

    fun logOut() {
        scope.launch {
            // This phone stops getting the account's message notifications; it needs the session, so it goes first.
            withTimeoutOrNull(4_000) { PushNotifications.unregister() }
            // Offline sign-out still has to forget the local session.
            try { auth.signOut() } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { runCatching { auth.clearSession() } }
        }
    }


    // =========================================================
    // HELPERS
    // =========================================================

    private suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        pendingSignup = null
    }

    private fun run(
        callback: (Boolean, String?) -> Unit,
        fallback: String,
        success: String? = null,
        block: suspend () -> Unit
    ) {
        scope.launch {
            try {
                block()
                callback(true, success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                callback(false, if (error is IllegalStateException) error.message ?: fallback else errorText(error, fallback))
            }
        }
    }

    private fun metadata(key: String): String =
        runCatching { (auth.currentUserOrNull()?.userMetadata?.get(key) as? JsonPrimitive)?.content }.getOrNull().orEmpty()

    private fun sameUser(uid: String): Boolean = currentUid() == uid

    private fun validEmail(email: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(email).matches()


    // =========================================================
    // ERROR HANDLING
    // =========================================================

    private fun errorText(
        error: Exception,
        fallback: String
    ): String {
        logFailure(TAG, fallback, error)

        return when (error) {
            is HttpRequestTimeoutException -> "SPOT's server took too long to respond. Please try again."

            is IOException -> "Check your internet connection and try again."

            is AuthWeakPasswordException -> "Choose a stronger password and try again."

            is AuthRestException -> when (error.errorCode) {
                AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit ->
                    "Too many attempts. Wait a moment and try again."
                AuthErrorCode.InvalidCredentials -> "Incorrect email or password."
                AuthErrorCode.WeakPassword -> "Choose a stronger password and try again."
                AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists ->
                    "This email already has an account. Sign in or reset your password."
                AuthErrorCode.EmailProviderDisabled, AuthErrorCode.SignupDisabled ->
                    "Email sign-up is currently unavailable for SPOT."
                else -> fallback
            }

            is PostgrestRestException -> when (error.code) {
                "42501" -> "SPOT could not access your profile. Please contact support if retrying does not help."
                "PGRST202", "42P01", "42883" -> "SPOT's profile service is not ready. Please try again later."
                else -> fallback
            }

            else -> fallback
        }
    }
}
