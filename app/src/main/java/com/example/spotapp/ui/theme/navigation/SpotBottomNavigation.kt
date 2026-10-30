package com.example.spotapp.ui.theme.navigation

import com.example.spotapp.ui.theme.spotSp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.example.spotapp.ui.theme.components.SpotGlassBackdrop
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.spotapp.data.AccountType
import com.example.spotapp.ui.theme.InterFontFamily
import com.example.spotapp.ui.theme.components.SpotGoldGradient
import com.example.spotapp.ui.theme.components.SpotIconTile
import com.example.spotapp.ui.theme.components.SpotMessageBubbleIcon
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.models.SpotRoute
import com.example.spotapp.ui.theme.models.SpotUiProfile

private val NavInk get() = SpotPalette.Ink
private val NavMuted get() = SpotPalette.Muted
private val NavRed get() = SpotPalette.Heart
val LocalSpotBottomNavigationSuppressed = staticCompositionLocalOf { false }

// 0f = fully expanded, 1f = fully compact. Changes every animation frame, so it uses
// compositionLocalOf (not staticCompositionLocalOf) to only recompose readers.
private val LocalSpotNavCompactFraction = compositionLocalOf { 0f }

/** What the Create button in the middle of the bar can start. */
enum class SpotCreateAction { POST, STORY, EVENT }

// Notifications open from Home's bell, so no tab is selected there.
private fun selectedTab(route: SpotRoute): Int = when (route) {
    SpotRoute.HOME -> 0
    SpotRoute.DISCOVER -> 1
    SpotRoute.MESSAGES_CHATS, SpotRoute.MESSAGES_GROUPS, SpotRoute.COLLAB_REQUESTS -> 3
    SpotRoute.PROFILE -> 4
    else -> -1
}

/**
 * Home · Discover · Create · Messages · Profile. Create is the raised gold button in the middle; it opens a menu
 * to post, add a story or, for Spotters, create an event. Unread messages show a red dot, never a count.
 */
