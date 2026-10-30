package com.example.spotapp.ui.theme.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.PostReportReasons
import com.example.spotapp.data.PublishedPost
import com.example.spotapp.data.SpotPostsRepository
import com.example.spotapp.data.userMessage
import com.example.spotapp.ui.theme.components.SpotAuthFieldShape
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.spotAuthFieldColors
import com.example.spotapp.ui.theme.screens.discover.CommunityError
import com.example.spotapp.ui.theme.screens.discover.CommunityFormBody
import com.example.spotapp.ui.theme.screens.discover.CommunityWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Why the member is reporting [post]: one reason, plus details that "Something else" needs. */
@Composable
internal fun ReportPostPage(post: PublishedPost, close: () -> Unit, onReported: () -> Unit) {
    var reason by rememberSaveable(post.id) { mutableStateOf<String?>(null) }
    var details by rememberSaveable(post.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val needsDetails = reason == "other"
    fun send() {
        val chosen = reason ?: return
        busy = true; error = ""
        scope.launch {
            try { SpotPostsRepository.report(post.id, chosen, details); onReported() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { error = userMessage(e, "Couldn't send your report. Try again.") }
            finally { busy = false }
        }
    }
    CommunityWindow("Report post", close, busy) { padding ->
        CommunityFormBody(padding, "Send report", enabled = reason != null && (!needsDetails || details.trim().length >= 5),
            busy = busy, onAction = ::send) {
            Text("Only SPOT's team sees reports. ${post.author.displayName.ifBlank { "The poster" }} won't know it was you.",
                style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
            Column(Modifier.selectableGroup().testTag("post-report-reasons")) {
                PostReportReasons.forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(reason == value, enabled = !busy, role = Role.RadioButton) { reason = value; error = "" }
                        .testTag("post-report-$value"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(reason == value, onClick = null, enabled = !busy,
                            colors = RadioButtonDefaults.colors(selectedColor = SpotPalette.GoldDeep))
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = SpotPalette.Ink)
                    }
                }
            }
            OutlinedTextField(details, { details = it.take(1000); error = "" }, Modifier.fillMaxWidth().padding(top = 4.dp).testTag("post-report-details"),
                label = { Text(if (needsDetails) "What's wrong?" else "Details · optional") },
                supportingText = if (needsDetails) { { Text("At least 5 characters") } } else null,
                minLines = 3, maxLines = 6, enabled = !busy, shape = SpotAuthFieldShape, colors = spotAuthFieldColors())
            CommunityError(error)
        }
    }
}
