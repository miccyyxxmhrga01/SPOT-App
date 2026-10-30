package com.example.spotapp.ui.theme.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Shared white/gold tokens; older screens keep private names for the same values.
 * Each token reads [dark], so screens recompose when the member switches theme.
 */
object SpotPalette {
    /** Set by SPOTAppTheme before any screen composes. */
    var dark by mutableStateOf(false)

    val Gold get() = Color(0xFFD4AF37)
    val GoldBright get() = Color(0xFFFDD87A)
    val GoldDeep get() = if (dark) Color(0xFFD9AE3A) else Color(0xFFAD8420)
    val GoldInk get() = if (dark) Color(0xFFE6C766) else Color(0xFF785B13)
    val GoldSoft get() = if (dark) Color(0xFF3A3118) else Color(0xFFFFF1CB)
    val GoldWash get() = if (dark) Color(0xFF2B2615) else Color(0xFFFFF8E6)
    val GoldLine get() = if (dark) Color(0xFF5C4E24) else Color(0xFFE7D6A3)
    val Ivory get() = if (dark) Color(0xFF1B1A17) else Color(0xFFFFFCF6)
    val Ink get() = if (dark) Color(0xFFF2EFE6) else Color(0xFF26251F)
    val Muted get() = if (dark) Color(0xFFA8A69B) else Color(0xFF74746C)
    val Line get() = if (dark) Color(0xFF3A3830) else Color(0xFFE9E4D9)
    val Track get() = if (dark) Color(0xFF2E2C26) else Color(0xFFF3EFE5)
    val Heart get() = if (dark) Color(0xFFF0666B) else Color(0xFFE5484D)
    val Danger get() = if (dark) Color(0xFFE27A7A) else Color(0xFFB63D3D)
    /** Form error and confirmation messages under fields. */
    val ErrorText get() = if (dark) Color(0xFFE27A7A) else Color(0xFFB3261E)
    val SuccessText get() = if (dark) Color(0xFF9CCB8A) else Color(0xFF365A27)
    /** Green pills: paid, going, open to collabs. */
    val GreenFill get() = if (dark) Color(0xFF1C3324) else Color(0xFFE3F3E8)
    val GreenLine get() = if (dark) Color(0xFF2F5A3D) else Color(0xFFBFE3CB)
    val GreenInk get() = if (dark) Color(0xFF9CCB8A) else Color(0xFF1E6B3A)
    /** Text and icons on gold fills stay dark in both themes. */
    val OnGold get() = Color(0xFF26251F)
    /** Content on an [Ink] fill (selected chips, toasts): the opposite of Ink. */
    val OnInk get() = if (dark) Color(0xFF171717) else Color.White
    /** Gold links and labels on an [Ink] fill. */
    val GoldOnInk get() = if (dark) Color(0xFF785B13) else Color(0xFFFFD66B)
    /** Cards, sheets and fields: white in light mode, a raised charcoal in dark mode. */
    val Surface get() = if (dark) Color(0xFF232220) else Color.White
    /** Page background behind cards. */
    val Page get() = if (dark) Color(0xFF121110) else Color(0xFFF8F8F8)
    /** Warm shadow tint, so elevation reads as depth rather than grey smudges on ivory. */
    val Shadow get() = if (dark) Color.Black else Color(0xFF554519)
}

val SpotGoldGradient: Brush = Brush.linearGradient(listOf(SpotPalette.GoldBright, SpotPalette.Gold))

/** A 48dp touch target with a 42dp circular face: white by default, gold when [prominent]. */
@Composable
fun SpotHeaderAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    prominent: Boolean = false, enabled: Boolean = true, tag: String = "") {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .9f else 1f, spring(dampingRatio = .6f, stiffness = 650f), label = "header action press")
    Box(modifier.size(48.dp).then(if (tag.isNotBlank()) Modifier.testTag(tag) else Modifier)
        .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
        .semantics { contentDescription = label; role = Role.Button }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(42.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else .45f }
            .shadow(if (prominent) 6.dp else 2.dp, CircleShape, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow)
            .background(if (prominent) SpotGoldGradient else SolidColor(SpotPalette.Surface), CircleShape)
            .border(1.dp, if (prominent) Color.White.copy(alpha = .7f) else SpotPalette.Line, CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(21.dp), tint = if (prominent) SpotPalette.OnGold else SpotPalette.Ink)
        }
    }
}

/** Large page title with optional back button, subtitle and trailing actions. */
@Composable
fun SpotScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null,
    onBack: (() -> Unit)? = null, backEnabled: Boolean = true, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = if (onBack != null) 8.dp else 20.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            SpotHeaderAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, enabled = backEnabled)
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = if (onBack != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Soft gold icon tile, used to mark sections and settings rows. */
@Composable
fun SpotIconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 32.dp,
    tint: Color = SpotPalette.GoldDeep, container: Color = SpotPalette.GoldSoft) {
    Box(modifier.size(size).background(container, RoundedCornerShape(size * .32f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * .56f), tint = tint)
    }
}

@Composable
fun SpotSectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, icon: ImageVector? = null,
    padding: PaddingValues = PaddingValues(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
    trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(padding), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { SpotIconTile(icon); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SpotPalette.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(this)
    }
}

enum class SpotButtonStyle { Primary, Secondary, Tonal }

