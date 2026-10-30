package com.example.spotapp

import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.AccountType
import com.example.spotapp.data.ProfileDetails
import com.example.spotapp.data.Supabase
import com.example.spotapp.data.awaitSession
import com.example.spotapp.ui.theme.SPOTAppTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProfileSetupFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var saved: ProfileDetails? = null
    private var savedPhoto: Uri? = null
    private val restore by lazy { StateRestorationTester(compose) }

    @Before fun keepTestActivityVisible() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    private fun show(height: Int = 720, width: Int = 360, scale: Float = 1f) {
        restore.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                SPOTAppTheme {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.requiredSize(width.dp, height.dp).testTag("fixture")) {
                            ProfileSetupFlow(onExit = {}, onSave = { profile, photo -> saved = profile; savedPhoto = photo })
                        }
                    }
                }
            }
        }
    }

    private fun next() = compose.onNodeWithTag("profile-submit").onChild().performClick()

    private fun choose(type: AccountType) {
        compose.onNodeWithTag("role-${type.storageValue}").performClick()
        next()
    }

    private fun identity() {
        compose.onNodeWithTag("profile-name").performTextInput("Alex Santos")
        if (compose.onAllNodesWithTag("profile-username").fetchSemanticsNodes().isEmpty()) next()
        compose.onNodeWithTag("profile-username").performTextInput("alex_santos")
        next()
    }

    // Courses are tiles on the page itself, labelled without "Bachelor of Science in"; the full name is what's saved.
    private fun course(value: String = ProfileCourseOptions[1]) {
        compose.onNodeWithText(profileCourseLabel(value)).performScrollTo().performClick()
    }

    private fun contained(tag: String) {
        // Questions may scroll on short screens; the action button is pinned and has no scroll parent.
        try { compose.onNodeWithTag(tag).performScrollTo() } catch (_: AssertionError) {}
        compose.onNodeWithTag(tag).assertIsDisplayed()
        val root = compose.onNodeWithTag("fixture").getUnclippedBoundsInRoot()
        val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue("$tag clipped: $bounds in $root", bounds.top >= root.top && bounds.bottom <= root.bottom + 1.dp)
        assertTrue("$tag clipped horizontally: $bounds in $root", bounds.left >= root.left && bounds.right <= root.right + 1.dp)
    }

    private fun roleLabelFits(type: AccountType) {
        val node = compose.onNodeWithText(profileRoleTitle(type), useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        // Paragraph width can include unused layout space; check the rendered lines.
        assertTrue("${profileRoleTitle(type)} text clipped",
            layouts.isNotEmpty() && layouts.all {
                !it.multiParagraph.didExceedMaxLines && (0 until it.lineCount).all { line ->
                    it.getLineLeft(line) >= -1f && it.getLineRight(line) <= it.size.width + 1f &&
                        it.getLineBottom(line) <= it.size.height + 1f
                }
            })
        val label = node.getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag("role-${type.storageValue}").getUnclippedBoundsInRoot()
        assertTrue("${profileRoleTitle(type)} outside its card", label.top >= card.top && label.bottom <= card.bottom)
    }

    private fun screenshot(name: String) {
        val bitmap = compose.onNodeWithTag("fixture").captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "profile-review")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun roleChoiceIsRequiredAndDoesNotScroll() {
        show()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithTag("role-student").performClick().assertIsSelected()
        compose.onNodeWithText("Continue").assertIsEnabled()
        screenshot("profile-role")
    }

    @Test fun seekerCompletesWithNoCourseTalentsPhotoOrBio() {
        show()
        choose(AccountType.TALENT_SEEKER)
        identity()
        compose.onNodeWithTag("profile-course").assertDoesNotExist()
        compose.onNodeWithTag("profile-organization").assertIsDisplayed()
        screenshot("profile-seeker")
        next()
        compose.onNodeWithTag("profile-bio").assertIsDisplayed()
        screenshot("profile-about")
        next()
        assertEquals(AccountType.TALENT_SEEKER, saved?.accountType)
        assertEquals("", saved?.course)
        assertEquals("", saved?.bio)
        assertNull(savedPhoto)
    }

    @Test fun studentMustSupplyCourseAndTalentBeforeFinishing() {
        show()
        choose(AccountType.STUDENT)
        identity()
        next()
        compose.onNodeWithText("Choose your course.").assertIsDisplayed()
        course()
        next()
        compose.onNodeWithText("Choose at least one talent.").assertIsDisplayed()
        // On a 720dp-tall phone the first talents are one-tap chips.
        compose.onNodeWithText("Singing").performClick()
        screenshot("profile-student")
        next()
        next()
        assertEquals(AccountType.STUDENT, saved?.accountType)
        assertEquals(listOf("Singing"), saved?.talents)
        assertEquals(ProfileCourseOptions[1], saved?.course)
    }

    @Test fun choiceAndIdentitySurviveRecreation() {
        show()
        choose(AccountType.TALENT_SEEKER)
        compose.onNodeWithTag("profile-name").performTextInput("Alex")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("profile-name").assertTextContains("Alex")
        compose.onNodeWithContentDescription("Previous profile step").performClick()
        compose.onNodeWithTag("role-talent_seeker").assertIsSelected()
    }

    @Test fun shortViewportKeepsIdentityAndActionsReachable() {
        show(height = 340)
        screenshot("profile-role-short")
        contained("role-student")
        contained("role-talent_seeker")
        contained("profile-submit")
        choose(AccountType.TALENT_SEEKER)
        compose.onNodeWithTag("profile-name").performTextInput("Alex")
        next()
        contained("profile-username")
        contained("profile-submit")
        screenshot("profile-identity-short")
        compose.onNodeWithTag("profile-username").performTextInput("alex_santos")
        next()
        contained("profile-organization")
        contained("profile-submit")
        next()
        contained("profile-bio")
        contained("profile-submit")
        next()
        assertEquals(AccountType.TALENT_SEEKER, saved?.accountType)
    }

    @Test fun largeTextAndSmallScreenStillFit() {
        show(height = 568, width = 320, scale = 1.5f)
        screenshot("profile-large-text")
        contained("role-student")
        contained("role-talent_seeker")
        contained("profile-submit")
        roleLabelFits(AccountType.STUDENT)
        roleLabelFits(AccountType.TALENT_SEEKER)
        choose(AccountType.STUDENT)
        contained("profile-name")
        contained("profile-submit")
        screenshot("profile-large-identity")
        identity()
        next()
        compose.onNodeWithText("Choose your course.").assertIsDisplayed()
        contained("profile-course")
        contained("profile-submit")
        screenshot("profile-large-student")
        course(ProfileCourseOptions[4])
        contained("profile-course")
        contained("profile-submit")
        screenshot("profile-large-selected-course")
        compose.onNodeWithText("Singing").performScrollTo().performClick()
        next()
        contained("profile-bio")
        contained("profile-submit")
        screenshot("profile-large-about")
        next()
        assertEquals(AccountType.STUDENT, saved?.accountType)
    }

    @Test fun changingRolesPreservesIdentityButUsesCorrectQuestions() {
        show()
        choose(AccountType.STUDENT)
        identity()
        course()
        compose.onNodeWithContentDescription("Previous profile step").performClick()
        compose.onNodeWithContentDescription("Previous profile step").performClick()
        choose(AccountType.TALENT_SEEKER)
        compose.onNodeWithTag("profile-name").assertTextContains("Alex Santos")
        next()
        compose.onNodeWithTag("profile-course").assertDoesNotExist()
        compose.onNodeWithTag("profile-organization").assertIsDisplayed()
        next()
        next()
        assertEquals("", saved?.firestoreFields()?.get("course"))
    }

    @Test fun allSixFixedCoursesCanBeSelectedAndSelectionSurvivesRecreation() {
        show()
        choose(AccountType.STUDENT)
        identity()
        compose.onNodeWithTag("profile-course").assert(
            SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
        assertEquals(6, ProfileCourseOptions.size)
        for (option in ProfileCourseOptions) {
            course(option)
            compose.onNodeWithText(profileCourseLabel(option)).assertIsSelected()
            // Picking a course never opens a pop-up.
            compose.onAllNodes(isDialog()).assertCountEquals(0)
            contained("profile-course")
            contained("profile-submit")
        }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(profileCourseLabel(ProfileCourseOptions.last())).assertIsSelected()
        compose.onNodeWithContentDescription("Previous profile step").performClick()
        next()
        compose.onNodeWithText(profileCourseLabel(ProfileCourseOptions.last())).assertIsSelected()
        screenshot("profile-fixed-course")
    }

    @Test fun finishingSetupOpensExistingHomeAndNavigation() {
        // The real home screen needs the device's saved sign-in. The app waits for it to be restored
        // before showing home, so the test does too, and skips on a phone that isn't signed in.
        runBlocking { withTimeout(20_000) { awaitSession() } }
        assumeTrue("Sign in to SPOT on this device to run this test.", Supabase.uid() != null)
        compose.setContent {
            SPOTAppTheme {
                var completed by remember { mutableStateOf(false) }
                if (completed) HomeScreen(onLogout = {})
                else ProfileSetupFlow(onExit = {}, onSave = { _, _ -> completed = true })
            }
        }
        choose(AccountType.TALENT_SEEKER)
        identity()
        next()
        next()
        compose.onNodeWithContentDescription("Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Discover").assertIsDisplayed().performClick()
        // Discover opens on its Explore tab; the tabs are icons named for accessibility.
        compose.onNodeWithTag("section-Explore").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithText("SPOT").assertIsDisplayed()
    }
}
