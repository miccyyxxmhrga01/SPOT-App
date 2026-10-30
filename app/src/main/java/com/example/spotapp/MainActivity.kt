package com.example.spotapp

import com.example.spotapp.ui.theme.spotSp

import android.graphics.Color as AndroidColor
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.spotapp.data.AuthRepository
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.example.spotapp.data.PushNotifications
import com.example.spotapp.data.SpotLinks
import com.example.spotapp.data.StartupCache
import com.example.spotapp.data.cloudinary.CloudinaryImageInterceptor
import com.example.spotapp.data.cloudinary.CloudinaryManager
import com.example.spotapp.data.preferences.OnboardingPreferences
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import com.example.spotapp.data.preferences.ThemeMode
import com.example.spotapp.data.preferences.ThemePreferences
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.data.Supabase
import com.example.spotapp.data.isOnline
import com.example.spotapp.data.onlineUpdates
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.event.AuthEvent
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import com.example.spotapp.ui.theme.components.AnimatedLoginBackground
import com.example.spotapp.ui.theme.components.SpotLogoMedallion
import com.example.spotapp.ui.theme.components.SpotWordmarkText
import com.example.spotapp.ui.theme.components.SpotEmailVerificationScreen
import com.example.spotapp.ui.theme.navigation.SpotNavigation
import com.example.spotapp.ui.theme.screens.onboarding.OnboardingScreen

private val MainSpotGold = Color(0xFFD4AF37)
private val MainSpotInk = Color(0xFF171717)

/** Large system text still enlarges SPOT, but only up to this much, so screens stay compact. */
private const val MAX_FONT_SCALE = 1.15f

class MainActivity : ComponentActivity() {
    // Applied to the activity's own context, so pop-ups, sheets and pages all get the same capped text size.
    override fun attachBaseContext(newBase: Context) {
        val config = newBase.resources.configuration
        super.attachBaseContext(if (config.fontScale <= MAX_FONT_SCALE) newBase
            else newBase.createConfigurationContext(Configuration(config).apply { fontScale = MAX_FONT_SCALE }))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The splash stays until the saved theme is read, so a Dark choice never opens on a light frame.
        var themeLoaded = false
        splash.setKeepOnScreenCondition { !themeLoaded }
        // The splash fades out over SPOT's opening screen instead of vanishing, so its icon dissolves into the medallion.
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate().alpha(0f).setDuration(250L).withEndAction { provider.remove() }.start()
        }

        CloudinaryManager.init(this)
        StartupCache.init(this)
        // Every AsyncImage uses this loader, so Cloudinary photos load at the size they're shown.
        SingletonImageLoader.setSafe { context -> ImageLoader.Builder(context).components { add(CloudinaryImageInterceptor()) }.build() }
        PushNotifications.createChannels(this)
        // A tapped notification opens its chat or the Notifications tab, and a share link its post, event or profile;
        // after rotation they were already handled.
        if (savedInstanceState == null) { PushNotifications.handle(intent); SpotLinks.handle(intent) }

        setContent {
            val mode = remember { ThemePreferences.mode(applicationContext) }.collectAsState(null).value
                ?: return@setContent
            SideEffect { themeLoaded = true }
            val dark = when (mode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            // System bars and the window behind them follow the theme, so no white strip shows around dark screens.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                        else SystemBarStyle.light(scrim = AndroidColor.TRANSPARENT, darkScrim = AndroidColor.WHITE),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                        else SystemBarStyle.light(scrim = AndroidColor.TRANSPARENT, darkScrim = AndroidColor.WHITE)
                )
                window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF121110.toInt() else AndroidColor.WHITE))
                onDispose { }
            }
            SPOTAppTheme(dark) {
                Surface(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    color = SpotPalette.Page
                ) {
                    SpotApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PushNotifications.appVisible = true
    }

    override fun onStop() {
        PushNotifications.appVisible = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PushNotifications.handle(intent)
        SpotLinks.handle(intent)
    }
}

@Composable
fun SpotApp() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val onboardingFlow = remember(context) {
        OnboardingPreferences.isOnboardingCompleted(context)
    }
    val onboardingCompleted by onboardingFlow.collectAsState(initial = null)

    when (onboardingCompleted) {
        // Only the background while this flag loads (a few milliseconds), so the opening animation plays once, in AuthFlow.
        null -> AnimatedLoginBackground()
        false -> OnboardingScreen(
            onFinished = {
                scope.launch { OnboardingPreferences.setOnboardingCompleted(context) }
            }
        )
        true -> AuthFlow()
    }
}

