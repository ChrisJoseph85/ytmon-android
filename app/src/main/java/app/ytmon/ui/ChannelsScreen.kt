package app.ytmon.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ytmon.data.Channel
import app.ytmon.data.QualityOverride

@Composable
fun ChannelsScreen() {
    val vm: ChannelsViewModel = ytViewModel { ChannelsViewModel(it.repo) }
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    var newUrl by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var importBody by remember { mutableStateOf("") }
    var importFmt by remember { mutableStateOf("csv") }
    var deleting by remember { mutableStateOf<Channel?>(null) }

    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {
            item {
                Text("Add a channel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text("Channel URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.add(newUrl) }, enabled = newUrl.isNotBlank() && !s.loading) { Text("Add") }
                    OutlinedButton(onClick = { importing = !importing }) { Text(if (importing) "Hide import" else "Import") }
                    OutlinedButton(onClick = { copyExport(context, vm, "csv") }) { Text("Export CSV") }
                    OutlinedButton(onClick = { copyExport(context, vm, "json") }) { Text("Export JSON") }
                }
            }

            if (importing) {
                item {
                    Column(Modifier.padding(top = 12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterPill("CSV", importFmt == "csv") { importFmt = "csv" }
                            FilterPill("JSON", importFmt == "json") { importFmt = "json" }
                        }
                        OutlinedTextField(
                            value = importBody,
                            onValueChange = { importBody = it },
                            label = { Text("Paste exported ${importFmt.uppercase()} (max 5 MB)") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Button(
                            onClick = { vm.import(importBody, importFmt) },
                            enabled = importBody.isNotBlank() && !s.loading,
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text("Import") }
                    }
                }
            }

            s.error?.let { item { ErrorPanel(it, onRetry = { vm.refresh() }) } }

            items(s.channels, key = { it.channelId }) { channel ->
                ChannelCard(
                    channel = channel,
                    scanning = channel.channelId in s.scanning,
                    onToggle = { vm.toggleEnabled(channel) },
                    onScan = { vm.scan(channel) },
                    onDelete = { deleting = channel },
                    onOverride = { vm.setOverride(channel, it) },
                )
            }
        }
    }

    deleting?.let { c ->
        ConfirmDialog(
            title = "Remove ${c.displayTitle}?",
            message = "The server stops monitoring this channel. Already-downloaded videos stay in your library.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = { vm.delete(c) },
            onDismiss = { deleting = null },
        )
    }
}

private fun copyExport(context: Context, vm: ChannelsViewModel, fmt: String) {
    vm.export(fmt) { text ->
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ytmon-channels", text))
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    scanning: Boolean,
    onToggle: () -> Unit,
    onScan: () -> Unit,
    onDelete: () -> Unit,
    onOverride: (QualityOverride?) -> Unit,
) {
    var override by remember(channel.channelId) { mutableStateOf(channel.qualityOverride) }

    Surface(Modifier.fillMaxWidth().padding(top = 12.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(channel.displayTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        channel.channelId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = channel.enabled, onCheckedChange = { onToggle() })
            }

            QualityPicker(override, { override = it }, Modifier.padding(top = 8.dp))

            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScan, enabled = !scanning) { Text(if (scanning) "Scanning…" else "Scan now") }
                TextButton(onClick = onDelete) { Text("Remove") }
            }

            channel.lastCheckedAt?.let {
                Text(
                    "Last checked $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}