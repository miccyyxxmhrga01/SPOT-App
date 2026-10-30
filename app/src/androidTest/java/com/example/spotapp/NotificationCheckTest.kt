package com.example.spotapp

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.PushNotifications
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.SpotMessageBanner
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Checks on a real phone how a new message is announced. Run one test at a time with `am instrument -e class ...#name`:
 * [phoneNotificationWhileSpotIsClosed] shows a test notification for six seconds (watch or screenshot the pop-up, then
 * read `dumpsys notification` for mSoundNotificationKey), and [bannerWhileSpotIsOpen] saves pictures of the in-app
 * banner to the app's external files, in `banner-review`.
 */
class NotificationCheckTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun phoneNotificationWhileSpotIsClosed() {
        assertTrue("Notifications are off for SPOT on this phone.", PushNotifications.canNotify(context))
        PushNotifications.appVisible = false
        PushNotifications.deliver(context, TEST_TAG, "SPOT test", "Checking that new messages pop up with a sound.")
        Thread.sleep(6_000)
        NotificationManagerCompat.from(context).cancel(TEST_TAG, 1)
    }

    @Test fun bannerWhileSpotIsOpen() {
        var dark by mutableStateOf(false)
        compose.setContent {
            SPOTAppTheme(dark = dark) {
                Box(Modifier.requiredSize(360.dp, 140.dp).testTag("banner-frame")) { SpotMessageBanner(Modifier.fillMaxSize()) }
            }
        }
        PushNotifications.appVisible = true
        for (theme in listOf(false, true)) {
            dark = theme
            compose.waitForIdle()
            // While SPOT is open, a new message goes to the banner instead of the phone's notification shade.
            PushNotifications.deliver(context, TEST_TAG, "Laira Angela", "Are you coming to the jam session later?")
            compose.mainClock.advanceTimeBy(800)
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag("banner-frame").captureToImage().asAndroidBitmap()
            val folder = File(context.getExternalFilesDir(null), "banner-review").apply { mkdirs() }
            File(folder, "banner-${if (theme) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.mainClock.advanceTimeBy(6_000)
        }
        PushNotifications.appVisible = false
    }

    private companion object { const val TEST_TAG = "spot-notification-check" }
}
