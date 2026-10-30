package com.example.spotapp.ui.theme.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Source and glass are siblings: navigation is never captured into its own backdrop. */
class SpotGlassState internal constructor(internal val source: GraphicsLayer) {
    internal var origin = Offset.Zero
    internal var revision = 0L
    internal var ready = false
}

val LocalSpotGlass = staticCompositionLocalOf<SpotGlassState?> { null }
val LocalSpotBottomContentPadding = staticCompositionLocalOf { 0.dp }

@Composable
fun rememberSpotGlassState(): SpotGlassState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { SpotGlassState(layer) }
}

fun Modifier.spotGlassSource(state: SpotGlassState): Modifier =
    onGloballyPositioned { state.origin = it.boundsInWindow().topLeft }
        .drawWithContent {
            state.source.record { this@drawWithContent.drawContent() }
            drawLayer(state.source)
            state.ready = true
            state.revision++
        }

/** Downsamples only the strip behind the bar, then blurs off the UI thread (including API 24–30). */
@Composable
fun SpotGlassBackdrop(modifier: Modifier = Modifier, state: SpotGlassState? = LocalSpotGlass.current) {
    if (state == null) return
    val sample = rememberGraphicsLayer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var bitmap by remember(state) { mutableStateOf<ImageBitmap?>(null) }
    val downsample = 4
    val padding = 8 // Sample pixels; avoids clamped blur edges at the visible glass boundary.
    LaunchedEffect(state, bounds, density, direction, lifecycle) {
        if (bounds.isEmpty) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastRevision = -1L
            try {
                while (isActive) {
                    if (state.ready && state.revision != lastRevision) {
                        lastRevision = state.revision
                        val width = (bounds.width / downsample).roundToInt().coerceAtLeast(1)
                        val height = (bounds.height / downsample).roundToInt().coerceAtLeast(1)
                        val offset = bounds.topLeft - state.origin
                        try {
                            sample.record(density, direction, IntSize(width + padding * 2, height + padding * 2)) {
                                translate(padding.toFloat(), padding.toFloat()) {
                                    scale(1f / downsample, 1f / downsample, Offset.Zero) {
                                        translate(-offset.x, -offset.y) { drawLayer(state.source) }
                                    }
                                }
                            }
                            val captured = sample.toImageBitmap()
                            bitmap = withContext(Dispatchers.Default) {
                                val software = captured.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
                                val pixels = IntArray(software.width * software.height)
                                software.getPixels(pixels, 0, software.width, 0, 0, software.width, software.height)
                                val blurred = blurGlassPixels(pixels, software.width, software.height, 3)
                                Bitmap.createBitmap(blurred, software.width, software.height, Bitmap.Config.ARGB_8888).asImageBitmap()
                                    .also { software.recycle() }
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { /* Frosted tint remains usable if capture is unavailable. */ }
                    }
                    delay(90) // At most 11 captures/second; no work for unchanged content.
                }
            } finally { bitmap = null }
        }
    }
    Canvas(modifier.onGloballyPositioned { bounds = it.boundsInWindow() }) {
        bitmap?.let {
            drawImage(it, srcOffset = IntOffset(padding, padding),
                srcSize = IntSize((it.width - padding * 2).coerceAtLeast(1), (it.height - padding * 2).coerceAtLeast(1)),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
        }
    }
}
