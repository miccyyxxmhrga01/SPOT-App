package com.example.spotapp.ui.theme.screens.settings

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.rounded.Send
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import com.example.spotapp.data.userMessage
import com.example.spotapp.data.FeedbackReports
import java.util.UUID
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.example.spotapp.BuildConfig
import com.example.spotapp.data.FeedbackEmail
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Feedback or a bug report, sent to SPOT's team from the person's email app: the message, who sent it, the SPOT
 * version and phone, and an optional screenshot. Nothing is stored in SPOT.
 */
@Composable
internal fun FeedbackPage(account: SpotUiProfile?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bug by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var screenshot by rememberSaveable { mutableStateOf<String?>(null) }
    var opened by rememberSaveable { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    // One id per report, so a retry after a dropped connection can't file it twice; a new one after each send.
    var reportId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var sending by remember { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { screenshot = uri.toString(); error = "" }
    }
    val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    val device = remember { "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}" }
    val sender = account?.let { "${it.displayName} (@${it.username})" } ?: "A SPOT member"

    // Sends the report straight to SPOT's team from inside the app.
    fun send() {
        FeedbackEmail.error(message)?.let { error = it; return }
        if (sending || preparing) return
        sending = true; error = ""; sent = false; opened = false
        scope.launch {
            try {
                withTimeout(90_000) { FeedbackReports.submit(context, reportId, bug, message, screenshot, version, device) }
                message = ""; screenshot = null; sent = true
                reportId = UUID.randomUUID().toString()
            } catch (_: TimeoutCancellationException) { error = "Sending took too long. Check your connection and try again." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = userMessage(e, "Couldn't send your report. Check your connection and try again.") }
            finally { sending = false }
        }
    }

    // The same report as an email from the member's own email app, for anyone who'd rather write to the team.
    fun email() {
        FeedbackEmail.error(message)?.let { error = it; return }
        if (preparing || sending) return
        preparing = true; error = ""
        scope.launch {
            val body = FeedbackEmail.body(message, sender, version, device)
            try {
                val attachment = screenshot?.let { withContext(Dispatchers.IO) { shareableCopy(context, Uri.parse(it)) } }
                val email = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(FeedbackEmail.ADDRESS))
                    putExtra(Intent.EXTRA_SUBJECT, FeedbackEmail.subject(bug, version))
                    putExtra(Intent.EXTRA_TEXT, body)
                    if (attachment != null) {
                        type = attachment.second
                        putExtra(Intent.EXTRA_STREAM, attachment.first)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else type = "text/plain"
                    // Only email apps, not every app that can share text.
                    selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                }
                context.startActivity(email)
                opened = true
            } catch (_: ActivityNotFoundException) {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("SPOT report", "To: ${FeedbackEmail.ADDRESS}\n\n$body"))
                error = "No email app was found. Your report was copied, so you can paste it into an email to ${FeedbackEmail.ADDRESS}."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Couldn't attach that screenshot. Remove it or choose another, then try again." }
            finally { preparing = false }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp)
        .testTag("feedback-page"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KindChoice("Feedback", Icons.Rounded.Lightbulb, !bug, Modifier.weight(1f)) { bug = false }
            KindChoice("Bug", Icons.Rounded.BugReport, bug, Modifier.weight(1f)) { bug = true }
        }
        OutlinedTextField(message, { message = it.take(2000); error = ""; opened = false; sent = false }, Modifier.fillMaxWidth().testTag("feedback-message"),
            label = { Text(if (bug) "What went wrong?" else "Your feedback") },
            placeholder = { Text(if (bug) "What you did, what happened, and what you expected" else "What would make SPOT better?") },
            supportingText = if (message.length > 1800) { { Text("${message.length}/2000") } } else null, minLines = 5, maxLines = 10, enabled = !preparing && !sending,
            shape = SpotAuthFieldShape, colors = spotAuthFieldColors())
        val shot = screenshot
        if (shot == null) SpotPillButton("Add a screenshot", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            icon = Icons.Rounded.AddPhotoAlternate, style = SpotButtonStyle.Secondary, enabled = !preparing, compact = true)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(shot, "Screenshot to attach", Modifier.size(64.dp, 110.dp).clip(RoundedCornerShape(12.dp)).background(SpotPalette.Track),
                contentScale = ContentScale.Crop)
            Column(Modifier.padding(start = 12.dp)) {
                Text("Screenshot attached", style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
                TextButton({ screenshot = null }, enabled = !preparing, contentPadding = PaddingValues(0.dp)) { Text("Remove", color = SpotPalette.Danger) }
            }
        }
        SpotCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = 1.dp) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                SpotIconTile(Icons.Rounded.PhoneAndroid, size = 38.dp)
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Sent with your report", style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
                    Text(sender, style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                    Text("SPOT $version · $device", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                }
            }
        }
        if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall, color = SpotPalette.ErrorText)
        if (sent) Row(Modifier.fillMaxWidth().background(SpotPalette.GreenFill, RoundedCornerShape(14.dp)).padding(12.dp).testTag("feedback-sent"),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = SpotPalette.GreenInk)
            Text("Sent. Thanks for helping improve SPOT!", Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = SpotPalette.Ink)
        }
        if (opened) Row(Modifier.fillMaxWidth().background(SpotPalette.GoldSoft, RoundedCornerShape(14.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = SpotPalette.GoldDeep)
            Text("Tap Send in your email app to finish. Thanks for helping improve SPOT!", Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = SpotPalette.Ink)
            TextButton({ message = ""; screenshot = null; opened = false }) { Text("Clear", color = SpotPalette.GoldInk) }
        }
        Button(::send, Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("feedback-send"), enabled = !preparing && !sending,
            colors = ButtonDefaults.buttonColors(containerColor = SpotPalette.Gold, contentColor = SpotPalette.OnGold)) {
            if (sending) CircularProgressIndicator(Modifier.size(18.dp), color = SpotPalette.OnGold, strokeWidth = 2.dp)
            else Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (sending) "Sending…" else if (bug) "Send bug report" else "Send feedback", fontWeight = FontWeight.SemiBold)
        }
        TextButton(::email, Modifier.align(Alignment.CenterHorizontally).testTag("feedback-email"), enabled = !preparing && !sending) {
            Icon(Icons.Rounded.Email, null, Modifier.size(16.dp), tint = SpotPalette.GoldInk); Spacer(Modifier.width(6.dp))
            Text(if (preparing) "Preparing…" else "Email ${FeedbackEmail.ADDRESS} instead", style = MaterialTheme.typography.labelMedium,
                color = SpotPalette.GoldInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun KindChoice(title: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    Surface(modifier.selectable(selected, role = Role.RadioButton, onClick = onSelect).testTag("feedback-kind-$title"),
        shape = RoundedCornerShape(18.dp), color = if (selected) SpotPalette.GoldWash else SpotPalette.Surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) SpotPalette.GoldLine else SpotPalette.Line)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SpotIconTile(icon, size = 34.dp, container = if (selected) SpotPalette.Surface else SpotPalette.GoldSoft)
            Text(title, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink, maxLines = 1)
        }
    }
}

/** A copy of the chosen screenshot that the email app may read, with its type. */
private fun shareableCopy(context: Context, source: Uri): Pair<Uri, String> {
    val type = context.contentResolver.getType(source)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
    val folder = File(context.cacheDir, "feedback").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val file = File(folder, "spot-screenshot." + if (type == "image/png") "png" else "jpg")
    checkNotNull(context.contentResolver.openInputStream(source)) { "Unreadable screenshot" }.use { input ->
        file.outputStream().use { input.copyTo(it) }
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.postmedia", file) to type
}
