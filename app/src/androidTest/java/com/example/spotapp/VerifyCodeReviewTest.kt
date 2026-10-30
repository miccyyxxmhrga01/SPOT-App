package com.example.spotapp

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.SignupVerificationScreen
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Pictures of the sign-up code screen at the test phone's usable size (360 × 728 dp) and 1.15× text, in light and dark,
 * empty and with a code typed. Nothing is sent: the screen gets stand-in actions. `adb pull
 * /sdcard/Android/data/com.example.spotapp/files/verify-review` copies them without root.
 */
class VerifyCodeReviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var dark by mutableStateOf(false)
    private var code by mutableStateOf("")

    private fun save(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("review").captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "verify-review")
        directory.mkdirs()
        File(directory, "$name-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun codeScreenInLightAndDark() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme(dark = dark) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.requiredSize(360.dp, 728.dp).testTag("review")) {
                            SignupVerificationScreen("student@example.com", code = code, onCodeChange = { code = it },
                                secondsRemaining = 42, onCheck = {}, onResend = {}, onBack = {})
                        }
                    }
                }
            }
        }
        val verify = hasText("Verify email") and hasClickAction()
        for (mode in listOf(false, true)) {
            dark = mode
            code = ""
            compose.onNode(verify).assertIsNotEnabled()
            save("1-empty")
            code = "482913"
            compose.onNode(verify).assertIsEnabled()
            save("2-typed")
        }
    }
}