private enum class AuthScreen {
    LOADING, LOGIN, SIGN_UP, EMAIL_VERIFICATION, PROFILE_SETUP, HOME, ERROR, OFFLINE
}

/** Re-imports the saved session, so Supabase retries its refresh now instead of after its 10 s back-off. */
private suspend fun retrySavedSession() {
    try {
        Supabase.auth.loadFromStorage()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
    }
}

// Auth.events is the only signal for a failed refresh while the status is still Initializing.
@OptIn(SupabaseExperimental::class)
@Composable
private fun AuthFlow() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val online by remember(context) { context.onlineUpdates() }.collectAsState(initial = context.isOnline())
    var currentScreen by rememberSaveable { mutableStateOf(AuthScreen.LOADING) }
    var authReady by remember { mutableStateOf(false) }
    var signedInUid by remember { mutableStateOf<String?>(null) }
    var accountError by remember { mutableStateOf("") }
    // The saved session couldn't be refreshed at startup; Supabase keeps retrying in the background.
    var startupRefreshFailed by remember { mutableStateOf(false) }
    var retrying by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Supabase.auth.events.collect { event ->
            if (event is AuthEvent.RefreshFailure && !authReady) startupRefreshFailed = true
        }
    }

    LaunchedEffect(online) {
        if (!online) return@LaunchedEffect
        if (!authReady && startupRefreshFailed) retrySavedSession()
        if (currentScreen == AuthScreen.OFFLINE) currentScreen = AuthScreen.LOADING
    }

    LaunchedEffect(Unit) {
        Supabase.auth.sessionStatus.collect { status ->
            // Wait for the saved session; a failed refresh (offline) keeps the current state while Supabase retries.
            if (status is SessionStatus.RefreshFailure && !authReady) startupRefreshFailed = true
            if (status is SessionStatus.Initializing || status is SessionStatus.RefreshFailure) return@collect
            val previousUid = signedInUid
            val firstEvent = !authReady
            val restoredUid = (status as? SessionStatus.Authenticated)?.session?.user?.id
            signedInUid = restoredUid
            authReady = true
            startupRefreshFailed = false

            if (restoredUid == null) {
                if (currentScreen != AuthScreen.SIGN_UP) {
                    currentScreen = AuthScreen.LOGIN
                }
            } else if (firstEvent) {
                // Preserve an in-progress signup during activity recreation.
                if (currentScreen != AuthScreen.SIGN_UP) {
                    currentScreen = AuthScreen.LOADING
                }
            } else if (restoredUid != previousUid) {
                // Login/Google callbacks finish authentication before routing.
                // Signup also needs to finish its verification step first.
                if (currentScreen != AuthScreen.LOGIN && currentScreen != AuthScreen.SIGN_UP) {
                    currentScreen = AuthScreen.LOADING
                }
            }
        }
    }

    LaunchedEffect(authReady, signedInUid, currentScreen) {
        if (!authReady || currentScreen != AuthScreen.LOADING) return@LaunchedEffect
        val checkedUid = signedInUid
        if (checkedUid == null) {
            currentScreen = AuthScreen.LOGIN
            return@LaunchedEffect
        }
        // An account that reached Home on this phone before opens there straight away, with the saved feed, even
        // offline. The account check still runs behind it and only reroutes if the account needs something else.
        if (StartupCache.reachedHome(checkedUid)) {
            currentScreen = AuthScreen.HOME
            scope.launch {
                val result = withTimeoutOrNull(20000L) {
                    suspendCancellableCoroutine<Pair<String, String?>> { continuation ->
                        AuthRepository.checkUserDestination { destination, error ->
                            if (continuation.isActive) continuation.resume(destination to error)
                        }
                    }
                }
                // Offline or failed checks keep Home; the next start checks again.
                if (result == null || !result.second.isNullOrBlank() || AuthRepository.currentUid() != checkedUid) return@launch
                if (result.first != "HOME") {
                    StartupCache.rememberHome(checkedUid, false)
                    currentScreen = AuthScreen.LOADING
                }
            }
            return@LaunchedEffect
        }
        // The account check needs Supabase; waiting out its timeout offline would only end in an error.
        if (!context.isOnline()) {
            currentScreen = AuthScreen.OFFLINE
            return@LaunchedEffect
        }

        try {
            // Cancellation discards results from a previous screen or account.
            val result = withTimeoutOrNull(20000L) {
                suspendCancellableCoroutine<Pair<String, String?>> { continuation ->
                    AuthRepository.checkUserDestination { destination, error ->
                        if (continuation.isActive) continuation.resume(destination to error)
                    }
                }
            }

            if (AuthRepository.currentUid() != checkedUid) return@LaunchedEffect

            if ((result == null || !result.second.isNullOrBlank()) && !context.isOnline()) {
                currentScreen = AuthScreen.OFFLINE
            } else if (result == null) {
                accountError = "Your account is taking longer to load. Check your connection and try again."
                currentScreen = AuthScreen.ERROR
            } else if (!result.second.isNullOrBlank()) {
                accountError = result.second ?: "Could not load your account."
                currentScreen = AuthScreen.ERROR
            } else {
                StartupCache.rememberHome(checkedUid, result.first == "HOME")
                currentScreen = when (result.first) {
                    "HOME" -> AuthScreen.HOME
                    "PROFILE_SETUP" -> AuthScreen.PROFILE_SETUP
                    "EMAIL_VERIFICATION" -> AuthScreen.EMAIL_VERIFICATION
                    "LOGIN" -> AuthScreen.LOGIN
                    else -> {
                        accountError = "Could not load your account. Please try again."
                        AuthScreen.ERROR
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!context.isOnline()) {
                currentScreen = AuthScreen.OFFLINE
            } else {
                accountError = error.localizedMessage ?: "Could not load your account."
                currentScreen = AuthScreen.ERROR
            }
        }
    }

    // A restored screen name is not proof of a signed-in Supabase session: until it's read, SPOT shows the opening
    // screen, or null for "can't reach SPOT". One crossfade covers every change, and the opening screen keeps running
    // from the session read through the account check instead of starting over.
    val stage: AuthScreen? = when {
        !authReady && startupRefreshFailed -> null
        !authReady -> AuthScreen.LOADING
        else -> currentScreen
    }
    Crossfade(stage, animationSpec = tween(320), label = "auth screen") { shown -> when (shown) {
        null -> SpotOfflineScreen(online, retrying, onRetry = {
            if (!retrying) scope.launch {
                retrying = true
                retrySavedSession()
                delay(1500)
                retrying = false
            }
        })
        AuthScreen.LOADING -> SpotOpeningScreen()
        AuthScreen.LOGIN -> LoginUI(
            onLoginSuccess = { currentScreen = AuthScreen.LOADING },
            onSignUpClick = { currentScreen = AuthScreen.SIGN_UP }
        )
        AuthScreen.SIGN_UP -> SignUpUI(
            onLoginClick = { currentScreen = AuthScreen.LOGIN },
            onSignUpSuccess = { currentScreen = AuthScreen.LOADING }
        )
        AuthScreen.EMAIL_VERIFICATION -> key(signedInUid) {
            SpotEmailVerificationScreen(
                onVerified = { currentScreen = AuthScreen.LOADING },
                onUseAnotherAccount = { AuthRepository.logOut() }
            )
        }
        AuthScreen.PROFILE_SETUP -> key(signedInUid) {
            val profileUid = signedInUid
            ProfileSetupScreen(
                onSetupComplete = {
                    if (profileUid != null && AuthRepository.currentUid() == profileUid) {
                        currentScreen = AuthScreen.HOME
                    }
                }
            )
        }
        AuthScreen.HOME -> key(signedInUid) { HomeScreen(onLogout = { AuthRepository.logOut() }) }
        AuthScreen.ERROR -> SpotAccountErrorScreen(
            message = accountError,
            onRetry = { currentScreen = AuthScreen.LOADING },
            onSignOut = { AuthRepository.logOut() }
        )
        AuthScreen.OFFLINE -> SpotOfflineScreen(online, retrying = false, onRetry = { currentScreen = AuthScreen.LOADING })
    } }
}

@Composable
internal fun HomeScreen(onLogout: () -> Unit) {
    SpotNavigation(onLogout = onLogout)
}

/**
 * What SPOT shows while it opens. The medallion grows in where the system splash's icon was, a gold ring circles it
 * while SPOT works, and the wordmark and tagline fade in under it. After a few seconds the line says it's still
 * connecting, so a slow network doesn't look like a frozen app.
 */
@Composable
private fun SpotOpeningScreen() {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(4_000); slow = true }
    val motion = rememberInfiniteTransition(label = "opening")
    val breath by motion.animateFloat(1f, 1.035f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val spin by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "spin")
    val track = SpotPalette.Line
    val gold = SpotPalette.GoldDeep
    // The splash icon (drawable/spot_splash_icon) is this medallion at 160dp, centered on the whole screen. This one starts
    // there, fully drawn, so the splash fades out over the same picture; then it settles to 112dp as the ring and words
    // come in. The splash centers on the full screen and this page stops above the navigation bar: half of it evens that out.
    val lift = with(LocalDensity.current) { (WindowInsets.navigationBars.getBottom(this) / 2).toDp() }
    val ring = ((appear.value - .5f) / .5f).coerceIn(0f, 1f)
    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription = "Opening SPOT" }) {
        AnimatedLoginBackground()
        Box(Modifier.align(Alignment.Center).offset(y = lift).size(150.dp).graphicsLayer {
            val scale = (160f / 112f - (160f / 112f - 1f) * appear.value) * breath
            scaleX = scale; scaleY = scale
        }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = ring }) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                drawCircle(track, radius = size.minDimension / 2 - inset, style = Stroke(stroke))
                // A gold arc that fades in from its tail, circling the medallion.
                rotate(spin) {
                    drawArc(Brush.sweepGradient(0f to Color.Transparent, .3f to gold), startAngle = 0f, sweepAngle = 108f,
                        useCenter = false, topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            SpotLogoMedallion(112.dp)
        }
        // The words sit under the medallion and follow it in.
        Column(Modifier.align(Alignment.TopCenter).padding(top = maxHeight / 2 + lift + 75.dp + 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            val words = ((appear.value - .35f) / .65f).coerceIn(0f, 1f)
            SpotWordmarkText(26.spotSp, Modifier.graphicsLayer { alpha = words; translationY = (1 - words) * 10.dp.toPx() })
            Spacer(Modifier.height(6.dp))
            AnimatedContent(slow, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "opening line") { still ->
                Text(if (still) "Still connecting…" else "Campus talent & connections", Modifier.graphicsLayer { alpha = words },
                    color = SpotPalette.Muted, fontSize = 13.spotSp)
            }
        }
    }
}

