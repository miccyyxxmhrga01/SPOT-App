package com.example.spotapp

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.SocialNotification
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.screens.home.*
import com.example.spotapp.ui.theme.screens.notification.NotificationGroup
import com.example.spotapp.ui.theme.screens.notification.NotificationRow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SocialExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun awake() { compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    private fun uri() = "android.resource://${InstrumentationRegistry.getInstrumentation().targetContext.packageName}/${R.raw.dance}"
    private fun screenshot(tag: String, file: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"social-review").apply { mkdirs() }
        File(directory,"$file.png").outputStream().use { compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun onePlayerHandsOffBetweenVideosAndStopsWhenNoneAreActive() {
        var selected by mutableStateOf(0)
        lateinit var controller: SpotVideoController
        val url = uri()
        compose.setContent { SPOTAppTheme { ProvideSpotVideo {
            controller = LocalSpotVideo.current!!
            Column { SpotVideo(url, selected == 0, Modifier.fillMaxWidth().height(180.dp)); SpotVideo(url, selected == 1, Modifier.fillMaxWidth().height(180.dp)) }
        } } }
        fun playing(): Boolean { var p=false; InstrumentationRegistry.getInstrumentation().runOnMainSync { p=controller.player.isPlaying && controller.player.currentPosition > 0 }; return p }
        compose.waitUntil(25_000) { playing() }
        val player = controller.player
        var oldOwner: Any? = null
        compose.runOnIdle { oldOwner = controller.owner; selected = 1 }
        compose.waitUntil(10_000) { playing() }
        compose.runOnIdle { assertSame(player, controller.player); assertNotSame(oldOwner, controller.owner) }
        compose.onAllNodesWithTag("active-social-video").assertCountEquals(1)
        compose.runOnIdle { selected = -1 }
        compose.waitUntil(5_000) { !playing() }
        compose.onAllNodesWithTag("active-social-video").assertCountEquals(0)
    }
    @Test fun playerPausesInBackgroundAndResumesOnlyWithAnActiveOwner() {
        lateinit var controller: SpotVideoController
        val url = uri()
        compose.setContent { SPOTAppTheme { ProvideSpotVideo { controller=LocalSpotVideo.current!!; SpotVideo(url,true,Modifier.fillMaxSize()) } } }
        fun playing(): Boolean { var p=false; InstrumentationRegistry.getInstrumentation().runOnMainSync { p=controller.player.isPlaying }; return p }
        compose.waitUntil(25_000) { playing() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.waitUntil(5_000) { !playing() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) { playing() }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun commentDraftSurvivesSendFailureAndClearsOnlyAfterConfirmation() {
        var sending by mutableStateOf(false)
        var revision by mutableIntStateOf(0)
        var error by mutableStateOf("")
        var sent = ""
        compose.setContent { SPOTAppTheme {
            HomeCommentsSheet(rememberModalBottomSheetState(skipPartiallyExpanded=true), emptyList(), null, {},
                { sent=it; sending=true }, {}, sending=sending, error=error, sendRevision=revision, managedSend=true)
        } }
        compose.onNode(hasSetTextAction()).performClick().performTextInput("A thoughtful campus comment")
        compose.onNodeWithContentDescription("Post comment").assertIsDisplayed()
        screenshot("connected-comments","comments-keyboard")
        compose.onNodeWithContentDescription("Post comment").performClick()
        compose.runOnIdle { assertEquals("A thoughtful campus comment",sent);sending=false;error="Please retry" }
        compose.onNodeWithText("A thoughtful campus comment").assertIsDisplayed()
        compose.onNodeWithContentDescription("Post comment").assertIsDisplayed().performClick()
        compose.runOnIdle { sending=false;error="";revision++ }
        assertEquals("", compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }
    @Test fun notificationOpensItsEventAndOffersPersistentReadStateAction() {
        var opened=false;var changed=false
        val item=SocialNotification("n","comment","creator","Sam Cruz","","p","commented on your post",System.currentTimeMillis(),false)
        compose.setContent { SPOTAppTheme { NotificationRow(NotificationGroup(listOf(item)),onOpen={opened=true},onToggleRead={changed=true}) } }
        compose.onNodeWithText("commented on your post", substring = true).performClick()
        assertTrue(opened)
        // Mark as read sits behind a long-press, which screen readers offer as "Notification options".
        compose.onNodeWithText("commented on your post", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText("Mark as read").performClick()
        assertTrue(changed)
    }
}