/** Pill button: gold gradient (primary), white outline (secondary) or soft gold (tonal). */
@Composable
fun SpotPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null,
    style: SpotButtonStyle = SpotButtonStyle.Primary, enabled: Boolean = true, compact: Boolean = false,
    trailingIcon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, spring(dampingRatio = .6f, stiffness = 650f), label = "pill press")
    val shape = RoundedCornerShape(50)
    val content = when (style) {
        SpotButtonStyle.Tonal -> SpotPalette.GoldInk
        SpotButtonStyle.Primary -> SpotPalette.OnGold
        SpotButtonStyle.Secondary -> SpotPalette.Ink
    }
    Row(modifier.graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else .5f }
        .heightIn(min = if (compact) 40.dp else 48.dp)
        .then(if (style == SpotButtonStyle.Primary) Modifier.shadow(4.dp, shape, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow) else Modifier)
        .clip(shape)
        .background(when (style) {
            SpotButtonStyle.Primary -> SpotGoldGradient
            SpotButtonStyle.Secondary -> SolidColor(SpotPalette.Surface)
            SpotButtonStyle.Tonal -> SolidColor(SpotPalette.GoldSoft)
        })
        .border(1.dp, when (style) {
            SpotButtonStyle.Primary -> Color.White.copy(alpha = .55f)
            SpotButtonStyle.Secondary -> SpotPalette.Line
            SpotButtonStyle.Tonal -> SpotPalette.GoldLine
        }, shape)
        .clickable(interaction, ripple(color = SpotPalette.GoldDeep), enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = content)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailingIcon, null, Modifier.size(18.dp), tint = content)
        }
    }
}

/** White rounded card with a hairline border and a warm, low shadow. */
@Composable
fun SpotCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(24.dp), color: Color = SpotPalette.Surface,
    border: Color = SpotPalette.Line, elevation: Dp = 2.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.shadow(elevation, shape, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow)
        .clip(shape).background(color).border(1.dp, border, shape), content = content)
}

/** One empty/error state for every screen: haloed icon, title, message and an optional action. */
@Composable
fun SpotEmptyState(icon: ImageVector, title: String, modifier: Modifier = Modifier, message: String = "",
    actionLabel: String? = null, onAction: () -> Unit = {}, actionIcon: ImageVector? = null, loading: Boolean = false) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(SpotPalette.GoldSoft, SpotPalette.GoldSoft.copy(alpha = 0f))), CircleShape))
            Box(Modifier.size(64.dp).shadow(6.dp, CircleShape, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow)
                .background(SpotPalette.Surface, CircleShape).border(1.dp, SpotPalette.GoldLine, CircleShape), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = SpotPalette.GoldDeep, strokeWidth = 2.dp)
                else Icon(icon, null, Modifier.size(28.dp), tint = SpotPalette.GoldDeep)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = SpotPalette.Ink, textAlign = TextAlign.Center)
        if (message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted, textAlign = TextAlign.Center)
        }
        if (actionLabel != null) {
            Spacer(Modifier.height(18.dp))
            SpotPillButton(actionLabel, onAction, icon = actionIcon)
        }
    }
}

/**
 * SPOT's one yes/no pop-up: an icon tile, a centred title, a short message and a gold action (red when [danger]).
 * Anything that needs more than a line of typing belongs on a page instead. [onDismissButton] lets the second
 * button do something other than cancel, such as Discard.
 */
@Composable
fun SpotConfirmDialog(icon: ImageVector, title: String, message: String?, confirm: String, onDismiss: () -> Unit,
    modifier: Modifier = Modifier, danger: Boolean = false, busy: Boolean = false, confirmEnabled: Boolean = true,
    dismiss: String = "Cancel", onDismissButton: () -> Unit = onDismiss, confirmTag: String = "confirm-action",
    content: (@Composable () -> Unit)? = null, onConfirm: () -> Unit) {
    val onAction = if (danger) MaterialTheme.colorScheme.onError else SpotPalette.OnGold
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, modifier = modifier, containerColor = SpotPalette.Surface,
        titleContentColor = SpotPalette.Ink, textContentColor = SpotPalette.Muted,
        icon = { SpotIconTile(icon, size = 52.dp, tint = if (danger) SpotPalette.Danger else SpotPalette.GoldDeep,
            container = if (danger) SpotPalette.Danger.copy(alpha = .1f) else SpotPalette.GoldSoft) },
        title = { Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
        text = if (message != null || content != null) { {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium)
                content?.invoke()
            }
        } } else null,
        confirmButton = { Button(onConfirm, Modifier.testTag(confirmTag), enabled = !busy && confirmEnabled, shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = if (danger) SpotPalette.Danger else SpotPalette.Gold, contentColor = onAction)) {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = onAction)
            else Text(confirm, fontWeight = FontWeight.SemiBold)
        } },
        dismissButton = { TextButton(onDismissButton, enabled = !busy) { Text(dismiss, color = SpotPalette.Ink) } })
}

/**
 * A full-screen dialog window draws its own status bar, which starts with the light theme's dark icons.
 * Call inside the dialog so the icons stay readable on the page: light icons in dark mode.
 */
@Composable
fun SpotDialogStatusBar() {
    val view = LocalView.current
    val dark = SpotPalette.dark
    DisposableEffect(view, dark) {
        (view.parent as? DialogWindowProvider)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !dark }
        onDispose { }
    }
}

/** A one-tap choice: plain when off, soft gold with a check when on. Used for courses and talents. */
@Composable
internal fun SpotOptionChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilterChip(selected, onClick, label = { Text(label, maxLines = 1) }, modifier = modifier, enabled = enabled,
        leadingIcon = if (selected) { { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) } } else null,
        shape = CircleShape, colors = FilterChipDefaults.filterChipColors(containerColor = SpotPalette.Surface, labelColor = SpotPalette.Ink,
            selectedContainerColor = SpotPalette.GoldSoft, selectedLabelColor = SpotPalette.GoldInk,
            selectedLeadingIconColor = SpotPalette.GoldDeep),
        border = FilterChipDefaults.filterChipBorder(enabled, selected, borderColor = SpotPalette.Line,
            selectedBorderColor = SpotPalette.GoldLine))
}
