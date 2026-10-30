package com.example.spotapp

import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.spotSp

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotapp.data.AuthRepository
import com.example.spotapp.data.GoogleSignInResult
import com.example.spotapp.data.GoogleAuthHelper
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.AnimatedLoginBackground
import com.example.spotapp.ui.theme.InterFontFamily
import com.example.spotapp.ui.theme.components.SpotAuthButton
import com.example.spotapp.ui.theme.components.SpotConfirmDialog
import com.example.spotapp.ui.theme.components.SpotAuthFieldShape
import com.example.spotapp.ui.theme.components.BisuSeal
import com.example.spotapp.ui.theme.components.SpotLogoMedallion
import com.example.spotapp.ui.theme.components.SpotNewPasswordForm
import com.example.spotapp.ui.theme.components.SpotScreenHeader
import com.example.spotapp.ui.theme.components.spotAuthFieldColors
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.MailOutline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val LoginGoldDark get() = SpotPalette.GoldInk
private val LoginInk get() = SpotPalette.Ink
private val LoginSecondary get() = SpotPalette.Muted
private val LoginBorder get() = SpotPalette.Line
private val LoginError get() = SpotPalette.ErrorText

@Composable
fun LoginUI(
    onSignUpClick: () -> Unit = {},
    onLoginSuccess: () -> Unit = {}
) {
    var email by rememberSaveable { mutableStateOf("") }
    // Keep passwords and active requests out of saved screen state.
    var password by remember { mutableStateOf("") }
    var busyAction by remember { mutableStateOf<String?>(null) }
    // After this phone deleted its account, Login opens by saying so.
    val notice = remember { AuthRepository.takeAccountNotice() }
    var message by remember { mutableStateOf(notice.orEmpty()) }
    var messageIsError by remember { mutableStateOf(notice == null) }
    var resetDialogVisible by remember { mutableStateOf(false) }
    var resetCodeEmail by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val loginSuccess by rememberUpdatedState(onLoginSuccess)
    fun showError(text: String) {
        messageIsError = true
        message = text
    }

    val submitLogin: () -> Unit = {
        if (busyAction == null) {
            val cleanEmail = email.trim()
            when {
                !Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() ->
                    showError("Enter a valid email address.")
                password.isEmpty() -> showError("Enter your password.")
                else -> {
                    focusManager.clearFocus()
                    busyAction = "email"
                    message = ""
                    scope.launch {
                        try {
                            val result = awaitLoginCallback { callback ->
                                AuthRepository.logIn(cleanEmail, password, callback)
                            }
                            if (result.first) {
                                password = ""
                                loginSuccess()
                            } else if (AuthRepository.currentUserNeedsEmailVerification()) {
                                // An unconfirmed account: a fresh code was emailed, and Sign up's code screen takes it.
                                password = ""
                                onSignUpClick()
                            } else {
                                showError(result.second ?: "Could not sign in. Please try again.")
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            showError(error.localizedMessage ?: "Could not sign in.")
                        } finally {
                            busyAction = null
                        }
                    }
                }
            }
        }
    }

    // Once the code is sent, the reset form takes the whole screen; Back returns to sign-in.
    val resetEmail = resetCodeEmail
    if (resetEmail != null) ResetPasswordPage(resetEmail, onBack = { resetCodeEmail = null }) {
        resetCodeEmail = null
        password = ""
        loginSuccess()
    } else LoginScreen(
        email = email, password = password, busyAction = busyAction,
        message = message, messageIsError = messageIsError,
        onEmailChange = { email = it; message = "" },
        onPasswordChange = { password = it; message = "" },
        onSubmit = submitLogin, onSignUp = onSignUpClick,
        onReset = {
            if (Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) resetDialogVisible = true
            else showError("Enter your email above, then tap Forgot password.")
        },
        onGoogle = {
            if (busyAction == null) {
                focusManager.clearFocus()
                busyAction = "google"
                message = ""
                scope.launch {
                    try {
                        when (val result = GoogleAuthHelper.signInWithGoogle(context)) {
                            GoogleSignInResult.SignedIn -> { password = ""; loginSuccess() }
                            GoogleSignInResult.NotCompleted -> {
                                messageIsError = false
                                message = "Google sign-in wasn't completed. You can try again."
                            }
                            is GoogleSignInResult.Failed -> showError(result.message)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        showError("Google sign-in failed. Please try again.")
                    } finally { busyAction = null }
                }
            }
        }
    )

    if (resetDialogVisible) {
        SpotConfirmDialog(Icons.Rounded.MailOutline, "Reset password", "Send a password reset code to ${email.trim()}?", "Send code",
            onDismiss = { resetDialogVisible = false }) {
            resetDialogVisible = false
            busyAction = "reset"
            message = ""
            val resetEmail = email.trim()
            scope.launch {
                try {
                    val result = awaitLoginCallback { callback ->
                        AuthRepository.sendPasswordResetEmail(resetEmail, callback)
                    }
                    messageIsError = !result.first
                    if (result.first) {
                        message = "If this email has an account, a reset code will arrive shortly. Check your spam folder too."
                        resetCodeEmail = resetEmail
                    } else {
                        message = result.second ?: "Couldn't send the reset code. Please try again."
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    showError(error.localizedMessage ?: "Couldn't send the reset code.")
                } finally {
                    busyAction = null
                }
            }
        }
    }
}

/** Supabase emails a one-time code; it signs the member in so the new password can be saved. */
@Composable
private fun ResetPasswordPage(email: String, onBack: () -> Unit, onSaved: () -> Unit) {
    // Codes and passwords stay out of saved state.
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val ready = Regex("[0-9]{6,10}").matches(code) && AuthRepository.passwordError(newPassword) == null && confirm == newPassword
    fun save() {
        if (!ready || busy) return
        busy = true; error = ""
        AuthRepository.completePasswordReset(email, code, newPassword) { ok, message ->
            busy = false
            if (ok) onSaved() else error = message.orEmpty()
        }
    }
    BackHandler { if (!busy) onBack() }
    Box(Modifier.fillMaxSize().testTag("reset-password-page")) {
        AnimatedLoginBackground(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            SpotScreenHeader("Reset password", onBack = onBack, backEnabled = !busy)
            SpotNewPasswordForm(
                intro = "Enter the code we sent to $email, then choose a new password.",
                newPassword = newPassword, onNewPassword = { newPassword = it; error = "" },
                confirm = confirm, onConfirm = { confirm = it; error = "" },
                visible = visible, onToggleVisible = { visible = !visible }, busy = busy, error = error,
                saveLabel = "Save password", ready = ready, onSave = ::save
            ) {
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(10); error = "" }, Modifier.fillMaxWidth().testTag("reset-code"),
                    label = { Text("Code from the email") }, singleLine = true, enabled = !busy,
                    shape = SpotAuthFieldShape, colors = spotAuthFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next))
            }
        }
    }
}

