package com.example.spotapp.ui.theme.screens.discover

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.*
import java.util.Calendar
import java.util.UUID

/** Who an edit to the time, place or talent fee would notify. */
internal data class EventAudience(val performers: Int = 0, val applicants: Int = 0, val invitees: Int = 0) {
    /** "5 performers and 3 applicants", or blank when nobody would be told. */
    val summary: String get() {
        val parts = listOfNotNull(
            performers.takeIf { it > 0 }?.let { "$it ${if (it == 1) "performer" else "performers"}" },
            applicants.takeIf { it > 0 }?.let { "$it ${if (it == 1) "applicant" else "applicants"}" },
            invitees.takeIf { it > 0 }?.let { "$it ${if (it == 1) "invitee" else "invitees"}" })
        return when (parts.size) { 0 -> ""; 1 -> parts[0]; else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last() }
    }
}

/** The event form's fields. Editing starts from [existing] as it is. */
@Stable
internal class EventForm(val existing: CampusEvent?, organizerDefault: String, startDefault: Long) {
    var title by mutableStateOf(existing?.title.orEmpty())
    var description by mutableStateOf(existing?.description.orEmpty())
    var image by mutableStateOf(existing?.imageUrl.orEmpty())
    var category by mutableStateOf(existing?.category.orEmpty())
    var startsAt by mutableLongStateOf(existing?.startsAt ?: startDefault)
    var location by mutableStateOf(existing?.location.orEmpty())
    var organizer by mutableStateOf(existing?.organizer ?: organizerDefault)
    var unlimited by mutableStateOf(existing?.let { it.participantLimit == null } ?: true)
    var limit by mutableStateOf(existing?.participantLimit?.toString() ?: "5")
    var talentFee by mutableStateOf(existing?.talentFee.orEmpty())
    var budget by mutableStateOf(existing?.talentFeeBudget.orEmpty())

    val participantLimit: Long? get() = if (unlimited) null else limit.toLongOrNull()
    val dateChanged get() = existing != null && day(startsAt) != day(existing.startsAt)
    val timeChanged get() = existing != null && clock(startsAt) != clock(existing.startsAt)
    val placeChanged get() = existing != null && location.trim() != existing.location
    val feeChanged get() = existing != null && (talentFee != existing.talentFee.orEmpty() ||
        (talentFee == TalentFee.PAID && budget.trim() != existing.talentFeeBudget))
    val changed: Boolean get() = if (existing == null) title.isNotBlank() || description.isNotBlank() || image.isNotBlank()
        else title != existing.title || description != existing.description || image != existing.imageUrl || category != existing.category ||
            location != existing.location || organizer != existing.organizer || participantLimit != existing.participantLimit ||
            talentFee != existing.talentFee.orEmpty() || budget != existing.talentFeeBudget || startsAt != existing.startsAt

    private fun day(time: Long) = Calendar.getInstance().apply { timeInMillis = time }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
    private fun clock(time: Long) = Calendar.getInstance().apply { timeInMillis = time }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    companion object {
        /** Tomorrow at 6 PM. */
        fun defaultStart(): Long = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        fun saver(existing: CampusEvent?, organizerDefault: String) = mapSaver(save = { form: EventForm ->
            mapOf("title" to form.title, "description" to form.description, "image" to form.image, "category" to form.category,
                "startsAt" to form.startsAt, "location" to form.location, "organizer" to form.organizer, "unlimited" to form.unlimited,
                "limit" to form.limit, "talentFee" to form.talentFee, "budget" to form.budget)
        }, restore = { saved ->
            EventForm(existing, organizerDefault, saved["startsAt"] as Long).apply {
                title = saved["title"] as String; description = saved["description"] as String; image = saved["image"] as String
                category = saved["category"] as String; startsAt = saved["startsAt"] as Long; location = saved["location"] as String
                organizer = saved["organizer"] as String; unlimited = saved["unlimited"] as Boolean; limit = saved["limit"] as String
                talentFee = saved["talentFee"] as String; budget = saved["budget"] as String
            }
        })
    }
}

