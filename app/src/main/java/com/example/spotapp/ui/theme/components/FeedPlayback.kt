package com.example.spotapp.ui.theme.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import kotlin.math.abs

/** Select by the media rectangle, not the height of the caption/card around it. */
internal class FeedPlayback {
    var viewport by mutableStateOf(Rect.Zero)
    private val bounds = mutableStateMapOf<String, Rect>()
    val activeId: String? by derivedStateOf {
        bounds.entries.filter { (_, r) ->
            r.width > 0 && r.height > 0 && viewport.overlaps(r) &&
                r.intersect(viewport).height >= minOf(r.height, viewport.height) * 0.6f &&
                r.intersect(viewport).width >= r.width * 0.8f
        }.minWithOrNull(compareBy<Map.Entry<String, Rect>> { abs(it.value.center.y - viewport.center.y) }.thenBy { it.key })?.key
    }
    fun update(id: String, rect: Rect) { bounds[id] = rect }
    fun remove(id: String) { bounds.remove(id) }
}

/**
 * Sound for the videos that play by themselves in the feed. They start muted, so scrolling never blasts sound or stops
 * the member's music; the speaker button turns sound on (or off again) for every feed video until SPOT closes.
 */
internal object FeedSound {
    var muted by mutableStateOf(true)
}

internal fun Modifier.feedViewport(playback: FeedPlayback) = onGloballyPositioned { playback.viewport = it.boundsInWindow() }

internal fun Modifier.feedVideo(playback: FeedPlayback, id: String): Modifier = composed {
    DisposableEffect(playback, id) { onDispose { playback.remove(id) } }
    onGloballyPositioned { playback.update(id, Rect(it.positionInWindow(), it.size.toSize())) }
}
