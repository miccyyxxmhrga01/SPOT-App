package com.example.spotapp.ui.theme.screens.post

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun PostDraftMediaPreview(draft: PostDraft, edit: () -> Unit) {
    val pager = rememberPagerState(pageCount = { draft.mediaUris.size })
    val ratios = remember(draft.mediaEdits) { mutableStateMapOf<String, Float>() }
    val current = draft.mediaUris.getOrNull(pager.currentPage)
    val ratio by animateFloatAsState(ratios[current] ?: 1f, tween(200), label = "Preview proportions")
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(24.dp)).background(PostStage).testTag("draft-media-preview")) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { draft.mediaUris[it] }) { index ->
            val uri = draft.mediaUris[index]
            val context = LocalContext.current
            var video by remember(uri) { mutableStateOf(false) }
            val bitmap by produceState<Bitmap?>(null, uri, draft.mediaEdits[uri]) {
                try {
                    video = MediaLibraryRepository(context).describe(uri).isVideo
                    value = if (video) PostMediaExporter.thumbnail(context, uri)
                        else PostMediaExporter.imagePreview(context, uri, draft.mediaEdits[uri] ?: PostMediaEdit(), 960)
                    value?.let { ratios[uri] = previewAspectRatio(it.width, it.height) }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { value = null }
            }
            Box(Modifier.fillMaxSize().clickable(onClickLabel = "Edit media", onClick = edit), contentAlignment = Alignment.Center) {
                val image = bitmap
                if (image != null) Image(image.asImageBitmap(), "Selected media ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else AsyncImage(uri, "Selected media ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (video) Icon(Icons.Rounded.PlayArrow, "Edit video", Modifier.size(42.dp).background(Color.Black.copy(alpha = .35f), CircleShape).padding(8.dp), tint = Color.White)
            }
        }
        SpotAnimatedIconButton(Icons.Rounded.Edit, "Edit media", edit,
            Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.Black.copy(alpha = .4f), CircleShape), tint = Color.White)
        if (draft.mediaUris.size > 1) Text("${pager.currentPage + 1} / ${draft.mediaUris.size}",
            Modifier.align(Alignment.TopStart).padding(12.dp).background(Color.Black.copy(alpha = .45f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
        if (draft.mediaUris.size > 1) Box(Modifier.align(Alignment.BottomCenter).padding(12.dp)
            .background(Color.Black.copy(alpha = .3f), CircleShape).padding(horizontal = 10.dp, vertical = 8.dp)) {
            SpotPageIndicator(draft.mediaUris.size, pager.currentPage, dark = true)
        }
    }
}
