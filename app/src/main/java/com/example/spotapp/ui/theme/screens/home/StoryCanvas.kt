package com.example.spotapp.ui.theme.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import com.example.spotapp.data.StoryComposition
import com.example.spotapp.ui.theme.InterFontFamily
import com.example.spotapp.ui.theme.components.SpotVideo
import com.example.spotapp.ui.theme.components.colorFilter
import kotlin.math.roundToInt

/** The editor, live viewer and saved Highlights render the same 9:16 composition. */
@Composable
internal fun StoryCanvas(url: String, video: Boolean, composition: StoryComposition, active: Boolean,
    modifier: Modifier = Modifier, editable: Boolean = false, cropMode: Boolean = false, selected: String? = null,
    onChange: (StoryComposition) -> Unit = {}, onSelect: (String) -> Unit = {},
    onReady: () -> Unit = {}, onFailure: () -> Unit = {}) {
    val latest by rememberUpdatedState(composition)
    val change by rememberUpdatedState(onChange)
    val select by rememberUpdatedState(onSelect)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight * 9f / 16f)
        val density = LocalDensity.current
        Box(Modifier.width(width).aspectRatio(9f / 16f).clipToBounds().background(Color(0xFF111111)).testTag("story-canvas")) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                scaleX = composition.scale.toFloat(); scaleY = composition.scale.toFloat()
                translationX = size.width * composition.offsetX.toFloat(); translationY = size.height * composition.offsetY.toFloat()
                rotationZ = composition.rotation * 90f
            }) {
                if (video) SpotVideo(url, active, Modifier.fillMaxSize(), muted = composition.muted,
                    clipStartMs = composition.trimStartMs, clipEndMs = composition.trimEndMs, fill = composition.fill, showControls = false)
                else AsyncImage(url, "Story preview", Modifier.fillMaxSize(), contentScale = if (composition.fill) ContentScale.Crop else ContentScale.Fit,
                    colorFilter = composition.look.colorFilter(), onSuccess = { onReady() }, onError = { onFailure() })
            }
            if (editable && cropMode) Box(Modifier.fillMaxSize().testTag("story-crop-gesture").pointerInput(url) {
                detectTransformGestures { _, pan, zoom, _ ->
                    change(latest.copy(scale = (latest.scale * zoom).coerceIn(1.0, 4.0),
                        offsetX = (latest.offsetX + pan.x / size.width).coerceIn(-1.0, 1.0),
                        offsetY = (latest.offsetY + pan.y / size.height).coerceIn(-1.0, 1.0)))
                }
            })
            composition.overlays.forEach { overlay ->
                key(overlay.id) {
                    var measured by remember { mutableStateOf(IntSize.Zero) }
                    val canvasWidth = with(density) { width.toPx() }
                    val canvasHeight = canvasWidth * 16f / 9f
                    val drag = if (editable && !cropMode) Modifier
                        .pointerInput(overlay.id) { detectTapGestures(onTap = { select(overlay.id) }) }
                        .pointerInput(overlay.id, canvasWidth) {
                            detectDragGestures(onDragStart = { select(overlay.id) }) { event, amount ->
                                event.consume()
                                change(latest.copy(overlays = latest.overlays.map { layer -> if (layer.id != overlay.id) layer else layer.copy(
                                    x = (layer.x + amount.x / canvasWidth).coerceIn(.08, .92), y = (layer.y + amount.y / canvasHeight).coerceIn(.08, .92)) }))
                            }
                        } else Modifier
                    Text(overlay.text, modifier = Modifier.offset { IntOffset((overlay.x * canvasWidth - measured.width / 2).roundToInt(),
                        (overlay.y * canvasHeight - measured.height / 2).roundToInt()) }
                        .widthIn(max = width * .84f).onSizeChanged { measured = it }.then(drag)
                        .testTag("story-layer-${overlay.id}")
                        .border(if (editable && selected == overlay.id && !cropMode) 1.dp else 0.dp,
                            if (editable && selected == overlay.id && !cropMode) Color(0xFFFDD87A) else Color.Transparent, RoundedCornerShape(8.dp))
                        .background(if (overlay.background) Color.Black.copy(alpha = .55f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp), color = Color(overlay.color), fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        fontSize = (width.value * overlay.size.toFloat() / density.fontScale).sp,
                        lineHeight = (width.value * overlay.size.toFloat() * 1.2f / density.fontScale).sp)
                }
            }
        }
    }
}
