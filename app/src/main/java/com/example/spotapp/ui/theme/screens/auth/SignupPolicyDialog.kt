package com.example.spotapp

import com.example.spotapp.ui.theme.spotSp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal enum class SignupPolicy(val title: String, val asset: String) {
    TERMS("Terms of Service", "legal/terms.md"),
    PRIVACY("Privacy Policy", "legal/privacy.md")
}

@Composable
internal fun SignupPolicyDialog(policy: SignupPolicy, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val paragraphs = remember(policy) {
        context.assets.open(policy.asset).bufferedReader().use { it.readText() }
            .split(Regex("\\r?\\n\\s*\\r?\\n"))
            .filter { it.isNotBlank() && !it.startsWith("# ") }
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(policy.title, Modifier.weight(1f).padding(vertical = 12.dp),
                        fontWeight = FontWeight.Bold, fontSize = 22.spotSp, color = SignupInk)
                    TextButton(onClick = onDismiss) { Text("Close", color = SignupGoldDark) }
                }
                HorizontalDivider()
                // Long policy documents get their own reader; signup itself stays fixed.
                LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(paragraphs) { paragraph ->
                        val heading = paragraph.startsWith("## ")
                        Text(paragraph.removePrefix("## "),
                            color = if (heading) SignupInk else SignupMuted,
                            fontSize = if (heading) 17.spotSp else 14.spotSp,
                            lineHeight = 22.sp, fontWeight = if (heading) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
