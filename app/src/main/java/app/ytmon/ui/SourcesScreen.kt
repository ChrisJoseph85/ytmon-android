package app.ytmon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ytmon.data.QualityOverride
import app.ytmon.data.Source

@Composable
fun SourcesScreen() {
    val vm: SourcesViewModel = ytViewModel { SourcesViewModel(it.repo) }
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var newUrl by remember { mutableStateOf("") }
    var override by remember { mutableStateOf<QualityOverride?>(null) }
    var deleting by remember { mutableStateOf<Source?>(null) }

    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${s.status.enabled} of ${s.status.sources} enabled",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = vm::scanAll, enabled = s.scanning.isEmpty()) {
                        Icon(Icons.Default.Refresh, "Scan all sources")
                    }
                }
                s.status.lastError?.let { InfoNote("Last source error: $it") }
            }

            /**
             * THE Watch Later guardrail. Without server cookies the playlist is not
             * empty — it is unreadable. Showing "0 items" here is the #1 support
             * ticket, so always explain instead.
             */
            if (!s.status.cookiesConfigured) {
                item {
                    InfoNote(
                        "Watch Later needs your YouTube cookies set on the server. " +
                            "Without them it will always read as empty. Set the cookies " +
                            "in Termux (ytmon config) and re-scan."
                    )
                }
            }

            item {
                Column(Modifier.padding(top = 16.dp)) {
                    Text("Add a playlist", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Paste a /playlist?list=… link, or type \"watch_later\".",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text("Playlist link or watch_later") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    QualityPicker(override, { override = it }, Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.preview(if (newUrl.trim() == "watch_later") "watch_later" else newUrl) },
                            enabled = newUrl.isNotBlank() && !s.loading,
                        ) { Text("Preview") }
                        OutlinedButton(onClick = {
                            vm.add(
                                app.ytmon.data.SourcePreview(newUrl.trim(), null, 0, "playlist"),
                                newUrl.trim(),
                                override,
                            )
                        }, enabled = newUrl.isNotBlank()) { Text("Add directly") }
                    }
                }
            }

            s.preview?.let { p ->
                item {
                    Column(Modifier.padding(top = 12.dp)) {
                        InfoNote("${p.title ?: p.sourceId} — ${p.count} item${if (p.count == 1) "" else "s"} found.")
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.add(p, newUrl.trim(), override) }) { Text("Add this playlist") }
                            TextButton(onClick = vm::cancelPreview) { Text("Cancel") }
                        }
                    }
                }
            }

            s.error?.let { item { ErrorPanel(it) } }

            items(s.sources, key = { it.sourceId }) { source ->
                SourceCard(
                    source = source,
                    scanning = source.sourceId in s.scanning,
                    onToggle = { vm.toggleEnabled(source) },
                    onScan = { vm.scan(source) },
                    onDelete = { deleting = source },
                    onOverride = { vm.setOverride(source, it) },
                )
            }
        }
    }

    deleting?.let { source ->
        ConfirmDialog(
            title = "Remove ${source.displayTitle}?",
            message = "Stops monitoring this playlist. Videos already downloaded stay in your library.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = { vm.delete(source) },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QualityPicker(
    value: QualityOverride?,
    onChange: (QualityOverride?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var quality by remember { mutableStateOf(value?.quality ?: "default") }
    var media by remember { mutableStateOf(value?.mediaType ?: "default") }
    var qOpen by remember { mutableStateOf(false) }
    var mOpen by remember { mutableStateOf(false) }
    val current = remember(quality, media) {
        if (quality == "default" && media == "default") null
        else QualityOverride(
            quality = quality.takeIf { it != "default" },
            mediaType = media.takeIf { it != "default" },
        )
    }
    LaunchedEffect(current) { onChange(current) }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton(onClick = { qOpen = true }) { Text("Quality: $quality") }
            DropdownMenu(qOpen, onDismissRequest = { qOpen = false }) {
                (listOf("default") + QUALITY_OPTIONS).forEach { q ->
                    DropdownMenuItem(
                        text = { Text(if (q == "default") "Server default" else "$q${if (q == quality) " ✓" else ""}") },
                        onClick = { quality = q; qOpen = false },
                    )
                }
            }
        }
        Box {
            OutlinedButton(onClick = { mOpen = true }) {
                Text("Type: ${if (media == "default") "server default" else media}")
            }
            DropdownMenu(mOpen, onDismissRequest = { mOpen = false }) {
                listOf("default" to "Server default", "video" to "Video", "audio" to "Audio").forEach { (v, l) ->
                    DropdownMenuItem(
                        text = { Text(l) },
                        onClick = { media = v; mOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: Source,
    scanning: Boolean,
    onToggle: () -> Unit,
    onScan: () -> Unit,
    onDelete: () -> Unit,
    onOverride: (QualityOverride?) -> Unit,
) {
    var override by remember(source.sourceId) { mutableStateOf(source.qualityOverride) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(source.displayTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${source.sourceId} · ${source.kind}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = source.enabled, onCheckedChange = { onToggle() })
            }

            // Live, accurate counts straight from the server.
            Text(
                "${source.counts.downloaded} of ${source.counts.total} downloaded, ${source.counts.failed} failed",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (source.counts.failed > 0) {
                Text(
                    "${source.counts.failed} item${if (source.counts.failed == 1) "" else "s"} could not be downloaded — usually private, removed or age-restricted.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                "Override: ${source.qualityOverride?.let { "quality=${it.quality ?: "server default"}, type=${it.mediaType ?: "server default"}" } ?: "none (uses the global setting)"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            QualityPicker(override, { override = it }, Modifier.padding(top = 6.dp))

            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onScan, enabled = !scanning) { Text(if (scanning) "Scanning…" else "Scan now") }
                TextButton(onClick = onDelete) { Text("Remove") }
            }
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            if (source.lastCheckedAt != null) {
                Text(
                    "Last checked ${source.lastCheckedAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}