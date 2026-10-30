package com.example.spotapp

import com.example.spotapp.ui.theme.spotSp

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotapp.data.AuthRepository
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.AnimatedLoginBackground
import com.example.spotapp.ui.theme.InterFontFamily
import com.example.spotapp.ui.theme.components.SpotAuthButton

import com.example.spotapp.ui.theme.components.SpotAuthFieldShape
import com.example.spotapp.ui.theme.components.SpotGoldGradient
import com.example.spotapp.ui.theme.components.SpotHeaderAction
import com.example.spotapp.ui.theme.components.SpotLogoMedallion
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.SpotWordmarkText
import com.example.spotapp.ui.theme.components.spotAuthFieldColors
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.ui.graphics.SolidColor
import com.example.spotapp.ui.theme.components.SpotEmailVerificationContent
import com.example.spotapp.ui.theme.components.awaitSpotAuthResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

internal val SignupInk get() = SpotPalette.Ink
internal val SignupMuted get() = SpotPalette.Muted
internal val SignupGoldDark get() = SpotPalette.GoldInk
@Composable
fun SignUpUI(onLoginClick: () -> Unit = {}, onSignUpSuccess: () -> Unit = {}) {
    var verifying by rememberSaveable { mutableStateOf(AuthRepository.currentUserNeedsEmailVerification()) }
    var email by rememberSaveable { mutableStateOf(AuthRepository.currentUserEmail()) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var linkSent by rememberSaveable { mutableStateOf(false) }
    var verificationMessage by rememberSaveable { mutableStateOf("") }
    // Never persist a password through activity/process recreation.
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val latestSuccess by rememberUpdatedState(onSignUpSuccess)
    val latestLogin by rememberUpdatedState(onLoginClick)

    fun goToLogin() {
        if (loading) return
        focus.clearFocus()
        AuthRepository.logOut()
        latestLogin()
    }

    LaunchedEffect(Unit) {
        when {
            AuthRepository.currentUserNeedsEmailVerification() -> {
                // Arriving from Login with an unconfirmed account: a code was just sent, so Resend waits a minute.
                if (!verifying) linkSent = true
                email = AuthRepository.currentUserEmail()
                verifying = true
            }
            AuthRepository.isUserLoggedIn() -> latestSuccess()
            else -> verifying = false
        }
    }

    fun recoverAccount(error: String) {
        if (AuthRepository.currentUserEmail().equals(email.trim(), ignoreCase = true) &&
            AuthRepository.currentUserNeedsEmailVerification()) {
            password = ""
            linkSent = false
            verificationMessage = "Account created. Tap Resend code to get your code."
            verifying = true
        } else message = error
    }

    fun createAccount() {
        if (loading) return
        when {
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> {
                message = "Enter a valid email address."
                return
            }
            AuthRepository.passwordError(password) != null -> {
                message = AuthRepository.passwordError(password).orEmpty()
                return
            }
            !agreed -> { message = "Confirm you're 16+ and accept the Terms to continue."; return }
        }
        focus.clearFocus()
        loading = true
        message = ""
        scope.launch {
            try {
                val result = awaitSpotAuthResult { callback ->
                    // Public identity is collected once, in profile setup.
                    AuthRepository.createAccountAndSendVerification("", email.trim(), password, callback)
                }
                if (result.first) {
                    password = ""
                    email = AuthRepository.currentUserEmail()
                    if (AuthRepository.currentUserNeedsEmailVerification()) {
                        linkSent = true
                        verificationMessage = ""
                        verifying = true
                    } else latestSuccess()
                } else recoverAccount(result.second ?: "Could not create your account. Please retry.")
            } catch (timeout: TimeoutCancellationException) {
                recoverAccount("This took too long. Check your connection and retry.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                recoverAccount("Could not create your account. Please retry.")
            } finally { loading = false }
        }
    }

    if (verifying) {
        // Verification owns Back as well, so an active request cannot switch accounts.
        SpotEmailVerificationContent(
            onVerified = { latestSuccess() },
            onUseAnotherAccount = { goToLogin() },
            initialLinkSent = linkSent,
            initialMessage = verificationMessage
        )
    } else {
        BackHandler(enabled = loading) { }
        SignupAccountScreen(
            email = email, password = password, agreed = agreed, loading = loading, message = message,
            onEmailChange = { email = it; message = "" },
            onPasswordChange = { password = it; message = "" },
            onAgreementChange = { agreed = it; message = "" },
            onSubmit = { createAccount() }, onLogin = { goToLogin() }
        )
    }
}

// Shared, bounded frame. Only decoration is removed when height is constrained.
// No scroll containers: IME and safe-area insets reduce the actual layout bounds.
@Composable
internal fun SignupFrame(
    step: Int,
    onBack: () -> Unit,
    enabled: Boolean = true,
    showBack: Boolean = true,
    backLabel: String = "Back to sign in",
    content: @Composable BoxWithConstraintsScope.() -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedLoginBackground(Modifier.matchParentSize())

        BoxWithConstraints(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .padding(horizontal = 24.dp)
        ) {
            val effectiveHeight = maxHeight / LocalDensity.current.fontScale
            val compact = effectiveHeight < 480.dp
            Column(
                Modifier.widthIn(max = 440.dp).fillMaxSize().align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showBack) {
                        SpotHeaderAction(Icons.AutoMirrored.Rounded.ArrowBack, backLabel, onBack, enabled = enabled)
                        Spacer(Modifier.width(4.dp))
                    }
                    SpotLogoMedallion(30.dp, glow = false, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    SpotWordmarkText(17.spotSp)
                    Spacer(Modifier.weight(1f))
                    Text("${step + 1} OF 3", Modifier.background(SpotPalette.GoldSoft, CircleShape)
                        .border(1.dp, SpotPalette.GoldLine, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                        color = SignupGoldDark, fontSize = 10.spotSp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
                }
                if (!compact) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Account", "Verify email", "Your profile").forEachIndexed { index, label ->
                            Column(Modifier.weight(1f)) {
                                Box(Modifier.fillMaxWidth().height(4.dp).background(
                                    if (index <= step) SpotGoldGradient else SolidColor(if (SpotPalette.dark) SpotPalette.Track else Color(0xFFE8E2D2)), CircleShape))
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (index < step) Icon(Icons.Rounded.Check, null, Modifier.padding(end = 3.dp).size(12.dp), tint = SignupGoldDark)
                                    Text(label, color = if (index <= step) SignupGoldDark else SignupMuted, fontSize = 11.spotSp,
                                        fontWeight = if (index == step) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    content()
                }
                Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
            }
        }
    }
}

@Composable
internal fun SignupAccountScreen(
    email: String,
    password: String,
    agreed: Boolean,
    loading: Boolean = false,
    message: String = "",
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAgreementChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onLogin: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var editingPassword by rememberSaveable { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }
    var policy by remember { mutableStateOf<SignupPolicy?>(null) }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0

    policy?.let { SignupPolicyDialog(it, onDismiss = { policy = null }) }

    SignupFrame(step = 0, onBack = onLogin, enabled = !loading, showBack = false) {
        // Reserve space for the sign-in footer on every screen size.
        val height = (maxHeight - 52.dp) / density.fontScale
        val compact = height < 520.dp
        val oneField = height < 340.dp
        val showEmail = !oneField || !editingPassword
        val showPassword = !oneField || editingPassword
        val shownMessage = localError.ifBlank { message }

        fun next() {
            if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                localError = "Enter a valid email address."
                emailFocus.requestFocus()
            } else {
                localError = ""
                editingPassword = true
                if (!oneField) passwordFocus.requestFocus()
            }
        }

        // Keep the correct field focused when IME resizing introduces the short layout.
        LaunchedEffect(oneField, editingPassword) {
            if (keyboardVisible) {
                if (oneField && !editingPassword) emailFocus.requestFocus()
                else if (editingPassword) passwordFocus.requestFocus()
            }
        }
        BackHandler(enabled = !loading) {
            if (oneField && editingPassword) { editingPassword = false; localError = "" }
            else onLogin()
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val bigHeadline = height >= 700.dp
            if (bigHeadline) {
                Text(buildAnnotatedString {
                    append("Find your people.\nShare your ")
                    withStyle(SpanStyle(color = SpotPalette.GoldDeep)) { append("spotlight") }
                    append(".")
                }, color = SignupInk, fontSize = 36.spotSp, lineHeight = 40.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Show your talent. Discover who's next.", color = SignupMuted,
                    fontSize = 15.spotSp, lineHeight = 22.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
            // The form sits straight on the page, no card around it.
            run {
                Column(Modifier.padding(vertical = if (compact) 4.dp else 8.dp)) {
                    if (!oneField && !bigHeadline) {
                        Text("Create your account", color = SignupInk, fontSize = if (compact) 22.spotSp else 25.spotSp,
                            fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
                        if (!compact) {
                            Spacer(Modifier.height(6.dp))
                            Text("Show your talent. Discover who's next.", color = SignupMuted,
                                fontSize = 13.spotSp, lineHeight = 19.sp)
                        }
                        Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                    }
                    if (oneField && editingPassword) {
                        TextButton(onClick = { editingPassword = false; localError = "" },
                            enabled = !loading, contentPadding = PaddingValues(0.dp)) {
                            Text("Edit email", color = SignupGoldDark, fontSize = 12.spotSp)
                            Spacer(Modifier.width(8.dp))
                            Text(email, color = SignupMuted, fontSize = 12.spotSp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                    if (showEmail) {
                        OutlinedTextField(
                            value = email, onValueChange = { onEmailChange(it); localError = "" },
                            modifier = Modifier.fillMaxWidth().focusRequester(emailFocus).testTag("signup-email")
                                .onFocusChanged { if (it.isFocused) editingPassword = false }
                                .semantics { contentType = ContentType.EmailAddress },
                            label = { Text("Email address") }, placeholder = { Text("you@example.com") },
                            leadingIcon = { Icon(Icons.Rounded.MailOutline, null, Modifier.size(20.dp)) },
                            singleLine = true, enabled = !loading,
                            textStyle = TextStyle(fontFamily = InterFontFamily, fontSize = 15.spotSp), shape = SpotAuthFieldShape,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { next() }),
                            colors = spotAuthFieldColors()
                        )
                    }
                    if (showEmail && showPassword) Spacer(Modifier.height(10.dp))
                    if (showPassword) {
                        OutlinedTextField(
                            value = password, onValueChange = { onPasswordChange(it); localError = "" },
                            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).testTag("signup-password")
                                .onFocusChanged { if (it.isFocused) editingPassword = true }
                                .semantics { contentType = ContentType.NewPassword },
                            label = { Text("Password") }, placeholder = { Text("At least ${AuthRepository.MIN_PASSWORD_LENGTH} characters") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, null, Modifier.size(20.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !loading) {
                                    Icon(if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        if (passwordVisible) "Hide password" else "Show password")
                                }
                            },
                            singleLine = true, enabled = !loading, textStyle = TextStyle(fontFamily = InterFontFamily, fontSize = 15.spotSp),
                            shape = SpotAuthFieldShape,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                            colors = spotAuthFieldColors()
                        )
                        if (!compact) {
                            Spacer(Modifier.height(7.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(14.dp),
                                    tint = if (password.length >= AuthRepository.MIN_PASSWORD_LENGTH) Color(0xFF426739) else SignupMuted)
                                Spacer(Modifier.width(5.dp))
                                Text("At least ${AuthRepository.MIN_PASSWORD_LENGTH} characters", color = SignupMuted, fontSize = 12.spotSp)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .toggleable(agreed, enabled = !loading, role = Role.Checkbox, onValueChange = onAgreementChange)
                                .testTag("signup-agreement"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = agreed, onCheckedChange = null, enabled = !loading,
                                colors = CheckboxDefaults.colors(checkedColor = SpotPalette.GoldDeep, uncheckedColor = Color(0xFFB9B3A5)),
                                modifier = Modifier.padding(end = 9.dp).size(22.dp))
                            Text(buildAnnotatedString {
                                val styles = TextLinkStyles(SpanStyle(color = SignupGoldDark,
                                    textDecoration = TextDecoration.Underline))
                                append("I'm 16+ and agree to the ")
                                withLink(LinkAnnotation.Clickable("terms", styles) { policy = SignupPolicy.TERMS }) {
                                    append("Terms")
                                }
                                append(" and acknowledge the ")
                                withLink(LinkAnnotation.Clickable("privacy", styles) { policy = SignupPolicy.PRIVACY }) {
                                    append("Privacy Policy")
                                }
                                append(".")
                            }, color = SignupMuted, fontSize = 12.spotSp, lineHeight = 16.sp)
                        }
                    }
                    if (shownMessage.isNotBlank()) {
                        Text(shownMessage, color = SpotPalette.ErrorText, fontSize = 12.spotSp, lineHeight = 16.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    if (!compact) Spacer(Modifier.height(6.dp))
                    Box(Modifier.testTag("signup-submit")) {
                        SpotAuthButton(
                            text = if (oneField && !editingPassword) "Continue" else "Create account",
                            loading = loading,
                            onClick = { if (oneField && !editingPassword) next() else onSubmit() }
                        )
                    }
                    if (!compact) {
                        Spacer(Modifier.height(14.dp))
                        Text("Next: verify your email, then make your profile yours.", color = SignupMuted,
                            fontSize = 12.spotSp, lineHeight = 17.sp)
                    }
                }
            }
                Row(Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("Already have an account?", color = SignupMuted, fontSize = 12.spotSp, lineHeight = 16.sp,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    TextButton(onClick = onLogin, enabled = !loading, modifier = Modifier.testTag("signup-signin")) {
                        Text("Sign in", color = SignupGoldDark, fontWeight = FontWeight.Bold, fontSize = 13.spotSp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(15.dp))
                    }
                }
        }
    }
}

@Preview(name = "Signup - phone", widthDp = 393, heightDp = 780, showBackground = true)
@Preview(name = "Signup - small phone", widthDp = 320, heightDp = 568, showBackground = true)
@Preview(name = "Signup - keyboard space", widthDp = 360, heightDp = 360, showBackground = true)
@Preview(name = "Signup - large text", widthDp = 393, heightDp = 780, fontScale = 1.5f, showBackground = true)
@Composable
private fun SignupPreview() {
    SPOTAppTheme {
        SignupAccountScreen("", "", false, onEmailChange = {}, onPasswordChange = {},
            onAgreementChange = {}, onSubmit = {}, onLogin = {})
    }
}
