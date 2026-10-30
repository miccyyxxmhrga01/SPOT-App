package com.example.spotapp.ui.theme.components

import com.example.spotapp.ui.theme.spotSp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotapp.SignupFrame
import com.example.spotapp.SignupGoldDark
import com.example.spotapp.SignupInk
import com.example.spotapp.SignupMuted
import com.example.spotapp.data.AuthRepository
import com.example.spotapp.ui.theme.SPOTAppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

@Composable
fun SpotEmailVerificationScreen(onVerified: () -> Unit, onUseAnotherAccount: () -> Unit) {
    SpotEmailVerificationContent(onVerified, onUseAnotherAccount)
}

@Composable
internal fun SpotEmailVerificationContent(
    onVerified: () -> Unit,
    onUseAnotherAccount: () -> Unit,
    initialLinkSent: Boolean = false,
    initialMessage: String = ""
) {
    val email = AuthRepository.currentUserEmail()
    val scope = rememberCoroutineScope()
    val latestOnVerified by rememberUpdatedState(onVerified)
    var busyAction by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf(initialMessage) }
    var messageIsError by remember { mutableStateOf(initialMessage.isNotBlank() && !initialLinkSent) }
    // The code from the email; it stays out of saved state.
    var code by remember { mutableStateOf("") }
    var continueSent by remember { mutableStateOf(false) }
    var nextSendAt by rememberSaveable(email) {
        mutableLongStateOf(if (initialLinkSent) System.currentTimeMillis() + 60000L else 0L)
    }
    var secondsRemaining by remember { mutableIntStateOf(0) }

    LaunchedEffect(nextSendAt) {
        do {
            secondsRemaining = ((nextSendAt - System.currentTimeMillis() + 999L) / 1000L)
                .coerceIn(0L, 60L).toInt()
            if (secondsRemaining > 0) delay(1.seconds)
        } while (secondsRemaining > 0)
    }

    fun request(sendEmail: Boolean) {
        if (busyAction != null || continueSent) return
        if (sendEmail && System.currentTimeMillis() < nextSendAt) return
        if (!sendEmail && code.length < 6) return
        busyAction = if (sendEmail) "send" else "check"
        message = ""
        scope.launch {
            try {
                val result = awaitSpotAuthResult { callback ->
                    if (sendEmail) AuthRepository.resendVerificationEmail(callback)
                    else AuthRepository.verifySignupCode(code, callback)
                }
                messageIsError = !result.first
                when {
                    !result.first -> message = result.second ?: "Could not complete this request. Please retry."
                    sendEmail -> {
                        nextSendAt = System.currentTimeMillis() + 60000L
                        code = ""
                        message = "New code sent. Check your inbox or spam folder."
                    }
                    else -> { continueSent = true; latestOnVerified() }
                }
            } catch (timeout: TimeoutCancellationException) {
                messageIsError = true
                message = "This took too long. Check your connection and retry."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messageIsError = true
                message = "Could not check your account. Please retry."
            } finally { busyAction = null }
        }
    }

    val enabled = busyAction == null && !continueSent
    BackHandler { if (enabled) onUseAnotherAccount() }
    SignupVerificationScreen(
        email = email, code = code, onCodeChange = { code = it; message = "" },
        message = message, messageIsError = messageIsError,
        checking = busyAction == "check", sending = busyAction == "send",
        enabled = enabled, secondsRemaining = secondsRemaining,
        onCheck = { request(false) }, onResend = { request(true) }, onBack = onUseAnotherAccount
    )
}

