package com.example.spotapp.ui.theme.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.spotapp.data.ProfileIdentityStore
import kotlinx.coroutines.delay

/** Same cleanup contract as DisposableEffect, also detaching subscriptions in the background. */
@Composable
fun LifecycleDisposableEffect(vararg keys: Any?, effect: DisposableEffectScope.() -> DisposableEffectResult) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(effect)
    DisposableEffect(lifecycle, *keys) {
        val scope = this
        var active: DisposableEffectResult? = null
        fun stop() { active?.dispose(); active = null }
        fun start() { if (active == null) active = latest(scope) }
        val observer = LifecycleEventObserver { _, event ->
            when (event) { Lifecycle.Event.ON_START -> start(); Lifecycle.Event.ON_STOP -> stop(); else -> Unit }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
}

@Composable
fun SpotAvatar(url: String?, description: String?, modifier: Modifier = Modifier, uid: String = "") {
    val identity = remember(uid) { ProfileIdentityStore.identity(uid) }
    val current by identity.collectAsStateWithLifecycle()
    LifecycleDisposableEffect(uid) {
        val release = ProfileIdentityStore.acquire(uid)
        onDispose { release() }
    }
    // A loaded empty URL intentionally overrides an old denormalized avatar after photo removal.
    val photo = current?.profileImageUrl ?: url.orEmpty()
    Box(modifier.clip(CircleShape).background(SpotPalette.Track), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Person, description, Modifier.fillMaxSize(.55f), tint = Color(0xFF77756D))
        if (photo.isNotBlank()) AsyncImage(
            ImageRequest.Builder(LocalContext.current).data(photo).crossfade(180).build(), description,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun SpotSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    placeholder: String = "Search", loading: Boolean = false, extra: (@Composable () -> Unit)? = null) {
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium, shape = RoundedCornerShape(50),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = SpotPalette.GoldDeep) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp, color = SpotPalette.GoldDeep)
                if (value.isNotEmpty()) IconButton({ onValueChange("") }) { Icon(Icons.Rounded.Close, "Clear search", Modifier.size(18.dp)) }
                extra?.invoke()
            }
        }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotPalette.GoldDeep,
            unfocusedBorderColor = SpotPalette.Line, focusedContainerColor = SpotPalette.Surface,
            unfocusedContainerColor = SpotPalette.Surface.copy(alpha = .92f), cursorColor = SpotPalette.GoldDeep))
}

enum class SkeletonKind { ROWS, POST, GRID, PROFILE, STORIES, CARDS }

/** Hold the previous frame for quick cache hits; only longer waits show placeholders. */
@Composable
fun SpotLoading(loading: Boolean, kind: SkeletonKind = SkeletonKind.ROWS, modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { if (loading) { delay(180); show = true } else show = false }
    Crossfade(show, modifier, animationSpec = tween(180), label = "content loading") { skeleton ->
        if (skeleton) SpotSkeleton(kind) else if (!loading) content()
    }
}

@Composable
fun SpotSkeleton(kind: SkeletonKind = SkeletonKind.ROWS) {
    val transition = rememberInfiniteTransition(label = "skeleton shimmer")
    val x by transition.animateFloat(-600f, 1200f, infiniteRepeatable(tween(1250, easing = LinearEasing)), label = "shimmer position")
    val brush = Brush.linearGradient(listOf(SpotPalette.Track, SpotPalette.Ivory, SpotPalette.Track), Offset(x, 0f), Offset(x + 500f, 500f))
    @Composable fun block(modifier: Modifier, circle: Boolean = false) {
        Box(modifier.clip(if (circle) CircleShape else RoundedCornerShape(9.dp)).background(brush))
    }
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("spot-skeleton").semantics { contentDescription = "Loading" }, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        when (kind) {
            SkeletonKind.STORIES -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { repeat(4) { block(Modifier.size(54.dp), true) } }
            SkeletonKind.GRID -> repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(3) { block(Modifier.weight(1f).aspectRatio(.8f)) } } }
            SkeletonKind.PROFILE -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    block(Modifier.size(82.dp), true); repeat(3) { block(Modifier.weight(1f).height(34.dp)) }
                }
                block(Modifier.fillMaxWidth(.5f).height(14.dp)); block(Modifier.fillMaxWidth(.8f).height(12.dp))
            }
            else -> repeat(if (kind == SkeletonKind.ROWS) 5 else 2) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    block(Modifier.size(40.dp), true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        block(Modifier.fillMaxWidth(.5f).height(12.dp)); block(Modifier.fillMaxWidth(.8f).height(10.dp))
                    }
                }
                if (kind != SkeletonKind.ROWS) block(Modifier.fillMaxWidth().height(if (kind == SkeletonKind.POST) 240.dp else 150.dp))
            }
        }
    }
}
