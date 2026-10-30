package com.example.spotapp.ui.theme.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.PI

internal val LocalSpotAmbientPhase = staticCompositionLocalOf<State<Float>?> { null }

@Composable
private fun rememberAmbientPhase(): State<Float> {
    val phase = remember { Animatable(0f) }
    var offset by remember { mutableFloatStateOf(0f) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(running) {
        if (running) {
            offset = (offset + phase.value) % (2f * PI.toFloat())
            phase.snapTo(0f)
            phase.animateTo(2f * PI.toFloat(), infiniteRepeatable(tween(26_000, easing = LinearEasing)))
        }
    }
    return remember { derivedStateOf { offset + phase.value } }
}

@Composable
internal fun ProvideSpotAmbientMotion(content: @Composable () -> Unit) {
    val phase = LocalSpotAmbientPhase.current ?: rememberAmbientPhase()
    CompositionLocalProvider(LocalSpotAmbientPhase provides phase, content = content)
}

@Composable
internal fun spotAmbientPhase(): State<Float> = LocalSpotAmbientPhase.current ?: rememberAmbientPhase()

/** A shared phase and draw-only state reads keep feed recompositions out of the animation.
 * Full intensity matches the login's [AnimatedLoginBackground], so every screen shares one look. */
fun Modifier.spotAmbientBackground(base: Color, intensity: Float = 1f): Modifier = composed {
    val phase = spotAmbientPhase()
    val enabled = LocalSpotVideoEnabled.current
    drawBehind { drawSpotAmbient(if (enabled) phase.value else 0f, base, intensity.coerceIn(0f, 1f)) }
}
