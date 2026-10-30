package com.example.spotapp

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.SpotScreenHeader
import com.example.spotapp.ui.theme.screens.settings.DeleteAccountPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Pictures of Settings › Delete account at the test phone's usable size (360 × 728 dp) and 1.15× text, in light and dark,
 * for password and Google-only accounts, plus the last yes/no. The page gets a stand-in delete and the test never confirms,
 * so nothing reaches the account signed in on the phone. `adb pull /sdcard/Android/data/com.example.spotapp/files/delete-review`
 * copies them without root.
 */
class DeleteAccountReviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var dark by mutableStateOf(false)
    private var hasPassword by mutableStateOf(true)
    private var deleteCalls = 0

    private val directory get() = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "delete-review")
        .apply { mkdirs() }

    private fun write(name: String, bitmap: Bitmap) =
        File(directory, "$name-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    private fun save(name: String) {
        compose.waitForIdle()
        write(name, compose.onNodeWithTag("review").captureToImage().asAndroidBitmap())
    }

    // Pop-ups are separate windows, so the whole screen is captured.
    private fun saveScreen(name: String) {
        compose.waitForIdle()
        write(name, InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
    }

    @Test fun deletePageInLightAndDark() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme(dark = dark) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.requiredSize(360.dp, 728.dp).background(SpotPalette.Ivory).testTag("review")) {
                            key(dark, hasPassword) {
                                SpotScreenHeader("Delete account", onBack = {})
                                DeleteAccountPage(hasPassword, "alexsantos", busy = false, onBusy = {}) { _, _ -> deleteCalls++ }
                            }
                        }
                    }
                }
            }
        }
        for (mode in listOf(false, true)) {
            dark = mode
            hasPassword = true
            compose.onNodeWithTag("delete-account-button").assertIsNotEnabled()
            save("1-password")
            compose.onNodeWithTag("delete-password").performTextInput("not-a-real-password")
            compose.onNodeWithTag("delete-account-button").assertIsEnabled()
            save("2-password-typed")
            compose.onNodeWithTag("delete-account-button").performClick()
            saveScreen("3-confirm")
            compose.onNodeWithText("Cancel").performClick()
            hasPassword = false
            save("4-google")
            compose.onNodeWithTag("delete-type").performTextInput("delete")
            compose.onNodeWithTag("delete-account-button").assertIsEnabled()
            save("5-google-typed")
        }
        assertEquals("the review never deletes anything", 0, deleteCalls)
    }
}