@Composable
internal fun LoginScreen(
    email: String, password: String,
    busyAction: String? = null, message: String = "", messageIsError: Boolean = true,
    onEmailChange: (String) -> Unit, onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit, onGoogle: () -> Unit, onReset: () -> Unit, onSignUp: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var editingPassword by rememberSaveable { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val busy = busyAction != null

    Box(Modifier.fillMaxSize()) {
        AnimatedLoginBackground(Modifier.matchParentSize())
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)) {
            val availableHeight = maxHeight / density.fontScale
            val compactHeader = availableHeight < 640.dp
            val tiny = availableHeight < 370.dp
            val columnWidth = minOf(maxWidth, 440.dp)
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (tiny) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SpotLogoMedallion(28.dp, Modifier.testTag("login-spot-logo"), glow = false)
                        BisuSeal(28.dp, Modifier.testTag("login-bisu-logo"), animated = false)
                        Text("Welcome to SPOT", color = LoginInk, fontSize = 23.spotSp, lineHeight = 28.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
                    }
                } else {
                    // Big centered logos on most phones; only really short screens shrink them to keep room for the form.
                    // SPOT and BISU sit side by side, so both also shrink if the pair would be wider than the screen.
                    val logo = minOf(when {
                        availableHeight >= 720.dp -> 144.dp
                        !compactHeader -> 120.dp
                        availableHeight >= 480.dp -> 96.dp
                        else -> 48.dp
                    }, columnWidth / 2.16f)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(logo * .16f)) {
                        SpotLogoMedallion(logo, Modifier.testTag("login-spot-logo"))
                        BisuSeal(logo, Modifier.testTag("login-bisu-logo"))
                    }
                    Spacer(Modifier.height(if (compactHeader) 8.dp else 16.dp))
                    Text("Welcome back.", color = LoginInk, fontSize = if (compactHeader) 28.spotSp else 34.spotSp,
                        lineHeight = if (compactHeader) 34.sp else 40.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp, textAlign = TextAlign.Center)
                    if (!compactHeader) {
                        Spacer(Modifier.height(6.dp))
                        Text("Pick up where you left off.", color = LoginSecondary,
                            fontSize = 13.spotSp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(if (tiny) 8.dp else 16.dp))
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val height = maxHeight / density.fontScale
                    val compact = height < 520.dp
                    val oneField = height < 400.dp
                    val showEmail = !oneField || !editingPassword
                    val showPassword = !oneField || editingPassword
                    val showGoogle = !oneField || !editingPassword
                    val shownMessage = localError.ifBlank { message }

                    fun next() {
                        if (busy) return
                        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                            localError = "Enter a valid email address."
                            emailFocus.requestFocus()
                        } else {
                            localError = ""
                            editingPassword = true
                            if (!oneField) passwordFocus.requestFocus()
                        }
                    }
                    LaunchedEffect(oneField, editingPassword) {
                        if (keyboardVisible) {
                            if (oneField && !editingPassword) emailFocus.requestFocus()
                            else if (editingPassword) passwordFocus.requestFocus()
                        }
                    }
                    BackHandler(enabled = oneField && editingPassword && !busy) {
                        editingPassword = false
                        localError = ""
                    }

                    // The form sits straight on the page, no card around it.
                    run {
                        Column(Modifier.padding(vertical = if (compact) 4.dp else 8.dp)) {
                            if (oneField && editingPassword) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { editingPassword = false; localError = "" }, enabled = !busy,
                                        modifier = Modifier.weight(1f).testTag("login-edit-email"), contentPadding = PaddingValues(0.dp)) {
                                        Text("Edit email", color = LoginGoldDark, fontSize = 12.spotSp)
                                    }
                                    TextButton(onClick = { focus.clearFocus(); onReset() }, enabled = !busy,
                                        modifier = Modifier.weight(1f).testTag("login-reset"), contentPadding = PaddingValues(0.dp)) {
                                        Text("Forgot password?", color = LoginGoldDark, fontSize = 12.spotSp, lineHeight = 16.sp,
                                            textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            if (showEmail) {
                                LoginInput(email, { onEmailChange(it); localError = "" }, "Email address", !busy,
                                    false, false, {}, { next() },
                                    Modifier.focusRequester(emailFocus).testTag("login-email")
                                        .onFocusChanged { if (it.isFocused) editingPassword = false }
                                        .semantics { contentType = ContentType.EmailAddress })
                            }
                            if (showEmail && showPassword) Spacer(Modifier.height(10.dp))
                            if (showPassword) {
                                LoginInput(password, { onPasswordChange(it); localError = "" }, "Password", !busy,
                                    true, passwordVisible, { passwordVisible = !passwordVisible }, onSubmit,
                                    Modifier.focusRequester(passwordFocus).testTag("login-password")
                                        .onFocusChanged { if (it.isFocused) editingPassword = true }
                                        .semantics { contentType = ContentType.Password })
                                if (!oneField) {
                                    TextButton(onClick = { focus.clearFocus(); onReset() }, enabled = !busy,
                                        modifier = Modifier.align(Alignment.End).testTag("login-reset")) {
                                        Text("Forgot password?", color = LoginGoldDark, fontSize = 12.spotSp)
                                    }
                                }
                            }
                            if (shownMessage.isNotBlank()) {
                                Text(shownMessage, color = if (localError.isNotBlank() || messageIsError) LoginError else SpotPalette.SuccessText,
                                    fontSize = 12.spotSp, lineHeight = 16.sp, maxLines = if (tiny) 2 else 3, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(vertical = if (tiny) 4.dp else 8.dp).testTag("login-message")
                                        .semantics { liveRegion = LiveRegionMode.Polite })
                            } else if (oneField) Spacer(Modifier.height(if (tiny) 8.dp else 12.dp))
                            Box(Modifier.testTag("login-submit")) {
                                SpotAuthButton(if (oneField && !editingPassword) "Continue" else "Log in",
                                    loading = busyAction == "email", enabled = !busy,
                                    onClick = { if (oneField && !editingPassword) next() else onSubmit() })
                            }
                            if (showGoogle) {
                                Row(Modifier.padding(vertical = if (tiny) 2.dp else if (compact) 8.dp else 14.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    HorizontalDivider(Modifier.weight(1f), color = LoginBorder)
                                    Text("or", color = LoginSecondary, fontSize = 11.spotSp, lineHeight = 16.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp))
                                    HorizontalDivider(Modifier.weight(1f), color = LoginBorder)
                                }
                                OutlinedButton(onClick = onGoogle, enabled = !busy,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("login-google"),
                                    shape = RoundedCornerShape(50), border = BorderStroke(1.dp, SpotPalette.Line),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = SpotPalette.Surface, contentColor = LoginInk)) {
                                    if (busyAction == "google") CircularProgressIndicator(Modifier.size(20.dp), color = LoginGoldDark, strokeWidth = 2.dp)
                                    else Image(painterResource(R.drawable.google), null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Continue with Google", fontSize = 13.spotSp, lineHeight = 17.sp,
                                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                }
                            }
                            if (!compact) Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onSignUp, enabled = !busy,
                                modifier = Modifier.fillMaxWidth().testTag("login-signup"),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp)) {
                                Text(buildAnnotatedString {
                                    append("Don't have an account? ")
                                    withStyle(SpanStyle(color = LoginGoldDark, fontWeight = FontWeight.Bold)) {
                                        append("Create account")
                                    }
                                }, color = LoginSecondary, fontSize = 12.spotSp, lineHeight = 16.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginInput(
    value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean,
    isPassword: Boolean, passwordVisible: Boolean, onVisibilityClick: () -> Unit, onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), enabled = enabled,
        label = { Text(label) }, singleLine = true, shape = SpotAuthFieldShape, textStyle = TextStyle(fontFamily = InterFontFamily, fontSize = 15.spotSp),
        leadingIcon = { Icon(if (isPassword) Icons.Outlined.Lock else Icons.Rounded.MailOutline, null, Modifier.size(20.dp)) },
        trailingIcon = if (isPassword) {{
            IconButton(onClick = onVisibilityClick, enabled = enabled) {
                Icon(if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    if (passwordVisible) "Hide password" else "Show password")
            }
        }} else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Email,
            imeAction = if (isPassword) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onDone() }, onDone = { onDone() }),
        colors = spotAuthFieldColors())
}

