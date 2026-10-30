package com.example.spotapp.ui.theme.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The double-tap like, as on Instagram: a white heart pops over the post inside a fading gold ring, then shrinks away.
 * Each new [trigger] plays it once; 0 shows nothing.
 */
@Composable
fun DoubleTapHeart(trigger: Int, modifier: Modifier = Modifier, size: Dp = 104.dp) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        alpha.snapTo(1f); scale.snapTo(.3f); ring.snapTo(0f)
        launch { ring.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }
        scale.animateTo(1f, spring(dampingRatio = .42f, stiffness = 420f))
        delay(380)
        launch { scale.animateTo(.6f, tween(220)) }
        alpha.animateTo(0f, tween(220))
    }
    if (alpha.value > 0f) Box(modifier.size(size * 1.7f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val progress = ring.value
            drawCircle(SpotPalette.GoldBright.copy(alpha = (1f - progress) * .9f * alpha.value),
                radius = this.size.minDimension / 2f * (.35f + .65f * progress),
                style = Stroke(width = 5.dp.toPx() * (1f - progress) + 1.dp.toPx()))
        }
        // A soft dark copy under the white heart keeps it visible on bright photos.
        Icon(Icons.Rounded.Favorite, null, Modifier.size(size).offset(y = 2.dp).graphicsLayer {
            scaleX = scale.value * 1.04f; scaleY = scale.value * 1.04f; this.alpha = alpha.value * .22f
        }, tint = Color.Black)
        Icon(Icons.Rounded.Favorite, null, Modifier.size(size).graphicsLayer {
            scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value
        }, tint = Color.White)
    }
}

/** Places a popup at the window's top-left corner, so its content can be drawn in window coordinates. */
private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize) = IntOffset.Zero
}

/**
 * A feed photo that zooms like Instagram: pinching lifts it out of the post and over a darkening screen, following
 * both fingers, and it springs back into place when they let go. One finger still scrolls the feed and swipes the
 * carousel. [onTap] and [onDoubleTap] receive taps.
 */
@Composable
fun PinchZoomPhoto(url: String, contentDescription: String?, modifier: Modifier = Modifier, onTap: () -> Unit = {},
    onDoubleTap: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var zooming by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(TransformOrigin.Center) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val latestTap by rememberUpdatedState(onTap)
    val latestDoubleTap by rememberUpdatedState(onDoubleTap)
    Box(modifier.onGloballyPositioned { bounds = it.boundsInWindow() }
        .pointerInput(onDoubleTap != null) {
            detectTapGestures(onTap = { latestTap() }, onDoubleTap = if (onDoubleTap != null) { _ -> latestDoubleTap?.invoke() } else null)
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var pinching = false
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        if (!pinching) {
                            pinching = true
                            val centroid = event.calculateCentroid(useCurrent = true)
                            origin = TransformOrigin((centroid.x / size.width).coerceIn(0f, 1f), (centroid.y / size.height).coerceIn(0f, 1f))
                            scale = 1f; offset = Offset.Zero; zooming = true
                        }
                        scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                        offset += event.calculatePan()
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (pinching) scope.launch {
                    val from = offset
                    launch { animate(Offset.VectorConverter, from, Offset.Zero, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> offset = value } }
                    animate(scale, 1f, animationSpec = spring(dampingRatio = .8f, stiffness = Spring.StiffnessMediumLow)) { value, _ -> scale = value }
                    zooming = false
                }
            }
        }) {
        // While zoomed the photo is drawn in the popup, so the post keeps an empty frame behind it.
        AsyncImage(url, contentDescription, Modifier.fillMaxSize().graphicsLayer { alpha = if (zooming) 0f else 1f },
            contentScale = ContentScale.Crop)
    }
    if (zooming) Popup(popupPositionProvider = WindowOrigin, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        val dim = ((scale - 1f) / 1.5f).coerceIn(0f, .78f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim))) {
            with(density) {
                AsyncImage(url, null, Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(bounds.width.toDp(), bounds.height.toDp())
                    .graphicsLayer {
                        transformOrigin = origin
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    }, contentScale = ContentScale.Crop)
            }
        }
    }
}

/**
 * A full-screen photo that stays zoomed: pinch to zoom (up to 5×) around your fingers, drag to look around, and
 * double-tap to zoom back out. Unzoomed, a double-tap goes to [onDoubleTap] and one-finger swipes pass through to
 * the gallery. [onZoomed] reports whether it's zoomed, so the gallery can stop paging meanwhile.
 */
@Composable
fun ZoomablePhoto(url: String, contentDescription: String?, modifier: Modifier = Modifier, onDoubleTap: () -> Unit = {},
    onZoomed: (Boolean) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val latestDoubleTap by rememberUpdatedState(onDoubleTap)
    val latestZoomed by rememberUpdatedState(onZoomed)
    val zoomed = scale > 1.01f
    LaunchedEffect(zoomed) { latestZoomed(zoomed) }
    fun clamp(value: Offset, factor: Float, area: IntSize) = Offset(
        value.x.coerceIn(-(factor - 1f) * area.width / 2f, (factor - 1f) * area.width / 2f),
        value.y.coerceIn(-(factor - 1f) * area.height / 2f, (factor - 1f) * area.height / 2f))
    Box(modifier
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = {
                if (scale > 1.01f) scope.launch {
                    val from = offset
                    launch { animate(Offset.VectorConverter, from, Offset.Zero, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> offset = value } }
                    animate(scale, 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> scale = value }
                } else latestDoubleTap()
            })
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val fingers = event.changes.count { it.pressed }
                    val centroid = event.calculateCentroid(useCurrent = true)
                    // Unzoomed one-finger drags belong to the gallery. Lifting the last finger has no centroid.
                    if (fingers >= 1 && centroid.isSpecified && (fingers >= 2 || scale > 1.01f)) {
                        val pan = event.calculatePan()
                        val factor = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                        val applied = factor / scale
                        // Zooming keeps the point between the fingers in place.
                        val fromCenter = centroid - Offset(size.width / 2f, size.height / 2f)
                        offset = clamp((offset - fromCenter) * applied + fromCenter + pan, factor, size)
                        scale = factor
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (scale < 1.01f) { scale = 1f; offset = Offset.Zero }
            }
        }) {
        AsyncImage(url, contentDescription, Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = offset.x; translationY = offset.y
        }, contentScale = ContentScale.Fit)
    }
}