/** Enter the code emailed at sign-up (Supabase's "Confirm signup" template), resend it, or change account. */
@Composable
internal fun SignupVerificationScreen(
    email: String,
    code: String = "",
    onCodeChange: (String) -> Unit = {},
    message: String = "",
    messageIsError: Boolean = false,
    checking: Boolean = false,
    sending: Boolean = false,
    enabled: Boolean = true,
    secondsRemaining: Int = 0,
    onCheck: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    SignupFrame(step = 1, onBack = onBack, enabled = enabled) {
        val height = maxHeight / LocalDensity.current.fontScale
        val compact = height < 470.dp
        val tiny = height < 320.dp
        // The step sits straight on the page, no card around it.
        run {
            Column(Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 8.dp)) {
                if (!compact) {
                    Box(Modifier.size(88.dp).background(SpotPalette.GoldSoft, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MarkEmailRead, null, Modifier.size(40.dp), tint = SpotPalette.Ink)
                    }
                    Spacer(Modifier.height(28.dp))
                }
                Text("Check your inbox", color = SignupInk, fontSize = if (tiny) 21.spotSp else 34.spotSp,
                    lineHeight = if (tiny) 26.sp else 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
                Spacer(Modifier.height(if (tiny) 4.dp else 10.dp))
                if (!tiny) {
                    Text("Enter the code we sent to", color = SignupMuted, fontSize = 13.spotSp)
                    Spacer(Modifier.height(5.dp))
                }
                Text(email.ifBlank { "your account email" }, color = SignupInk, fontSize = 15.spotSp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(if (tiny) 10.dp else 22.dp))
                // Digits only, spaced out like the code in the email; Done on the keyboard verifies.
                OutlinedTextField(code, { value -> onCodeChange(value.filter(Char::isDigit).take(10)) },
                    Modifier.fillMaxWidth().testTag("verify-code"), enabled = enabled, singleLine = true,
                    label = { Text("Code from the email") }, shape = SpotAuthFieldShape, colors = spotAuthFieldColors(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 22.spotSp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 6.sp, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (code.length >= 6) onCheck() }))
                if (!compact) {
                    Spacer(Modifier.height(14.dp))
                    Text("Your profile is next. The code expires after a while, so use the newest one.",
                        color = SignupMuted, fontSize = 12.spotSp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(if (tiny) 10.dp else 20.dp))
                if (message.isNotBlank()) {
                    Text(message, color = if (messageIsError) SpotPalette.ErrorText else SpotPalette.SuccessText,
                        fontSize = 12.spotSp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
                }
                Box(Modifier.testTag("verify-submit")) {
                    SpotAuthButton(text = "Verify email", loading = checking, enabled = enabled && code.length >= 6, onClick = onCheck)
                }
                val resendText = when {
                    sending -> "Sending..."
                    secondsRemaining > 0 -> "Resend in ${secondsRemaining}s"
                    else -> "Resend code"
                }
                if (tiny) {
                    // Barely any height: keep both actions on one quiet row.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onResend, enabled = enabled && secondsRemaining == 0) {
                            Text(resendText, color = if (secondsRemaining > 0) SignupMuted else SignupGoldDark, fontSize = 12.spotSp)
                        }
                        TextButton(onClick = onBack, enabled = enabled) {
                            Text("Change account", color = SignupMuted, fontSize = 12.spotSp)
                        }
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    SpotAuthSecondaryButton(resendText, enabled = enabled && !sending && secondsRemaining == 0, onClick = onResend)
                    TextButton(onClick = onBack, enabled = enabled, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Use another account", color = SignupGoldDark, fontSize = 14.spotSp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (!compact) {
                    Text("No email? Check your spam folder.", color = SignupMuted, fontSize = 12.spotSp,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

internal suspend fun awaitSpotAuthResult(start: ((Boolean, String?) -> Unit) -> Unit): Pair<Boolean, String?> =
    withTimeout(30.seconds) {
        suspendCancellableCoroutine { continuation ->
            start { success, error -> if (continuation.isActive) continuation.resume(success to error) }
        }
    }

@Preview(name = "Verify - phone", widthDp = 393, heightDp = 780, showBackground = true)
@Preview(name = "Verify - short screen", widthDp = 360, heightDp = 360, showBackground = true)
@Composable
private fun SignupVerificationPreview() {
    SPOTAppTheme {
        SignupVerificationScreen("you@example.com", onCheck = {}, onResend = {}, onBack = {})
    }
}
