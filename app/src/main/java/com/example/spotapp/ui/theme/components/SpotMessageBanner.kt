package com.example.spotapp.ui.theme.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.PushNotifications
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * A new message while SPOT is open: it slides in at the top for a few seconds, like a phone notification but in
 * SPOT's own colours (a dark card in both themes). Tapping opens the chat; swiping it up or the X closes it.
 */
@Composable
fun SpotMessageBanner(modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf<PushNotifications.InAppMessage?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // A newer message replaces the one showing and restarts its timer.
        PushNotifications.inApp.collectLatest {
            message = it; visible = true
            delay(4_500)
            visible = false
        }
    }
    val shown = message
    val shape = RoundedCornerShape(20.dp)
    AnimatedVisibility(visible && shown != null, modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        if (shown != null) Row(Modifier.fillMaxWidth().shadow(10.dp, shape, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow)
            .clip(shape).background(MaterialTheme.colorScheme.inverseSurface)
            .pointerInput(shown) { detectVerticalDragGestures { _, drag -> if (drag < -8f) visible = false } }
            .clickable(onClickLabel = "Open chat") { visible = false; PushNotifications.openConversation.value = shown.conversationId }
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }.testTag("message-banner"),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(SpotGoldGradient, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                SpotMessageBubbleIcon(Modifier.size(20.dp), SpotPalette.OnGold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(shown.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (shown.body.isNotBlank()) Text(shown.body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .75f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton({ visible = false }) {
                Icon(Icons.Rounded.Close, "Dismiss", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .7f))
            }
        }
    }
}
