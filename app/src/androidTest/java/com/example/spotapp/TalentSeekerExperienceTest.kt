package com.example.spotapp

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.AccountType
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.seeker.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class TalentSeekerExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val seeker = SpotUiProfile("review-seeker", "Campus Events", "campusevents", "", "", accountType = AccountType.TALENT_SEEKER,
        organization = "University arts council", seekingTalents = listOf("Dancing", "Singing"))
    private val creator = SpotUiProfile("review-creator", "Sam Cruz", "samcruz", "Dancing · Choreography", "BS in Computer Science",
        accountType = AccountType.STUDENT, bio = "Bringing campus stories to life through dance.")
    @Before fun awake() { compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "talent-review").apply { mkdirs() }
        val b = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun seekerActionsAndCreatorCardAreUsableAtDeviceFontScale() {
        val destinations = mutableListOf<String>(); var viewed = 0; var invited = 0
        compose.setContent { SPOTAppTheme { Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            SpotterShortcuts(seeker) { destinations.add(it) }
            TalentCreatorCard(creator, { viewed++ }, { invited++ }, Modifier.padding(16.dp))
        } } }
        listOf("Find creators", "Saved work", "My events").forEach { compose.onNodeWithText(it).performScrollTo().performClick() }
        compose.onNodeWithText("View profile").performScrollTo().performClick()
        compose.onNodeWithText("Invite").performClick()
        compose.runOnIdle { assertEquals(listOf("talent","saved","events"), destinations); assertEquals(1, viewed); assertEquals(1, invited) }
        compose.onNodeWithTag("spotter-shortcuts").performScrollTo(); screenshot("seeker-discover-shortcuts")
    }
    @Test fun creatorDoesNotSeeSpotterShortcuts() {
        val destinations = mutableListOf<String>()
        compose.setContent { SPOTAppTheme { Column(Modifier.fillMaxSize().statusBarsPadding()) { SpotterShortcuts(creator) { destinations.add(it) } } } }
        compose.onNodeWithText("Find creators").assertDoesNotExist(); compose.onNodeWithText("My events").assertDoesNotExist()
        compose.onNodeWithText("Opportunities").assertDoesNotExist(); compose.onNodeWithText("Requests").assertDoesNotExist(); compose.onNodeWithTag("spotter-shortcuts").assertDoesNotExist()
        compose.runOnIdle { assertTrue(destinations.isEmpty()) }; screenshot("creator-discover-shortcuts")
    }
}
