package com.example.spotapp

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.AccountType
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.DiscoverSections
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class SpotterNavigationExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val creator = SpotUiProfile("review-creator", "Sam Cruz", "samcruz", "Dancing", "BSCS", accountType = AccountType.STUDENT)
    @Before fun awake() { compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    private fun capture(name: String) {
        compose.waitForIdle()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "navigation-review").apply { mkdirs() }
        val b = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    // Invitations moved from Notifications to Messages, under Requests (see MessagesExperienceTest).
    @Test fun discoverShowsSectionsWithoutOpportunities() {
        compose.setContent { SPOTAppTheme {
            var selected by remember { mutableStateOf("Explore") }
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Text("Discover", Modifier.padding(18.dp))
                DiscoverSections(selected) { selected = it }
                Text("Showing $selected", Modifier.padding(18.dp))
            }
        } }
        listOf("Explore", "Events", "Collabs", "Groups").forEach {
            compose.onNodeWithTag("section-$it").assertIsDisplayed()
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithTag("section-Opportunities").assertDoesNotExist()
        capture("discover-sections")
        listOf("Events", "Collabs", "Groups", "Explore").forEach {
            compose.onNodeWithTag("section-$it").performClick().assertIsSelected()
            compose.onNodeWithText("Showing $it").assertIsDisplayed()
        }
    }
}