@Composable
fun SpotMainBottomNavigation(
    selectedRoute: SpotRoute, profile: SpotUiProfile, onNavigate: (SpotRoute) -> Unit,
    visible: Boolean = true, hidden: Boolean = false, unreadMessagesCount: Int = 0,
    onCreate: (SpotCreateAction) -> Unit = { if (it == SpotCreateAction.POST) onNavigate(SpotRoute.NEW_POST) }
) {
    if (LocalSpotBottomNavigationSuppressed.current) return
    val selected = selectedTab(selectedRoute)
    // hidden -> fully removed (e.g. a chat thread is open and owns the bottom area).
    // visible (when not hidden) -> true = full size, false = compact pill (e.g. scrolling down).
    AnimatedVisibility(!hidden,
        enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(140))) {
        GlassNavigation(compact = !visible, selected, 5, indicator = false) {
            NavigationItem("Home", selected == 0, { onNavigate(SpotRoute.HOME) }, Modifier.weight(1f)) { tint ->
                Icon(if (selected == 0) Icons.Rounded.Home else Icons.Outlined.Home, null, Modifier.size(24.dp), tint)
            }
            NavigationItem("Discover", selected == 1, { onNavigate(SpotRoute.DISCOVER) }, Modifier.weight(1f)) { tint ->
                Icon(if (selected == 1) Icons.Rounded.Explore else Icons.Outlined.Explore, null, Modifier.size(24.dp), tint)
            }
            CreateButton(profile.accountType == AccountType.TALENT_SEEKER, onCreate, Modifier.weight(1f))
            NavigationItem("Messages", selected == 3, { onNavigate(SpotRoute.MESSAGES_CHATS) },
                Modifier.weight(1f), dot = unreadMessagesCount > 0) { tint -> SpotMessageBubbleIcon(Modifier.size(25.dp), tint) }
            NavigationItem("Profile", selected == 4, { onNavigate(SpotRoute.PROFILE) }, Modifier.weight(1f)) { tint ->
                Box(Modifier.size(28.dp).border(if (selected == 4) 2.dp else 1.5.dp, if (selected == 4) SpotPalette.GoldDeep else tint.copy(alpha = 0.6f), CircleShape)
                    .padding(2.5.dp).clip(CircleShape).background(if (SpotPalette.dark) SpotPalette.Track else Color(0xFFEDEBE4)), contentAlignment = Alignment.Center) {
                    // Keep a normal profile icon beneath Coil while loading or on an invalid URL.
                    Icon(Icons.Rounded.Person, null, Modifier.size(17.dp).testTag("profile-avatar-fallback"), tint)
                    if (profile.profileImageUrl.isNotBlank()) {
                        AsyncImage(profile.profileImageUrl, null, Modifier.fillMaxSize().testTag("profile-avatar-photo"), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }
}

/** The raised gold + and its menu. It lowers into the bar when the bar is compact. */
@Composable
private fun CreateButton(spotter: Boolean, onCreate: (SpotCreateAction) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val compactFraction = LocalSpotNavCompactFraction.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "create press")
    val turn by animateFloatAsState(if (open) 45f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "create turn")
    val size = 56.dp - 16.dp * compactFraction
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.offset(y = (-16).dp * (1f - compactFraction)).size(size).graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(8.dp, CircleShape, ambientColor = Color(0xFFC99A1E), spotColor = Color(0xFFC99A1E))
            .background(SpotGoldGradient, CircleShape).border(3.dp, SpotPalette.Ivory, CircleShape).clip(CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "Create") { open = true }
            .semantics { contentDescription = "Create" }.testTag("nav-create"), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, Modifier.size(28.dp - 6.dp * compactFraction).rotate(turn), tint = SpotPalette.OnGold)
        }
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, (-12).dp), shape = RoundedCornerShape(22.dp),
            containerColor = SpotPalette.Surface, shadowElevation = 10.dp) {
            CreateItem(Icons.Rounded.AddPhotoAlternate, "Post", "create-post") { open = false; onCreate(SpotCreateAction.POST) }
            CreateItem(Icons.Rounded.AutoStories, "Story", "create-story") { open = false; onCreate(SpotCreateAction.STORY) }
            if (spotter) CreateItem(Icons.Rounded.Event, "Event", "create-event") { open = false; onCreate(SpotCreateAction.EVENT) }
        }
    }
}

@Composable
private fun CreateItem(icon: ImageVector, title: String, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(title, style = MaterialTheme.typography.titleSmall, color = NavInk) },
        onClick = onClick, leadingIcon = { SpotIconTile(icon, size = 38.dp, container = SpotPalette.GoldSoft) },
        modifier = Modifier.heightIn(min = 56.dp).widthIn(min = 180.dp).testTag(tag))
}