/** SPOT opens by itself once the connection is back; Try again just skips the wait. */
@Composable
private fun SpotOfflineScreen(online: Boolean, retrying: Boolean, onRetry: () -> Unit) {
    // Online but still unreachable: the Wi-Fi has no internet, or Supabase is down.
    if (online) {
        SpotAccountErrorScreen("Check your connection and try again.", onRetry,
            title = "Can't reach SPOT", icon = Icons.Rounded.CloudOff, retrying = retrying)
    } else {
        SpotAccountErrorScreen("Connect to Wi-Fi or mobile data. SPOT opens once you're back online.", onRetry,
            title = "You're offline", icon = Icons.Rounded.WifiOff, retrying = retrying)
    }
}

@Composable
private fun SpotAccountErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onSignOut: (() -> Unit)? = null,
    title: String = "Couldn't open your account",
    icon: ImageVector? = null,
    retrying: Boolean = false
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedLoginBackground()
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 430.dp).fillMaxWidth(),
                color = SpotPalette.Surface,
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(
                    modifier = Modifier.padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (icon != null) {
                        Box(Modifier.size(56.dp).background(SpotPalette.GoldSoft, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(28.dp), tint = SpotPalette.GoldDeep)
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(title, color = SpotPalette.Ink,
                        fontSize = 20.spotSp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(message.ifBlank { "Please check your connection and try again." },
                        color = SpotPalette.Muted, fontSize = 13.spotSp,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onRetry,
                        enabled = !retrying,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MainSpotGold, contentColor = MainSpotInk,
                            disabledContainerColor = MainSpotGold.copy(alpha = 0.6f), disabledContentColor = MainSpotInk)
                    ) {
                        if (retrying) CircularProgressIndicator(Modifier.size(18.dp), color = MainSpotInk, strokeWidth = 2.dp)
                        else Text("Try again")
                    }
                    if (onSignOut != null) TextButton(onClick = onSignOut) {
                        Text("Sign out", color = SpotPalette.GoldInk)
                    }
                }
            }
        }
    }
}