/**
 * Creating an event, or editing [existing] before it starts. Changing the time, place or talent fee tells
 * [audience]. [onRemove] adds the card at the bottom that cancels the event, or deletes it when [removeDeletes].
 */
@Composable
internal fun EventEditor(profile: SpotUiProfile, close: () -> Unit, saved: (String) -> Unit, existing: CampusEvent? = null,
    audience: EventAudience = EventAudience(), onRemove: (() -> Unit)? = null, removeDeletes: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val organizerDefault = profile.organization.ifBlank { profile.displayName }
    val id = rememberSaveable { existing?.eventId ?: UUID.randomUUID().toString() }
    val form = rememberSaveable(saver = EventForm.saver(existing, organizerDefault)) { EventForm(existing, organizerDefault, EventForm.defaultStart()) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    fun back() { if (form.changed) discard = true else close() }
    fun save() {
        if (busy) return
        val draft = EventDraft(id, form.title, form.description, form.image, form.category, form.startsAt, form.location, form.organizer,
            form.participantLimit, form.talentFee, form.budget)
        val issue = when {
            !form.unlimited && form.limit.toLongOrNull() == null -> "Enter how many performer slots there are, or tick No limit."
            form.talentFee.isBlank() -> "Choose whether performers are paid."
            else -> draft.error()
        }
        if (issue != null) { error = issue; return }
        focus.clearFocus(); busy = true; error = ""; progress = 0f
        scope.launch {
            try {
                val savedId = withTimeout(180_000) {
                    if (existing != null) { DiscoverRepository.shared.updateEvent(context.applicationContext, existing, draft) { progress = it }; existing.eventId }
                    else DiscoverRepository.shared.createEvent(context.applicationContext, draft) { progress = it }
                }
                saved(savedId)
            } catch (_: TimeoutCancellationException) { error = "Saving took too long. Please retry; the same draft won't be created twice." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is IllegalStateException || e is IllegalArgumentException) e.message.orEmpty() else "Couldn't save. Check your connection and retry." }
            finally { busy = false }
        }
    }
    EventWindow(::back, busy) {
        EventEditorScreen(form, profile, audience, busy, progress, error, onBack = ::back, onSave = ::save,
            onRemove = onRemove, removeDeletes = removeDeletes)
    }
    if (discard) SpotConfirmDialog(Icons.Rounded.DeleteOutline, if (existing != null) "Discard your changes?" else "Discard this event?",
        "Your unsaved changes will be lost.", "Discard", onDismiss = { discard = false }, danger = true, dismiss = "Keep editing", onConfirm = close)
}

