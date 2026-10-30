package com.example.spotapp.ui.theme.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.example.spotapp.R
import kotlinx.coroutines.launch

/** Light from the top left: a pale gold edge fading to a deep gold one, like a beveled coin rim. */
private val CoinRim get() = Brush.linearGradient(listOf(Color(0xFFFFF1B8), SpotPalette.GoldBright, SpotPalette.GoldDeep, Color(0xFF8A6414)))

/**
 * Bohol Island State University's seal as a 3D gold coin beside the SPOT medallion: a beveled gold rim, domed shading
 * and a glossy highlight over the unchanged seal, with a soft shadow. With [animated], it flips once like a coin when
 * it appears (its gold back shows mid-turn), and tilts under a finger that presses and drags on it, springing back
 * flat on release.
 */
@Composable
fun BisuSeal(size: Dp, modifier: Modifier = Modifier, animated: Boolean = true) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Degrees around the vertical axis; one full turn that overshoots a little and settles.
    val flip = remember { Animatable(if (animated) -360f else 0f) }
    LaunchedEffect(animated) { if (animated) flip.animateTo(0f, spring(dampingRatio = .62f, stiffness = 26f)) }
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    val turn = ((flip.value % 360f) + 360f) % 360f
    val showingBack = turn in 90f..270f
    Box(modifier.size(size)
        .graphicsLayer {
            rotationY = flip.value + tiltY
            rotationX = tiltX
            cameraDistance = 14f * density.density
        }
        .then(if (!animated) Modifier else Modifier.pointerInput(Unit) {
            // Pressing tilts the coin toward the finger, up to 16°; it doesn't consume, so nothing else is blocked.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun tiltTo(at: Offset) {
                    tiltY = ((at.x / this.size.width) * 2f - 1f).coerceIn(-1f, 1f) * 16f
                    tiltX = -((at.y / this.size.height) * 2f - 1f).coerceIn(-1f, 1f) * 16f
                }
                tiltTo(down.position)
                do {
                    val event = awaitPointerEvent()
                    event.changes.firstOrNull { it.pressed }?.let { tiltTo(it.position) }
                } while (event.changes.any { it.pressed })
                scope.launch {
                    val fromX = tiltX
                    launch { animate(fromX, 0f, animationSpec = spring(dampingRatio = .45f, stiffness = Spring.StiffnessLow)) { v, _ -> tiltX = v } }
                    animate(tiltY, 0f, animationSpec = spring(dampingRatio = .45f, stiffness = Spring.StiffnessLow)) { v, _ -> tiltY = v }
                }
            }
        }), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize()
            .shadow(size * .12f, CircleShape, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow)
            .background(CoinRim, CircleShape)
            .padding(size * .045f)
            .clip(CircleShape)) {
            if (showingBack) Canvas(Modifier.matchParentSize()) {
                // The coin's back: polished gold with an engraved ring.
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A6), SpotPalette.GoldDeep), center = Offset(this.size.width * .35f,
                    this.size.height * .3f), radius = this.size.width * .8f))
                drawCircle(Color(0xFF8A6414).copy(alpha = .45f), radius = this.size.minDimension * .36f, style = Stroke(this.size.minDimension * .03f))
            } else {
                Image(painterResource(R.drawable.bisu_logo), "Bohol Island State University logo", Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop)
                Canvas(Modifier.matchParentSize()) {
                    val radius = this.size.minDimension / 2f
                    // A dome: the face darkens toward its edge.
                    drawCircle(Brush.radialGradient(0f to Color.Transparent, .68f to Color.Transparent, 1f to Color.Black.copy(alpha = .3f),
                        center = center, radius = radius))
                    // Gloss where the light falls, top left, like enamel.
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .42f), Color.Transparent),
                        center = Offset(this.size.width * .3f, this.size.height * .24f), radius = radius * 1.05f))
                }
            }
        }
        // A bright bevel catching the light along the upper-left rim.
        Canvas(Modifier.matchParentSize()) {
            val inset = this.size.minDimension * .022f
            drawArc(Color.White.copy(alpha = .6f), startAngle = 190f, sweepAngle = 100f, useCenter = false,
                topLeft = Offset(inset, inset), size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
                style = Stroke(this.size.minDimension * .014f))
        }
    }
}
