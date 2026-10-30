package com.example.spotapp.ui.theme.screens.discover

import com.example.spotapp.ui.theme.components.spotAmbientBackground
import com.example.spotapp.ui.theme.components.SpotAvatar
import com.example.spotapp.ui.theme.components.SpotDialogStatusBar
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.SpotAuthButton
import com.example.spotapp.ui.theme.components.SpotScreenHeader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.spotapp.data.SpotTalentCategories
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val CommunityGold get() = SpotPalette.GoldDeep
internal val CommunityIvory get() = SpotPalette.Ivory
internal val CommunityLine get() = SpotPalette.Line
@Composable
internal fun communityChipColors() = FilterChipDefaults.filterChipColors(containerColor = SpotPalette.Surface,
    labelColor = if (SpotPalette.dark) SpotPalette.Muted else Color(0xFF554F43), selectedContainerColor = SpotPalette.GoldSoft, selectedLabelColor = SpotPalette.GoldInk)

/**
 * A group's cover or an event's poster. Without a photo it draws one: a gold gradient with the spotlight rings of
 * SPOT's event posters, the [title]'s initials and the group or event icon, so no card is left with an empty box.
 */
@Composable
internal fun CommunityCover(url: String, event: Boolean, modifier: Modifier = Modifier, title: String = "") {
    if (url.isNotBlank()) {
        Box(modifier.background(SpotPalette.GoldWash)) {
            AsyncImage(url, if (event) "Event poster" else "Group cover", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        return
    }
    val initials = coverInitials(title)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.background(Brush.linearGradient(listOf(Color(0xFFFBE7AC), SpotPalette.GoldBright, SpotPalette.GoldDeep)))) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .82f, size.height * .5f)
            val stroke = Stroke(1.5.dp.toPx())
            listOf(.95f, .68f, .42f).forEach { radius -> drawCircle(Color.White.copy(alpha = .3f), size.height * radius, center, style = stroke) }
            drawCircle(Color.White.copy(alpha = .22f), size.height * .24f, center)
        }
        Icon(if (event) Icons.Rounded.Event else Icons.Rounded.Groups, null,
            Modifier.align(BiasAlignment(.64f, 0f)).size(maxHeight * .2f), tint = Color.White.copy(alpha = .92f))
        if (initials.isNotBlank()) Text(initials, Modifier.align(Alignment.CenterStart).padding(start = 22.dp),
            color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
            fontSize = (maxHeight.value * .36f / fontScale).sp,
            style = MaterialTheme.typography.displayMedium.copy(shadow = Shadow(Color.Black.copy(alpha = .18f), Offset(0f, 3f), 10f)))
    }
}

/** Up to two initials for a cover: "Kulto ni Rene" is "KN". */
internal fun coverInitials(title: String): String =
    title.trim().split(Regex("\\s+")).mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }.take(2).joinToString("").uppercase()
@Composable
internal fun CommunityAvatar(url: String, uid: String = "") { SpotAvatar(url, "Profile", Modifier.size(38.dp), uid) }
@Composable
internal fun CommunityWindow(title: String, close: () -> Unit, busy: Boolean = false, content: @Composable (PaddingValues) -> Unit) {
    Dialog(onDismissRequest = { if (!busy) close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = !busy, dismissOnClickOutside = false)) {
        SpotDialogStatusBar()
        Scaffold(Modifier.fillMaxSize().spotAmbientBackground(CommunityIvory), containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
            SpotScreenHeader(title, Modifier.statusBarsPadding(), onBack = close, backEnabled = !busy)
        }, content = content)
    }
}
/** A form inside a [CommunityWindow]: the fields scroll and the main action stays pinned above the keyboard. */
@Composable
internal fun CommunityFormBody(padding: PaddingValues, action: String, enabled: Boolean, busy: Boolean, onAction: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { SpotAuthButton(action, loading = busy, enabled = enabled, onClick = onAction) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommunityCategories(selected: String, select: (String) -> Unit, all: Boolean = false) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (if (all) listOf("") + SpotTalentCategories else SpotTalentCategories).forEach { item ->
            FilterChip(selected == item, { select(item) }, label = { Text(item.ifBlank { "All talents" }, style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(50), colors = communityChipColors(),
                border = BorderStroke(1.dp, if (selected == item) SpotPalette.GoldLine else CommunityLine))
        }
    }
}
@Composable
internal fun CommunityError(message: String, retry: (() -> Unit)? = null) {
    if (message.isNotBlank()) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            if (retry != null) TextButton(onClick = retry) { Text("Retry") }
        }
    }
}
@Composable
internal fun CommunityBadge(label: String) {
    Surface(color = SpotPalette.GoldWash, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, SpotPalette.GoldLine)) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = SpotPalette.GoldInk, style = MaterialTheme.typography.labelSmall)
    }
}
internal fun eventDate(time: Long, pattern: String = "EEE, MMM d · h:mm a"): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
