package com.example.spotapp.ui.theme.screens.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.spotapp.data.CampusEvent
import com.example.spotapp.data.SpotTalentCategories
import com.example.spotapp.data.TalentFee
import com.example.spotapp.ui.theme.components.SpotAvatar
import com.example.spotapp.ui.theme.components.SpotDialogStatusBar
import com.example.spotapp.ui.theme.components.SpotIconTile
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.spotAmbientBackground
import java.util.Calendar

// The pieces the event screens share: the poster, ticket stubs, where-you-stand pills and banners, info rows and
// the slots bar. Colors come from SpotPalette so dark mode follows; the poster stays dark in both, like a stage.

internal val PosterInk = Color(0xFF221E14)
internal val PosterText = Color(0xFFFBF8F1)
internal val PosterMuted = Color(0xFFD9D2BF)
internal val PosterTrack = Color(0xFF3A3322)
private val PosterRingOuter = Color(0xFF3A3322)
private val PosterRingMiddle = Color(0xFF4A4029)
private val PosterRingInner = Color(0xFF6B5A2E)
private val PosterSpot = Color(0xFFD4A82A)

/** Grey text for grey pills and banners: darker than Muted so small text keeps its contrast. */
internal val EventGreyInk get() = if (SpotPalette.dark) SpotPalette.Muted else Color(0xFF5E584A)

/** The event's poster, or SPOT's rings when it has none. */
@Composable
internal fun EventPoster(url: String, modifier: Modifier = Modifier) {
    Box(modifier.background(PosterInk)) {
        if (url.isNotBlank()) AsyncImage(url, "Event poster", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .75f, size.height * .42f)
            val stroke = Stroke((size.minDimension / 115f).coerceIn(1f, 2.dp.toPx()))
            drawCircle(PosterRingOuter, size.height * .65f, center, style = stroke)
            drawCircle(PosterRingMiddle, size.height * .435f, center, style = stroke)
            drawCircle(PosterRingInner, size.height * .235f, center, style = stroke)
            drawCircle(PosterSpot, size.height * .104f, center)
        }
    }
}

/** "6 PM" on the hour, "5:30 PM" otherwise. */
internal fun eventClock(time: Long): String =
    eventDate(time, if (Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.MINUTE) == 0) "h a" else "h:mm a")

/** The first part of a location ("Main Quad" of "Main Quad, North Campus"), for one-line rows. */
internal fun shortPlace(location: String): String = location.substringBefore(',').trim().ifBlank { location.trim() }

/** "3 slots left", "No slots left" or "Open slots". */
internal fun slotsLeft(event: CampusEvent): String = event.participantLimit?.let {
    val left = (it - event.participantCount).coerceAtLeast(0)
    if (left == 0L) "No slots left" else "$left ${if (left == 1L) "slot" else "slots"} left"
} ?: "Open slots"

/** The organizer's first name for sentences like "Ben will accept or decline it." */
internal fun organizerFirstName(event: CampusEvent): String =
    event.organizerName.trim().substringBefore(' ').ifBlank { "The organizer" }