@Preview(name = "Login - phone", widthDp = 360, heightDp = 720)
@Preview(name = "Login - small phone", widthDp = 320, heightDp = 568)
@Preview(name = "Login - keyboard", widthDp = 360, heightDp = 340)
@Preview(name = "Login - large text", widthDp = 320, heightDp = 568, fontScale = 1.5f)
@Composable
private fun LoginPreview() {
    SPOTAppTheme { LoginScreen("", "", onEmailChange = {}, onPasswordChange = {},
        onSubmit = {}, onGoogle = {}, onReset = {}, onSignUp = {}) }
}

@Preview(name = "Reset password - light", widthDp = 360, heightDp = 720)
@Preview(name = "Reset password - large text", widthDp = 360, heightDp = 720, fontScale = 1.35f)
@Composable
private fun ResetPasswordPreview() {
    SPOTAppTheme(dark = false) { ResetPasswordPage("you@example.com", onBack = {}, onSaved = {}) }
}

@Preview(name = "Reset password - dark", widthDp = 360, heightDp = 720)
@Composable
private fun ResetPasswordDarkPreview() {
    SPOTAppTheme(dark = true) { ResetPasswordPage("you@example.com", onBack = {}, onSaved = {}) }
}
private suspend fun awaitLoginCallback(
    start: ((Boolean, String?) -> Unit) -> Unit
): Pair<Boolean, String?> = suspendCancellableCoroutine { continuation ->
    start { success, error ->
        if (continuation.isActive) continuation.resume(success to error)
    }
}