/** The event form drawn from [form], for the app, previews and the review test. */
@Composable
internal fun EventEditorScreen(form: EventForm, profile: SpotUiProfile, audience: EventAudience, busy: Boolean, progress: Float, error: String,
    onBack: () -> Unit, onSave: () -> Unit, onRemove: (() -> Unit)? = null, removeDeletes: Boolean = false) {
    val context = LocalContext.current
    val editing = form.existing != null
    var talentSheet by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            form.image = uri.toString()
        }
    }
    Column(Modifier.fillMaxSize().imePadding().testTag("event-editor")) {
        EventPageHeader(if (editing) "Edit event" else "New event", onBack, close = !editing, enabled = !busy)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (editing && audience.summary.isNotBlank()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SpotPalette.GoldWash)
                .border(1.dp, SpotPalette.GoldLine, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Icon(Icons.Rounded.NotificationsNone, null, Modifier.size(20.dp), tint = SpotPalette.GoldInk)
                Text("If you change the time, place or talent fee, we'll tell ${audience.summary}.", Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyLarge, color = SpotPalette.Ink)
            }
            PosterField(form.image, enabled = !busy, pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                remove = { form.image = "" })
            FormField(form.title, { form.title = it.take(100) }, "Event title", "e.g. Acoustic Night at the Quad", enabled = !busy,
                modifier = Modifier.testTag("event-title"))
            Column {
                PickerField("Talent", form.category, "Choose a talent", null, Icons.Rounded.ExpandMore, changed = false, enabled = !busy,
                    modifier = Modifier.testTag("event-talent")) { talentSheet = true }
                Text("Creators with this talent see it first.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerField("Date", eventDate(form.startsAt, "EEE, MMM d"), "", Icons.Rounded.CalendarMonth, null, form.dateChanged, !busy,
                    Modifier.weight(1f)) {
                    val c = Calendar.getInstance().apply { timeInMillis = form.startsAt }
                    DatePickerDialog(context, { _, y, m, d -> form.startsAt = Calendar.getInstance().apply { timeInMillis = form.startsAt; set(y, m, d) }.timeInMillis },
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply { datePicker.minDate = System.currentTimeMillis(); show() }
                }
                PickerField("Time", eventDate(form.startsAt, "h:mm a"), "", Icons.Rounded.Schedule, null, form.timeChanged, !busy, Modifier.weight(1f)) {
                    val c = Calendar.getInstance().apply { timeInMillis = form.startsAt }
                    TimePickerDialog(context, { _, h, m -> form.startsAt = Calendar.getInstance().apply { timeInMillis = form.startsAt
                        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }.timeInMillis }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
                }
            }
            FormField(form.location, { form.location = it.take(160) }, "Where", "Venue and campus", enabled = !busy, changed = form.placeChanged,
                modifier = Modifier.testTag("event-location"))
            FormField(form.organizer, { form.organizer = it.take(100) }, "Organizer", "Your organization", enabled = !busy,
                helper = "Shown as “by ${profile.displayName.ifBlank { "you" }} · ${form.organizer.trim().ifBlank { "…" }}”.")
            FormField(form.description, { form.description = it.take(3000) }, "About", "What's the event, who's it for, and what should performers bring?",
                enabled = !busy, singleLine = false, counter = "${form.description.length} / 3,000", modifier = Modifier.testTag("event-about"))
            val accepted = form.existing?.participantCount?.toInt() ?: 0
            SlotsField(form, floor = maxOf(1, accepted), enabled = !busy,
                helper = if (accepted > 0) "You've accepted $accepted, so keep at least $accepted." else "Each Creator you accept takes one slot.")
            FeeField(form, enabled = !busy)
            if (editing && onRemove != null) RemoveCard(removeDeletes, enabled = !busy, onRemove)
        }
        EventPinnedBar {
            CommunityError(error)
            if (busy) Text(if (progress in 0.01f..0.9f) "Uploading poster · ${(progress * 100).toInt()}%" else "Saving…",
                style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
            Box(Modifier.testTag("event-save")) {
                SpotAuthButton(if (editing) "Save changes" else "Post event", loading = busy, enabled = !busy && (!editing || form.changed), onClick = onSave)
            }
            if (!editing) Text("Creators can apply as soon as it's posted.", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
                color = SpotPalette.Muted, textAlign = TextAlign.Center)
        }
    }
    if (talentSheet) TalentPickSheet(form.category, { form.category = it; talentSheet = false }, { talentSheet = false })
}

/** The label above a field, with a gold "Changed" tag when an edit will be announced. */
@Composable
internal fun FormLabel(text: String, changed: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = SpotPalette.Ink)
        if (changed) Text("Changed", Modifier.clip(CircleShape).background(SpotPalette.GoldSoft).padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = SpotPalette.GoldInk)
    }
}

@Composable
internal fun FormField(value: String, onChange: (String) -> Unit, label: String, placeholder: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, singleLine: Boolean = true, changed: Boolean = false, helper: String? = null, counter: String? = null) {
    Column {
        FormLabel(label, changed)
        OutlinedTextField(value, onChange, modifier.fillMaxWidth().then(if (singleLine) Modifier else Modifier.heightIn(min = 128.dp)),
            enabled = enabled, placeholder = { Text(placeholder) }, singleLine = singleLine, minLines = if (singleLine) 1 else 4,
            maxLines = if (singleLine) 1 else 8, textStyle = MaterialTheme.typography.bodyLarge, shape = SpotAuthFieldShape,
            colors = spotAuthFieldColors().copy(unfocusedContainerColor = SpotPalette.Surface,
                unfocusedIndicatorColor = if (changed) SpotPalette.GoldDeep else SpotPalette.Line))
        if (helper != null || counter != null) Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(helper.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
            if (counter != null) Text(counter, style = MaterialTheme.typography.labelMedium, color = SpotPalette.Muted)
        }
    }
}