/** A ticket stub for event rows: month over day, perforated on its right edge. */
@Composable
internal fun EventStub(time: Long, modifier: Modifier = Modifier, muted: Boolean = false) {
    val perforation = SpotPalette.Ivory
    Column(modifier.width(60.dp).fillMaxHeight().background(if (muted) SpotPalette.Line else SpotPalette.GoldSoft)
        .drawBehind {
            val x = size.width - 1.dp.toPx()
            drawLine(perforation, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(eventDate(time, "MMM").uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp, color = if (muted) EventGreyInk else SpotPalette.GoldInk)
        Text(eventDate(time, "d"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = if (muted) EventGreyInk else SpotPalette.Ink)
    }
}

/**
 * A date block (month, day, optionally the weekday) for cards. [light] keeps dark text in both themes, for a light
 * box laid over a poster.
 */
@Composable
internal fun EventDateBox(time: Long, modifier: Modifier = Modifier, container: Color = SpotPalette.GoldSoft,
    weekday: Boolean = false, width: Dp = 48.dp, light: Boolean = false) {
    Column(modifier.width(width).clip(RoundedCornerShape(if (weekday) 14.dp else 12.dp)).background(container)
        .padding(top = 5.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(eventDate(time, "MMM").uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp, color = if (light) Color(0xFF6B4F00) else SpotPalette.GoldInk)
        Text(eventDate(time, "d"), style = if (weekday) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = if (light) PosterInk else SpotPalette.Ink, lineHeight = 24.sp)
        if (weekday) Text(eventDate(time, "EEE"), style = MaterialTheme.typography.labelSmall,
            color = if (light) Color(0xFF5E584A) else EventGreyInk)
    }
}

/** Where the member stands with an event, as the pill on its row. */
internal enum class EventStanding(val label: String) {
    APPLY("Apply"), APPLIED("Applied"), GOING("Going"), YOURS("Yours"), FULL("Full"), DECLINED("Declined"),
    CLOSED("Closed"), CANCELLED("Cancelled"), ATTENDED("Attended")
}

/**
 * [status] is the member's place from listenMyEventStatus: "going", "pending" or "declined". An application still
 * pending when the event starts can't be answered any more, so it reads Closed.
 */
internal fun eventStanding(event: CampusEvent, status: String?, uid: String, creator: Boolean, now: Long): EventStanding? = when {
    event.cancelled -> EventStanding.CANCELLED
    event.organizerId == uid -> EventStanding.YOURS
    event.startsAt <= now -> when (status) {
        "going" -> EventStanding.ATTENDED
        "pending" -> EventStanding.CLOSED
        "declined" -> EventStanding.DECLINED
        else -> null
    }
    status == "going" -> EventStanding.GOING
    status == "pending" -> EventStanding.APPLIED
    status == "declined" -> EventStanding.DECLINED
    event.isFull -> EventStanding.FULL
    creator -> EventStanding.APPLY
    else -> null
}

@Composable
internal fun EventStatusPill(standing: EventStanding, modifier: Modifier = Modifier) {
    val (fill, ink, line) = when (standing) {
        EventStanding.APPLY -> Triple(SpotPalette.Ink, SpotPalette.OnInk, null)
        EventStanding.APPLIED, EventStanding.YOURS -> Triple(SpotPalette.GoldWash, SpotPalette.GoldInk, SpotPalette.GoldLine)
        EventStanding.GOING -> Triple(SpotPalette.GreenFill, SpotPalette.GreenInk, null)
        EventStanding.CANCELLED -> Triple(SpotPalette.Danger.copy(alpha = .12f), SpotPalette.ErrorText, null)
        else -> Triple(SpotPalette.Track, EventGreyInk, null)
    }
    Text(standing.label, modifier.testTag("event-pill").clip(CircleShape).background(fill)
        .then(if (line != null) Modifier.border(1.dp, line, CircleShape) else Modifier)
        .padding(horizontal = 12.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
}

internal enum class BannerTone { GOLD, GREEN, GREY, RED }

/** A status card under an event's title: an icon in a circle, a bold line and a short explanation. */
@Composable
internal fun EventBanner(icon: ImageVector, title: String, message: String, tone: BannerTone, modifier: Modifier = Modifier,
    extra: (@Composable ColumnScope.() -> Unit)? = null) {
    val fill = when (tone) {
        BannerTone.GOLD -> SpotPalette.GoldWash
        BannerTone.GREEN -> SpotPalette.GreenFill
        BannerTone.GREY -> SpotPalette.Track
        BannerTone.RED -> SpotPalette.Danger.copy(alpha = .1f)
    }
    val line = when (tone) {
        BannerTone.GOLD -> SpotPalette.GoldLine
        BannerTone.GREEN -> SpotPalette.GreenLine
        BannerTone.GREY -> SpotPalette.Line
        BannerTone.RED -> SpotPalette.Danger.copy(alpha = .35f)
    }
    val badge = when (tone) {
        BannerTone.GOLD -> SpotPalette.Gold
        BannerTone.GREEN -> SpotPalette.GreenInk
        BannerTone.GREY -> EventGreyInk
        BannerTone.RED -> SpotPalette.Danger
    }
    Surface(modifier.fillMaxWidth().testTag("event-banner"), shape = RoundedCornerShape(18.dp), color = fill, border = BorderStroke(1.dp, line)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(badge, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(20.dp), tint = if (tone == BannerTone.GOLD) SpotPalette.OnGold else SpotPalette.Surface)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = SpotPalette.Ink)
                    Text(message, style = MaterialTheme.typography.bodyMedium,
                        color = if (tone == BannerTone.RED) SpotPalette.ErrorText else EventGreyInk)
                }
            }
            extra?.invoke(this)
        }
    }
}

/** A gold icon tile beside a bold line and a muted one, as on the event page. */
@Composable
internal fun EventInfoRow(icon: ImageVector, title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SpotIconTile(icon, size = 44.dp, tint = SpotPalette.GoldInk, container = SpotPalette.GoldSoft)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = SpotPalette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** How full the event is. Nothing is drawn for events without a slot limit. */
@Composable
internal fun EventSlotsBar(event: CampusEvent, modifier: Modifier = Modifier, track: Color = SpotPalette.Track) {
    val limit = event.participantLimit ?: return
    val share = if (limit <= 0L) 1f else (event.participantCount.toFloat() / limit).coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(share).fillMaxHeight().clip(CircleShape).background(SpotPalette.Gold))
    }
}

/** A small capitalised tag above the event's title. */
@Composable
internal fun EventTag(text: String, strong: Boolean = false) {
    Text(text.uppercase(), Modifier.clip(CircleShape).background(if (strong) SpotPalette.Ink else SpotPalette.GoldSoft)
        .padding(horizontal = 11.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
        letterSpacing = .4.sp, color = if (strong) SpotPalette.OnInk else SpotPalette.GoldInk, maxLines = 1)
}

/** "Paid · ₱1,500", "Free" or nothing, over the poster. */
@Composable
internal fun EventFeeTag(event: CampusEvent, modifier: Modifier = Modifier) {
    val paid = event.talentFee == TalentFee.PAID
    if (event.talentFee == null) return
    Text(if (paid) "Paid" + if (event.talentFeeBudget.isNotBlank()) " · ${event.talentFeeBudget}" else "" else "Free",
        modifier.widthIn(max = 190.dp).clip(CircleShape).background(if (paid) SpotPalette.Gold else PosterText)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = SpotPalette.OnGold,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun EventSectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SpotPalette.Ink)
        trailing?.invoke()
    }
}

/** Back (or close) and the page's title, as on Applications, New event and Edit event. */
@Composable
internal fun EventPageHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, close: Boolean = false, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack, enabled = enabled) {
            Icon(if (close) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, if (close) "Close" else "Back", tint = SpotPalette.Ink)
        }
        Text(title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A full-screen page for the event screens. Back closes it unless [busy]. */
@Composable
internal fun EventWindow(close: () -> Unit, busy: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) close() }, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, dismissOnBackPress = !busy, dismissOnClickOutside = false)) {
        SpotDialogStatusBar()
        Box(Modifier.fillMaxSize().spotAmbientBackground(SpotPalette.Ivory), content = content)
    }
}

