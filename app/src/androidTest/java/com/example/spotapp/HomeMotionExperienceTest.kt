package com.example.spotapp

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.home.PopularPostsCarousel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class HomeMotionExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun awake() { compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    private fun step(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun page() = compose.onNodeWithTag("popular-carousel").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
    @Test fun carouselAdvancesOnlyOnScreenAndRespectsOverlays() {
        val source = "android.resource://${InstrumentationRegistry.getInstrumentation().targetContext.packageName}/${R.drawable.image1}"
        val person = SpotUiProfile("motion", "Campus creator", "creator", "Dancing", "BSIT")
        val posts = (1..2).map { PublishedPost("motion-$it", person, "Spotlight $it", listOf("Dancing"), emptyList(), listOf(PublishedMedia(source, "image/jpeg")), "public") }
        var enabled by mutableStateOf(true)
        lateinit var scroll: androidx.compose.foundation.ScrollState
        compose.mainClock.autoAdvance = false
        compose.setContent { SPOTAppTheme {
            scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().spotAmbientBackground(Color.White).verticalScroll(scroll)) {
                Spacer(Modifier.height(24.dp))
                PopularPostsCarousel(posts, false, "", {}, {}, playbackEnabled = enabled, autoAdvanceMillis = 1_000)
                Spacer(Modifier.height(1600.dp))
            }
        } }
        step(100)
        assertEquals("Popular 1 of 2", page())
        step(1800)
        assertEquals("Popular 2 of 2", page())
        compose.runOnUiThread { enabled = false }; step(3500)
        assertEquals("Popular 2 of 2", page())
        compose.runOnUiThread { runBlocking { scroll.scrollTo(1100) }; enabled = true }
        step(3500); assertEquals("Popular 2 of 2", page())
        compose.runOnUiThread { runBlocking { scroll.scrollTo(0) } }
        step(1800); assertEquals("Popular 1 of 2", page())
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "story-editor-review").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "home-motion.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun ambientMotionFreezesInBackgroundAndResumesFromItsPosition() {
        val owner = object : LifecycleOwner { val registry = LifecycleRegistry(this); override val lifecycle get() = registry }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        var phase: State<Float>? = null
        compose.mainClock.autoAdvance = false
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) { SPOTAppTheme {
            phase = LocalSpotAmbientPhase.current
            Box(Modifier.fillMaxSize().spotAmbientBackground(Color.White))
        } } }
        step(100); val first = phase!!.value
        step(700); assertTrue(phase!!.value > first)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        step(100); val paused = phase!!.value
        step(900); assertEquals(paused, phase!!.value, .0001f)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        step(700); assertTrue(phase!!.value > paused)
    }
}