/** A field that opens a picker: talent, date or time. */
@Composable
internal fun PickerField(label: String, value: String, placeholder: String, icon: ImageVector?, trailing: ImageVector?, changed: Boolean,
    enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier) {
        FormLabel(label, changed)
        Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = SpotAuthFieldShape,
            color = SpotPalette.Surface, border = BorderStroke(if (changed) 2.dp else 1.dp, if (changed) SpotPalette.GoldDeep else SpotPalette.Line)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = SpotPalette.GoldInk); Spacer(Modifier.width(8.dp)) }
                Text(value.ifBlank { placeholder }, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isBlank()) SpotPalette.Muted else SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (trailing != null) Icon(trailing, null, Modifier.size(20.dp), tint = SpotPalette.Muted)
            }
        }
    }
}

/** A dashed "Add a poster" box, or the poster with Change and Remove over it. */
@Composable
private fun PosterField(image: String, enabled: Boolean, pick: () -> Unit, remove: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    if (image.isBlank()) {
        val line = SpotPalette.Muted
        Surface(onClick = pick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(170.dp).testTag("event-poster")
            .drawBehind {
                drawRoundRect(line, cornerRadius = CornerRadius(20.dp.toPx()), style = Stroke(1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))
            }, shape = shape, color = SpotPalette.Surface) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                SpotIconTile(Icons.Rounded.AddPhotoAlternate, size = 48.dp, tint = SpotPalette.GoldInk)
                Text("Add a poster", style = MaterialTheme.typography.titleMedium, color = SpotPalette.Ink)
                Text("Optional · a wide photo works best", style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
            }
        }
    } else Box(Modifier.fillMaxWidth().height(170.dp).clip(shape).testTag("event-poster")) {
        EventPoster(image, Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpotPillButton("Change", pick, enabled = enabled, style = SpotButtonStyle.Secondary, compact = true)
            SpotPillButton("Remove", remove, enabled = enabled, style = SpotButtonStyle.Secondary, compact = true)
        }
    }
}

/** − slots + with a No limit tick box. Fewer than [floor] isn't offered: that many performers are already in. */
@Composable
private fun SlotsField(form: EventForm, floor: Int, enabled: Boolean, helper: String) {
    Column {
        FormLabel("Performer slots")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val value = form.limit.toIntOrNull()
            val active = enabled && !form.unlimited
            Row(Modifier.height(52.dp).clip(SpotAuthFieldShape).background(SpotPalette.Surface).border(1.dp, SpotPalette.Line, SpotAuthFieldShape),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton({ form.limit = ((value ?: floor) - 1).coerceAtLeast(floor).toString() }, enabled = active && (value ?: 0) > floor) {
                    Icon(Icons.Rounded.Remove, "Fewer slots")
                }
                BasicTextField(if (form.unlimited) "" else form.limit, { text -> if (text.all(Char::isDigit)) form.limit = text.take(6) },
                    Modifier.width(52.dp).testTag("event-slots"), enabled = active, singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center, color = SpotPalette.Ink, fontWeight = FontWeight.SemiBold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), cursorBrush = SolidColor(SpotPalette.GoldDeep),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (form.unlimited) Text("–", style = MaterialTheme.typography.titleLarge, color = SpotPalette.Muted)
                            inner()
                        }
                    })
                IconButton({ form.limit = ((value ?: 0) + 1).coerceIn(floor, 100_000).toString() }, enabled = active) {
                    Icon(Icons.Rounded.Add, "More slots")
                }
            }
            Row(Modifier.heightIn(min = 44.dp).toggleable(form.unlimited, enabled = enabled, role = Role.Checkbox) { noLimit ->
                form.unlimited = noLimit
                if (!noLimit && (form.limit.toIntOrNull() ?: 0) < floor) form.limit = maxOf(floor, 5).toString()
            }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(form.unlimited, null, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = SpotPalette.GoldDeep,
                    checkmarkColor = SpotPalette.OnGold))
                Text("No limit", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge, color = SpotPalette.Ink)
            }
        }
        Text(helper, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
    }
}

