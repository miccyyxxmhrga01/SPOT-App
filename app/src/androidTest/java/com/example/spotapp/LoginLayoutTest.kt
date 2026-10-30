package com.example.spotapp

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.ui.theme.SPOTAppTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class LoginLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val restore by lazy { StateRestorationTester(compose) }
    private var submits = 0
    private var google = 0
    private var resets = 0
    private var signups = 0

    @Before fun keepTestActivityVisible() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    private fun show(width: Int = 360, height: Int = 720, scale: Float = 1f, busy: String? = null, message: String = "",
                     dark: Boolean? = null) {
        restore.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                SPOTAppTheme(dark = dark ?: isSystemInDarkTheme()) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.requiredSize(width.dp, height.dp).testTag("fixture")) {
                            var email by rememberSaveable { mutableStateOf("") }
                            var password by remember { mutableStateOf("") }
                            LoginScreen(email, password, busy, message,
                                onEmailChange = { email = it }, onPasswordChange = { password = it },
                                onSubmit = { submits++ }, onGoogle = { google++ },
                                onReset = { resets++ }, onSignUp = { signups++ })
                        }
                    }
                }
            }
        }
    }

    private fun contained(tag: String) {
        val node = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
        val root = compose.onNodeWithTag("fixture").getUnclippedBoundsInRoot()
        assertTrue("$tag clipped: $node in $root", node.top >= root.top && node.bottom <= root.bottom + 1.dp &&
            node.left >= root.left && node.right <= root.right + 1.dp)
    }

    private fun next() = compose.onNodeWithTag("login-submit").onChild().performClick()

    // External app files, so `adb pull /sdcard/Android/data/com.example.spotapp/files/login-review` reads them without run-as.
    private fun screenshot(name: String) {
        val bitmap = compose.onNodeWithTag("fixture").captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "login-review")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // BISU's seal sits to the right of the SPOT medallion, the same size and vertically centered with it.
    private fun bisuBesideSpot() {
        contained("login-bisu-logo")
        val spot = compose.onNodeWithTag("login-spot-logo").getUnclippedBoundsInRoot()
        val bisu = compose.onNodeWithTag("login-bisu-logo").getUnclippedBoundsInRoot()
        assertTrue("BISU logo not right of SPOT: $spot / $bisu", bisu.left >= spot.right)
        assertEquals(spot.bottom.value - spot.top.value, bisu.bottom.value - bisu.top.value, 0.5f)
        assertEquals((spot.top.value + spot.bottom.value) / 2, (bisu.top.value + bisu.bottom.value) / 2, 1f)
    }

    @Test fun phoneShowsBothFieldsAndActionsWithoutScroll() {
        show()
        listOf("login-email", "login-password", "login-submit", "login-reset", "login-google", "login-signup").forEach(::contained)
        bisuBesideSpot()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
        screenshot("login-phone")
    }

    @Test fun smallPhoneKeepsBothFieldsAndActionsVisible() {
        show(width = 320, height = 568)
        listOf("login-email", "login-password", "login-submit", "login-reset", "login-google", "login-signup").forEach(::contained)
        bisuBesideSpot()
        screenshot("login-small")
    }

    // The user's phone: 360 x 728 dp usable, with SPOT's text capped at 1.15.
    @Test fun testPhoneShowsBisuBesideSpotInLight() {
        show(height = 728, scale = 1.15f, dark = false)
        listOf("login-email", "login-password", "login-submit", "login-google", "login-signup").forEach(::contained)
        bisuBesideSpot()
        screenshot("login-test-phone-light")
    }

    // The BISU coin flips once as the screen opens; this catches it mid-turn, then checks it settles in place.
    @Test fun bisuSealFlipsLikeACoin() {
        compose.mainClock.autoAdvance = false
        show(height = 728, scale = 1.15f, dark = false)
        compose.mainClock.advanceTimeBy(150)
        screenshot("login-bisu-flipping-1")
        compose.mainClock.advanceTimeBy(150)
        screenshot("login-bisu-flipping-2")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        bisuBesideSpot()
    }

    @Test fun testPhoneShowsBisuBesideSpotInDark() {
        show(height = 728, scale = 1.15f, dark = true)
        bisuBesideSpot()
        screenshot("login-test-phone-dark")
    }

    @Test fun shortViewportValidatesAndPreservesEmail() {
        show(height = 340)
        next()
        compose.onNodeWithText("Enter a valid email address.").assertIsDisplayed()
        contained("login-submit")
        contained("login-google")
        compose.onNodeWithTag("login-email").performTextInput("student@example.com")
        next()
        contained("login-password")
        contained("login-reset")
        contained("login-submit")
        compose.onNodeWithTag("login-password").performTextInput("secret123")
        compose.onNodeWithContentDescription("Show password").performClick()
        compose.onNodeWithContentDescription("Hide password").assertIsDisplayed().performClick()
        screenshot("login-short-password")
        compose.onNodeWithTag("login-edit-email").performClick()
        compose.onNodeWithTag("login-email").assertTextContains("student@example.com")
        next()
        compose.onNodeWithTag("login-password").assertTextContains("secret123")
        next()
        assertEquals(1, submits)
    }

    @Test fun largeTextKeepsTheWholeLoginPathReachable() {
        show(width = 320, height = 568, scale = 1.5f)
        contained("login-email")
        contained("login-google")
        contained("login-signup")
        screenshot("login-large-email")
        compose.onNodeWithTag("login-email").performTextInput("student@example.com")
        next()
        listOf("login-password", "login-reset", "login-submit", "login-edit-email").forEach(::contained)
        screenshot("login-large-password")
        compose.onNodeWithTag("login-password").performTextInput("secret123")
        next()
        assertEquals(1, submits)
    }

    @Test fun keyboardNextMovesToPasswordAndDoneSubmits() {
        show()
        compose.onNodeWithTag("login-email").performTextInput("student@example.com")
        compose.onNodeWithTag("login-email").performImeAction()
        compose.onNodeWithTag("login-password").assertIsFocused().performTextInput("secret123")
        compose.onNodeWithTag("login-password").performImeAction()
        assertEquals(1, submits)
    }

    @Test fun allAlternativeActionsRemainConnected() {
        show()
        compose.onNodeWithTag("login-google").performClick()
        compose.onNodeWithTag("login-reset").performClick()
        compose.onNodeWithTag("login-signup").performClick()
        assertEquals(1, google)
        assertEquals(1, resets)
        assertEquals(1, signups)
    }

    @Test fun busyRequestsDisableCompetingActions() {
        show(busy = "google")
        listOf("login-email", "login-password", "login-reset", "login-google", "login-signup")
            .forEach { compose.onNodeWithTag(it).assertIsNotEnabled() }
        compose.onNodeWithTag("login-submit").onChild().assertIsNotEnabled()
    }

    @Test fun messagesFitWithPasswordAndRecoveryOnShortScreen() {
        show(height = 340, message = "Could not sign in. Check your connection and try again.")
        compose.onNodeWithTag("login-email").performTextInput("student@example.com")
        next()
        listOf("login-password", "login-message", "login-submit", "login-reset").forEach(::contained)
        screenshot("login-short-error")
    }

    @Test fun recreationRestoresEmailWithoutPersistingPassword() {
        show()
        compose.onNodeWithTag("login-email").performTextInput("student@example.com")
        compose.onNodeWithTag("login-password").performTextInput("secret123")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("login-email").assertTextContains("student@example.com")
        compose.onNodeWithTag("login-password").assertTextEquals("Password", "")
    }
}
