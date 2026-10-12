package app.ytmon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import app.ytmon.data.MonitorSettings
import app.ytmon.data.asSize
import app.ytmon.data.MonitorCheckRequest

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = ytViewModel { SettingsViewModel(it.repo) }
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ---------- DOWNLOAD DEFAULTS ----------
            Section("Download quality") {
                Text(
                    "Quality is a ceiling: \"720p\" downloads 720p or lower.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var qOpen by remember { mutableStateOf(false) }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { qOpen = true }) { Text("${s.quality.quality} (max)") }
                    DropdownMenu(qOpen, onDismissRequest = { qOpen = false }) {
                        QUALITY_OPTIONS.forEach { q ->
                            DropdownMenuItem(
                                text = { Text("$q${if (q == s.quality.quality) " ✓" else ""}") },
                                onClick = { vm.putQuality(q, s.quality.mediaType); qOpen = false },
                            )
                        }
                    }
                }
                Text("Default media type", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill("Video", s.quality.mediaType == "video") { vm.putQuality(s.quality.quality, "video") }
                    FilterPill("Audio", s.quality.mediaType == "audio") { vm.putQuality(s.quality.quality, "audio") }
                }
            }

            HorizontalDivider()

            // ---------- MONITORING ----------
            Section("Monitoring") {
                SwitchRow("Monitor channels", s.monitor.enabled) { vm.putMonitor(s.monitor.copy(enabled = it)) }
                LabeledRow("Check every (minutes, 1–1440)", s.monitor.intervalMinutes.toString())
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60, 240).forEach { m ->
                        FilterPill("${m}m", s.monitor.intervalMinutes == m) {
                            vm.putMonitor(s.monitor.copy(intervalMinutes = m))
                        }
                    }
                }
                LabeledRow("Window (hours)", s.monitor.windowHours.toString())
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(12, 24, 48, 168).forEach { h ->
                        FilterPill("${h}h", s.monitor.windowHours == h) {
                            vm.putMonitor(s.monitor.copy(windowHours = h))
                        }
                    }
                }
                SwitchRow("Future uploads only", s.monitor.futureOnly) { vm.putMonitor(s.monitor.copy(futureOnly = it)) }
                Text("Content filter", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all", "video", "shorts").forEach { f ->
                        FilterPill(f, s.monitor.contentFilter == f) { vm.putMonitor(s.monitor.copy(contentFilter = f)) }
                    }
                }
                Button(
                    onClick = vm::scanNow,
                    enabled = !s.scanning,
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text(if (s.scanning) "Scanning…" else "Scan now") }
                Text(
                    "A manual scan hits YouTube through the server and can take a while.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // ---------- DELETION ----------
            Section("Automatic deletion") {
                SwitchRow(
                    "Delete the file when I finish watching",
                    s.retention.deleteFileWhenWatched,
                ) { vm.requestDeleteOnWatch(it) }
                Text(
                    "Off by default. When on, the file is permanently erased as soon as you finish watching it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("Keep for (days, blank = forever)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                var days by remember(s.retention.retentionDays) {
                    mutableStateOf(s.retention.retentionDays?.toString() ?: "")
                }
                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it.filter(Char::isDigit) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )

                Text("Maximum library items (blank = unlimited)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                var maxItems by remember(s.retention.maxLibraryItems) {
                    mutableStateOf(s.retention.maxLibraryItems?.toString() ?: "")
                }
                OutlinedTextField(
                    value = maxItems,
                    onValueChange = { maxItems = it.filter(Char::isDigit) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )

                Button(
                    onClick = {
                        vm.setRetentionDays(days.toIntOrNull())
                        vm.setMaxItems(maxItems.toIntOrNull())
                        // Saving is followed by a preview so the user sees the real number.
                        vm.previewRetention()
                    },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("Save and preview impact") }

                Text(
                    "Retention is time-driven: it runs about every 6 hours and never on server restart. " +
                        "Pinned videos are always protected from retention.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            HorizontalDivider()

            // ---------- STORAGE ----------
            Section("Storage") {
                LabeledRow("Files", s.storage.totalFiles.toString())
                LabeledRow("Total", s.storage.totalBytes.asSize())
                LabeledRow("Unwatched", s.storage.unwatchedBytes.asSize())
                LabeledRow("Watched", s.storage.watchedBytes.asSize())
                LabeledRow("Orphaned", s.storage.orphanedBytes.asSize())
                LabeledRow("Free on device", s.storage.freeBytes.asSize())
                Text(
                    "The library itself lives inside Termux; these numbers describe the server's media directory.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            HorizontalDivider()

            // ---------- SERVER ADDRESS ----------
            Section("Server address") {
                Text(
                    "Where the ytmon server lives. Default is this phone's own loopback address.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var url by remember { mutableStateOf(app.ytmon.data.ServerConfig.baseUrl) }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("127.0.0.1:8765") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveServerUrl(context, url) }, enabled = !s.loading) {
                        Text(if (s.loading) "Testing…" else "Save & test")
                    }
                    OutlinedButton(onClick = { vm.resetServerUrl(context) }) { Text("Reset") }
                }
                Text(
                    "You can type \"127.0.0.1:8765\" or a full http:// URL — /api/v1 is added for you. " +
                        "Currently using ${app.ytmon.data.ServerConfig.displayAddress}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (!app.ytmon.data.ServerConfig.isLoopback(url)) {
                    InfoNote(
                        "Anything other than this phone's own loopback address is treated as a REMOTE " +
                            "client by the server. Remote access needs a bearer token — add one below."
                    )
                }

                // ---------- TOKEN ----------
                var token by remember { mutableStateOf(app.ytmon.data.ServerConfig.token.orEmpty()) }
                Text(
                    "Bearer token",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    if (app.ytmon.data.ServerConfig.token.isNullOrBlank()) {
                        "No token set. Loopback does not need one — leave this empty."
                    } else {
                        "A token is saved and sent as \"Authorization: Bearer …\"."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveToken(context, token); vm.testConnection(context) }) {
                        Text("Save token")
                    }
                    OutlinedButton(onClick = {
                        token = ""
                        vm.clearToken(context)
                    }) { Text("Clear token") }
                }
                Text(
                    "Create one from localhost on the server, for example in Termux: " +
                        "curl -X POST http://127.0.0.1:8765/api/v1/tokens " +
                        "(that endpoint refuses remote callers). The token is stored on the " +
                        "phone, so you only enter it once.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            HorizontalDivider()

            // ---------- SERVER ----------
            Section("Server") {
                s.info?.let {
                    LabeledRow("Version", "${it.version ?: "?"} (API ${it.apiVersion ?: "?"})")
                    LabeledRow("yt-dlp", it.ytDlp ?: "—")
                    LabeledRow("ffmpeg", it.ffmpeg ?: "—")
                    LabeledRow("Bind", "${it.host}:${it.port}")
                    LabeledRow("LAN access", if (it.allowLan) "allowed" else "disabled")
                    LabeledRow("Token required", if (it.authRequired) "yes (loopback is exempt)" else "no")
                }
                Text(
                    "The app always talks to http://127.0.0.1:8765. Loopback is trusted, so no token is sent. " +
                        "Using the LAN IP from the phone would be treated as a remote client and fail with 401.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    // Turning delete-on-watch ON needs an explicit, spelled-out confirmation.
    if (s.confirmDeleteFileWhenWatched) {
        ConfirmDialog(
            title = "Erase files after watching?",
            message = "Every video you watch to the end will have its file permanently deleted from the server. " +
                "There is no trash and no undo — only the library row is kept. Pinned videos are protected.",
            confirmLabel = "Enable",
            destructive = true,
            onConfirm = vm::confirmDeleteOnWatch,
            onDismiss = vm::cancelDeleteOnWatch,
        )
    }

    // Retention impact preview: read-only, real numbers, before anything is applied.
    s.preview?.let { p ->
        ConfirmDialog(
            title = "Apply retention now?",
            message = buildString {
                append("${p.wouldDelete} video${if (p.wouldDelete == 1) "" else "s"} would be deleted, freeing ${p.bytes.asSize()}.\n\n")
                append("${p.completedTotal} completed items are in scope.\n")
                if (p.videoIds.isNotEmpty()) append("\nPinned videos are never included.")
            },
            confirmLabel = "Apply now",
            destructive = p.wouldDelete > 0,
            onConfirm = vm::applyRetention,
            onDismiss = vm::dismissPreview,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Column(Modifier.padding(top = 8.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}