/** The white bar pinned to the bottom of a page, holding its main action. */
@Composable
internal fun EventPinnedBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val line = SpotPalette.Line
    Column(modifier.fillMaxWidth().background(SpotPalette.Surface)
        .drawBehind { drawLine(line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
        .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/** A person's photo, or their initials on a soft tint when they have none (and in previews, which can't load photos). */
@Composable
internal fun EventPersonAvatar(url: String, name: String, uid: String, size: Dp, modifier: Modifier = Modifier) {
    if (url.isNotBlank() && !LocalInspectionMode.current) { SpotAvatar(url, name, modifier.size(size), uid); return }
    val dark = SpotPalette.dark
    val (fill, ink) = when (Math.floorMod(uid.hashCode(), 3)) {
        0 -> SpotPalette.GoldSoft to SpotPalette.GoldInk
        1 -> SpotPalette.GreenFill to SpotPalette.GreenInk
        else -> (if (dark) Color(0xFF232A45) else Color(0xFFE3E6F5)) to (if (dark) Color(0xFFB8C2F0) else Color(0xFF2F3E8C))
    }
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(modifier.size(size).clip(CircleShape).background(fill).semantics { contentDescription = name }, contentAlignment = Alignment.Center) {
        Text(initials.ifBlank { "?" }, style = if (size >= 40.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, color = ink)
    }
}

/** Pick one talent from a short sheet; [anyLabel] adds a first row that clears the choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TalentPickSheet(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit, anyLabel: String? = null) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SpotPalette.Surface) {
        Text("Talent", Modifier.padding(start = 20.dp, bottom = 6.dp), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = SpotPalette.Ink)
        LazyColumn(Modifier.navigationBarsPadding().testTag("talent-sheet"), contentPadding = PaddingValues(bottom = 16.dp)) {
            val options = (if (anyLabel != null) listOf("") else emptyList()) + SpotTalentCategories
            items(options, key = { it.ifBlank { "any" } }) { talent ->
                val chosen = talent == selected
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { this.selected = chosen }
                    .clickable(role = Role.RadioButton) { onPick(talent) }.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (talent.isBlank()) SpotIconTile(Icons.Rounded.AllInclusive, size = 36.dp)
                    else SpotIconTile(talentIcon(talent), size = 36.dp)
                    Text(talent.ifBlank { anyLabel.orEmpty() }, Modifier.weight(1f).padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge, color = SpotPalette.Ink)
                    if (chosen) Icon(Icons.Rounded.Check, "Selected", tint = SpotPalette.GoldDeep)
                }
            }
        }
    }
}