/** For free only, or with a talent fee and an optional budget. */
@Composable
private fun FeeField(form: EventForm, enabled: Boolean) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FormLabel("Talent fee", form.feeChanged)
        FeeOption(form.talentFee == TalentFee.FREE, "For free only", "Unpaid performance", enabled, Modifier.testTag("event-fee-free"),
            onSelect = { form.talentFee = TalentFee.FREE })
        FeeOption(form.talentFee == TalentFee.PAID, "With talent fee", "Paid performance", enabled, Modifier.testTag("event-fee-paid"),
            onSelect = { form.talentFee = TalentFee.PAID }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Budget · optional", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = SpotPalette.Ink)
                OutlinedTextField(form.budget, { form.budget = it.take(80) }, Modifier.fillMaxWidth().testTag("event-budget"), enabled = enabled,
                    placeholder = { Text("e.g. ₱1,500 per act") }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(12.dp), colors = spotAuthFieldColors())
            }
        }
    }
}

@Composable
internal fun FeeOption(selected: Boolean, title: String, subtitle: String, enabled: Boolean, modifier: Modifier = Modifier, onSelect: () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SpotPalette.Surface,
        border = BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) SpotPalette.Gold else SpotPalette.Line)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected, null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = SpotPalette.GoldDeep))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = SpotPalette.Ink)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
                }
            }
            if (selected && content != null) content()
        }
    }
}

/** The red card at the end of Edit event: cancel the event, or delete it while nobody else is involved. */
@Composable
private fun RemoveCard(deletes: Boolean, enabled: Boolean, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(18.dp)).background(SpotPalette.Danger.copy(alpha = .06f))
        .border(1.dp, SpotPalette.Danger.copy(alpha = .35f), RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (deletes) "Delete event" else "Cancel event", style = MaterialTheme.typography.titleMedium, color = SpotPalette.ErrorText)
        Text(if (deletes) "Nobody has applied or been invited yet, so it's removed for good."
            else "Closes registration and tells everyone in it. Performers keep the event and its chat. This can't be undone.",
            style = MaterialTheme.typography.bodyLarge, color = SpotPalette.Ink)
        OutlinedButton(onRemove, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("event-remove-card"), enabled = enabled, shape = CircleShape,
            border = BorderStroke(1.5.dp, SpotPalette.ErrorText), colors = ButtonDefaults.outlinedButtonColors(containerColor = SpotPalette.Surface,
                contentColor = SpotPalette.ErrorText)) {
            Text(if (deletes) "Delete event" else "Cancel event", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EditorPreview(existing: CampusEvent?, dark: Boolean = false) {
    val form = remember { EventForm(existing, "Indie Studio", EventForm.defaultStart()).apply {
        if (existing == null) { title = "Acoustic Night at the Quad"; category = "Singing"; location = "Main Quad, North Campus"; talentFee = TalentFee.PAID
            budget = "₱1,500 per act"; unlimited = false; limit = "8" }
        else startsAt = existing.startsAt + 3_600_000L
    } }
    SPOTAppTheme(dark = dark) {
        Box(Modifier.background(SpotPalette.Ivory)) {
            EventEditorScreen(form, EventSamples.spotter, EventAudience(5, 3), false, 0f, "", {}, {}, onRemove = if (existing != null) ({}) else null)
        }
    }
}

@Preview(name = "New event", widthDp = 360, heightDp = 1500, fontScale = 1.15f)
@Composable private fun NewEventPreview() = EditorPreview(null)

@Preview(name = "Edit event · dark", widthDp = 360, heightDp = 1500, fontScale = 1.15f)
@Composable private fun EditEventPreview() = EditorPreview(EventSamples.acousticNight(), dark = true)
