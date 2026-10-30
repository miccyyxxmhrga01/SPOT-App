package com.example.spotapp.ui.theme.screens.home

import com.example.spotapp.ui.theme.components.SpotPalette
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.spotapp.data.PostUpload
import com.example.spotapp.data.PostUploadManager

private val UploadGold get() = SpotPalette.GoldDeep
private val UploadInk get() = SpotPalette.Ink
private val UploadMuted get() = SpotPalette.Muted
private val UploadIvory get() = SpotPalette.Ivory
private val UploadLine get() = SpotPalette.GoldLine
private val UploadError get() = SpotPalette.ErrorText

/** A compact strip above the stories while a new post publishes in the background. */
@Composable
internal fun PostUploadBanner(uid: String) {
    val context = LocalContext.current
    val upload by PostUploadManager.upload.collectAsStateWithLifecycle()
    val current = upload?.takeIf { it.uid == uid }
    // Keeps the last state on screen while the strip collapses away.
    var last by remember { mutableStateOf(current) }
    SideEffect { if (current != null) last = current }
    AnimatedVisibility(current != null, enter = expandVertically(tween(280)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(280)) + fadeOut(tween(200))) {
        (current ?: last)?.let { item -> UploadStrip(item, retry = { PostUploadManager.retry(context) }, dismiss = PostUploadManager::dismiss) }
    }
}

@Composable
private fun UploadStrip(item: PostUpload, retry: () -> Unit, dismiss: () -> Unit) {
    val uploading = item.status == PostUpload.Status.UPLOADING
    val progress by animateFloatAsState(item.progress, tween(400, easing = FastOutSlowInEasing), label = "post upload progress")
    val pulse by rememberInfiniteTransition(label = "post upload pulse").animateFloat(1f, .55f,
        infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Reverse), label = "post upload thumbnail")
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("post-upload-banner"),
        shape = RoundedCornerShape(16.dp), color = UploadIvory, border = BorderStroke(1.dp, UploadLine)) {
        Column(Modifier.animateContentSize()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).graphicsLayer { alpha = if (uploading) pulse else 1f }.clip(RoundedCornerShape(9.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF938363), Color(0xFF39362F)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(16.dp), tint = Color.White.copy(alpha = .6f))
                    if (item.thumbnail.isNotBlank()) AsyncImage(item.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(when (item.status) {
                        PostUpload.Status.UPLOADING -> when {
                            item.progress < .05f -> "Preparing your post…"
                            item.progress < .95f -> "Uploading…"
                            else -> "Almost done…"
                        }
                        PostUpload.Status.DONE -> "Posted"
                        PostUpload.Status.FAILED -> "Couldn't upload your post"
                    }, style = MaterialTheme.typography.labelMedium, color = if (item.status == PostUpload.Status.FAILED) UploadError else UploadInk,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    if (item.status == PostUpload.Status.FAILED) Text(item.error, style = MaterialTheme.typography.labelSmall,
                        color = UploadMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when (item.status) {
                    PostUpload.Status.UPLOADING -> Text("${(item.progress * 100).toInt()}%", Modifier.padding(end = 10.dp),
                        style = MaterialTheme.typography.labelSmall, color = UploadGold)
                    PostUpload.Status.DONE -> Icon(Icons.Rounded.CheckCircle, null, Modifier.padding(end = 10.dp).size(20.dp), tint = UploadGold)
                    PostUpload.Status.FAILED -> {
                        TextButton(retry, contentPadding = PaddingValues(horizontal = 10.dp)) {
                            Text("Retry", style = MaterialTheme.typography.labelMedium, color = UploadGold)
                        }
                        IconButton(dismiss, Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Close, "Dismiss", Modifier.size(18.dp), tint = UploadMuted)
                        }
                    }
                }
            }
            if (uploading) {
                val bar = Modifier.fillMaxWidth().height(2.dp)
                if (item.progress < .05f) LinearProgressIndicator(bar, color = UploadGold, trackColor = UploadLine)
                else LinearProgressIndicator(progress = { progress }, bar, color = UploadGold, trackColor = UploadLine,
                    drawStopIndicator = {}, gapSize = 0.dp)
            }
        }
    }
}
