package com.example.spotapp

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.AccountType
import com.example.spotapp.ui.theme.SPOTAppTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Pictures of every profile-setup step at the test phone's usable size (360 × 728 dp) and SPOT's largest text (1.15×),
 * in light and dark, for reviewing the design. They're saved under the app's external files, so
 * `adb pull /sdcard/Android/data/com.example.spotapp/files/setup-review` copies them without root.
 */
class ProfileSetupReviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private data class Shot(val dark: Boolean, val step: Int, val role: AccountType?)
    private var shot by mutableStateOf(Shot(false, 0, null))

    private fun capture(name: String, next: Shot) {
        shot = next
        save(name, next.dark)
    }

    private fun save(name: String, dark: Boolean) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("review").captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "setup-review")
        directory.mkdirs()
        File(directory, "$name-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun everyStepInLightAndDark() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme(dark = shot.dark) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.requiredSize(360.dp, 728.dp).testTag("review")) {
                            // A fresh flow for each picture, opened at that step.
                            key(shot) {
                                ProfileSetupFlow("Alex Santos", onExit = {}, onSave = { _, _ -> }, startStep = shot.step,
                                    startRole = shot.role, startUsername = if (shot.step > 0) "alexsantos" else "")
                            }
                        }
                    }
                }
            }
        }
        for (dark in listOf(false, true)) {
            capture("1-role", Shot(dark, 0, null))
            capture("2-name", Shot(dark, 1, AccountType.STUDENT))
            capture("3-course-talents", Shot(dark, 2, AccountType.STUDENT))
            compose.onNodeWithText(profileCourseLabel(ProfileCourseOptions[1])).performClick()
            compose.onNodeWithText("Guitar").performClick()
            compose.onNodeWithText("Singing").performClick()
            save("3-selected", dark)
            capture("3-looking-for", Shot(dark, 2, AccountType.TALENT_SEEKER))
            capture("4-photo-bio", Shot(dark, 3, AccountType.STUDENT))
        }
    }
}