@Composable
private fun GlassNavigation(compact: Boolean, selected: Int, count: Int, indicator: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val compactFraction by animateFloatAsState(
        if (compact) 1f else 0f,
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "Navigation compact fraction"
    )
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(36.dp)
        val barHeight = 70.dp - (22.dp * compactFraction) // 70dp expanded -> 48dp compact
        // Frosted white in light mode, smoked charcoal in dark mode, so the tab labels keep their contrast.
        val dark = SpotPalette.dark
        val rim = if (dark) listOf(Color.White.copy(alpha = .14f), Color.White.copy(alpha = .04f))
            else listOf(Color.White.copy(alpha = .95f), Color(0xFFDBD4C2).copy(alpha = .55f))
        val frost = if (dark) listOf(SpotPalette.Surface.copy(alpha = .88f), SpotPalette.Ivory.copy(alpha = .84f))
            else listOf(Color.White.copy(alpha = .82f), Color(0xFFF7F3EA).copy(alpha = .74f))
        // Only the glass is clipped to the bar, so the raised Create button can rise above it.
        BoxWithConstraints(Modifier.widthIn(max = 520.dp).fillMaxWidth().height(barHeight).testTag("spot-navigation")) {
            Box(Modifier.matchParentSize()
                .shadow(8.dp, shape, clip = false, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow)
                .clip(shape)
                .border(1.dp, Brush.verticalGradient(rim), shape)) {
                // Capture is separate from this foreground, so icons and badges stay crisp.
                SpotGlassBackdrop(Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(frost)))
            }
            if (indicator && selected >= 0) {
                val slotWidth = (maxWidth - 16.dp) / count
                val indicatorWidth = minOf(54.dp, slotWidth - 4.dp) * (1f - 0.12f * compactFraction)
                val indicatorHeight = 48.dp - (12.dp * compactFraction)
                val indicatorTopInset = 9.dp - (3.dp * compactFraction)
                val position by animateDpAsState(8.dp + slotWidth * selected + (slotWidth - indicatorWidth) / 2,
                    spring(dampingRatio = 0.82f, stiffness = 420f), label = "Navigation selection")
                Box(Modifier.offset(x = position, y = indicatorTopInset).width(indicatorWidth).height(indicatorHeight)
                    .shadow(2.dp, RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(if (dark) listOf(SpotPalette.GoldWash, SpotPalette.GoldSoft)
                        else listOf(SpotPalette.GoldWash.copy(alpha = .92f), Color(0xFFEEDBA0).copy(alpha = .8f))), RoundedCornerShape(22.dp))
                    .border(1.dp, if (dark) SpotPalette.GoldLine else Color.White.copy(alpha = 0.9f), RoundedCornerShape(22.dp)))
            }
            CompositionLocalProvider(LocalSpotNavCompactFraction provides compactFraction) {
                Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp).selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}

/** A tab: its icon over a short name, which fades out while the bar is compact. */
@Composable
private fun NavigationItem(label: String, selected: Boolean, onClick: () -> Unit,
                           modifier: Modifier = Modifier, badgeCount: Int = 0, dot: Boolean = false, showLabel: Boolean = true,
                           icon: @Composable (Color) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val compactFraction = LocalSpotNavCompactFraction.current
    val pressScale = if (pressed) 0.94f else if (selected) 1.02f else 1f
    val compactScale = 1f - 0.15f * compactFraction // icons render ~85% size when compact
    val scale by animateFloatAsState(pressScale * compactScale,
        spring(dampingRatio = 0.65f, stiffness = 700f), label = "$label press")
    val tint by animateColorAsState(if (selected) NavInk else NavMuted, tween(180), label = "$label color")
    // Labels grow with large text only up to 1.1×, so all five still fit side by side on a 360dp phone.
    val density = LocalDensity.current
    val labelSize = with(density) { (10.5.dp * density.fontScale.coerceAtMost(1.1f)).toSp() }
    Column(modifier.heightIn(min = 48.dp)
        .selectable(selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
        .semantics { contentDescription = label }.testTag("nav-${label.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) {
            icon(tint)
            if (dot) Box(Modifier.align(Alignment.TopEnd).offset(3.dp, (-2).dp).size(10.dp)
                .background(SpotPalette.Surface, CircleShape).padding(2.dp).background(NavRed, CircleShape))
            else if (badgeCount > 0) {
                Box(Modifier.align(Alignment.TopEnd).offset(10.dp, (-7).dp)
                    .defaultMinSize(19.dp, 19.dp).background(Color(0xFFB63839), CircleShape)
                    .border(1.5.dp, SpotPalette.Surface, CircleShape).padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center) {
                    Text(if (badgeCount > 99) "99+" else badgeCount.toString(), color = Color.White,
                        fontFamily = InterFontFamily, fontSize = 10.spotSp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (showLabel && compactFraction < 0.6f) Text(label, Modifier.padding(top = 3.dp).graphicsLayer { alpha = 1f - compactFraction / 0.6f },
            color = tint, fontFamily = InterFontFamily, fontSize = labelSize, lineHeight = labelSize * 1.25f,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
