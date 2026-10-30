package com.example.spotapp

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.SignupVerificationScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class SignupLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun account(width: Int = 360, height: Int = 720, scale: Float = 1f, error: String = "") {
        compose.setContent {
            val actualDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(actualDensity, scale)) {
                SPOTAppTheme {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.requiredSize(width.dp, height.dp).testTag("fixture")) {
                            var email by remember { mutableStateOf("") }
                            var password by remember { mutableStateOf("") }
                            var agreed by remember { mutableStateOf(false) }
                            SignupAccountScreen(email, password, agreed, message = error,
                                onEmailChange = { email = it }, onPasswordChange = { password = it },
                                onAgreementChange = { agreed = it }, onSubmit = {}, onLogin = {})
                        }
                    }
                }
            }
        }
    }

    private fun assertContained(tag: String) {
        compose.onNodeWithTag(tag).assertIsDisplayed()
        val root = compose.onNodeWithTag("fixture").getUnclippedBoundsInRoot()
        val node = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue("$tag extends below screen: $node / $root", node.bottom <= root.bottom + 1.dp)
        assertTrue("$tag extends above screen: $node / $root", node.top >= root.top - 1.dp)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("fixture").captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "signup-review")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun phoneHasTwoFieldsAndNoScroll() {
        account()
        screenshot("signup-phone")
        assertContained("signup-email")
        assertContained("signup-password")
        assertContained("signup-agreement")
        assertContained("signup-submit")
        assertContained("signup-signin")
        compose.onNodeWithContentDescription("Back to sign in").assertDoesNotExist()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
        compose.onNodeWithText("Full name").assertDoesNotExist()
        compose.onNodeWithText("Confirm password").assertDoesNotExist()
    }

    @Test fun smallPhoneKeepsActionsVisible() {
        account(width = 320, height = 568)
        screenshot("signup-small-phone")
        assertContained("signup-submit")
        assertContained("signup-password")
    }

    @Test fun largeTextKeepsActionsVisible() {
        account(scale = 1.5f)
        screenshot("signup-large-text")
        assertContained("signup-submit")
    }

    @Test fun shortViewportProgressesWithoutLosingEmail() {
        account(height = 340)
        compose.onNodeWithTag("signup-password").assertDoesNotExist()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Enter a valid email address.").assertIsDisplayed()
        compose.onNodeWithTag("signup-email").performTextInput("student@example.com")
        compose.onNodeWithText("Continue").performClick()
        assertContained("signup-password")
        assertContained("signup-agreement")
        assertContained("signup-submit")
        screenshot("signup-short-password")
        assertContained("signup-signin")
        compose.onNodeWithText("Edit email").performClick()
        compose.onNodeWithTag("signup-email").assertTextContains("student@example.com")
    }

    @Test fun errorAndActionFitTogether() {
        account(height = 480, error = "Profile setup is temporarily unavailable. Please try again later.")
        compose.onNodeWithText("Profile setup is temporarily unavailable. Please try again later.").assertIsDisplayed()
        assertContained("signup-submit")
        screenshot("signup-error")
    }

    @Test fun passwordCanBeRevealedAndHidden() {
        account()
        compose.onNodeWithContentDescription("Show password").performClick()
        compose.onNodeWithContentDescription("Hide password").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Show password").assertIsDisplayed()
        // Tap the checkbox, since the row's center can fall on a policy link at different font sizes.
        compose.onNodeWithTag("signup-agreement").assertIsOff()
            .performTouchInput { click(Offset(10.dp.toPx(), center.y)) }.assertIsOn()
    }

    @Test fun verificationFitsShortViewport() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
              SPOTAppTheme {
                Box(Modifier.fillMaxSize()) {
                Box(Modifier.requiredSize(360.dp, 340.dp).testTag("fixture")) {
                    SignupVerificationScreen("student@example.com", secondsRemaining = 42,
                        onCheck = {}, onResend = {}, onBack = {})
                }
                }
              }
            }
        }
        assertContained("verify-submit")
        compose.onNodeWithText("Resend in 42s").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
        screenshot("verify-short")
    }

    @Test fun policyReaderCanBeDismissed() {
        var closed = false
        compose.setContent { SPOTAppTheme { SignupPolicyDialog(SignupPolicy.PRIVACY) { closed = true } } }
        compose.onNodeWithText("Privacy Policy").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        assertTrue(closed)
    }

    @Test fun tallPhoneKeepsActionsVisible() {
        account(height = 752)
        screenshot("signup-tall-phone")
        assertContained("signup-submit")
        assertContained("signup-agreement")
    }

    @Test fun policyLinksDoNotAcceptAgreement() {
        account()
        for ((link, title) in listOf("Terms" to "Terms of Service", "Privacy Policy" to "Privacy Policy")) {
            val text = compose.onNodeWithText("I'm 16+", substring = true, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.first()
            val offset = layout.layoutInput.text.text.indexOf(link)
            val position = layout.getBoundingBox(offset).center
            text.performTouchInput { click(position) }
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithTag("signup-agreement").assertIsOff()
        }
    }
}
