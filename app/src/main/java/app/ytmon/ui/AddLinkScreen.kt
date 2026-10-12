package app.ytmon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ytmon.data.FetchResult
import app.ytmon.data.Job
import app.ytmon.data.asEta
import app.ytmon.data.asSize
import app.ytmon.data.asSpeed

@Composable
fun AddLinkScreen() {
    val vm: AddLinkViewModel = ytViewModel { AddLinkViewModel(it.repo) }
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (s.phase) {
                AddLinkState.Phase.INPUT, AddLinkState.Phase.PREVIEW -> InputPhase(s, vm)
                AddLinkState.Phase.DOWNLOADING, AddLinkState.Phase.DONE -> ProgressPhase(s, vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputPhase(s: AddLinkState, vm: AddLinkViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = s.url,
                onValueChange = vm::setUrl,
                label = { Text("YouTube video, playlist or channel URL") },
                placeholder = { Text("https://youtu.be/…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text("Media type", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Video", s.mediaType == "video") { vm.setMediaType("video") }
                FilterPill("Audio", s.mediaType == "audio") { vm.setMediaType("audio") }
            }
            Text(
                if (s.mediaType == "audio") {
                    "Audio downloads are real MP3s with embedded cover art."
                } else {
                    "Video downloads are MP4 files."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        item {
            Text("Quality", style = MaterialTheme.typography.labelLarge)
            Text(
                "A ceiling, not a promise — \"720p\" downloads 720p or lower. " +
                    "A per-source or per-channel override wins over this.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            var open by remember { mutableStateOf(false) }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { open = true }) { Text("${s.quality} (max)") }
                DropdownMenu(open, onDismissRequest = { open = false }) {
                    QUALITY_OPTIONS.forEach { q ->
                        DropdownMenuItem(
                            text = { Text("$q${if (q == s.quality) " ✓" else ""}") },
                            onClick = { vm.setQuality(q); open = false },
                        )
                    }
                }
            }
        }

        item {
            Text("Limit: ${s.limit} items", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = s.limit.toFloat(),
                onValueChange = { vm.setLimit(it.toInt()) },
                valueRange = 1f..50f,
            )
        }

        item {
            Button(
                onClick = vm::preview,
                enabled = !s.loading && s.url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (s.loading) "Working…" else "Preview")
            }
            Text(
                "Preview does not download anything. You confirm before anything is queued.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        item { ErrorPanel(s.error, onRetry = vm::clearError) }

        s.preview?.let { p -> item { PreviewCard(p, s, vm) } }
    }
}

@Composable
private fun PreviewCard(p: FetchResult, s: AddLinkState, vm: AddLinkViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "${p.kind ?: "link"} · resolved to ${p.resolved ?: "—"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Stat("New", p.added.size, MaterialTheme.colorScheme.secondary)
            Stat("Duplicates", p.duplicates.size, MaterialTheme.colorScheme.onSurfaceVariant)
            Stat("Unavailable", p.unavailable.size, MaterialTheme.colorScheme.error)
        }

        if (p.truncated) {
            InfoNote(
                "The server truncated this list. Only the first ${p.added.size + p.duplicates.size} " +
                    "entries were considered — raise the limit to see more."
            )
        }

        if (p.added.isNotEmpty()) {
            Text("Would be added", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            p.added.take(20).forEach { v ->
                Text("• ${v.displayTitle}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            if (p.added.size > 20) {
                Text("…and ${p.added.size - 20} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (p.duplicates.isNotEmpty()) {
            Text(
                "Already in your library (skipped): ${p.duplicates.joinToString(", ").take(200)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (p.unavailable.isNotEmpty()) {
            Text(
                "Unavailable on YouTube (private, removed or age-restricted): ${p.unavailable.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(onClick = vm::confirmDownload, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("Download ${p.added.size} item${if (p.added.size == 1) "" else "s"}")
        }
        TextButton(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth()) { Text("Start over") }
    }
}

@Composable
private fun ProgressPhase(s: AddLinkState, vm: AddLinkViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val result = s.result
        Text(
            if (s.phase == AddLinkState.Phase.DONE) "Finished" else "Downloading…",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (result != null) {
            Text(
                "Queued ${result.queued.size} · duplicates ${result.duplicates.size} · unavailable ${result.unavailable.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // quality is null on a preview, but present on a real download.
            Text(
                "Quality: ${result.quality ?: "server default"} · type: ${result.mediaType ?: s.mediaType}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(Modifier.weight(1f).padding(top = 12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(s.progress.values.toList(), key = { it.videoId }) { job -> JobProgressRow(job) }
        }

        OutlinedButton(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) { Text("Add another link") }
    }
}

@Composable
fun Stat(label: String, value: Int, tint: androidx.compose.ui.graphics.Color) {
    Column {
        Text("$value", style = MaterialTheme.typography.headlineSmall, color = tint, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun JobProgressRow(job: Job, onRetry: (() -> Unit)? = null, onCancel: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(job.videoId, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
            StateChip(job.status)
        }
        if (job.status == "downloading" || job.status == "queued") {
            LinearProgressIndicator(
                progress = { (job.progress / 100.0).coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Text(
                "${job.progress.toInt()}% · ${job.speed.asSpeed()} · ETA ${job.eta.asEta()} · " +
                    "${job.downloadedBytes.asSize()}" + (job.totalBytes?.let { " / ${it.asSize()}" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        job.errorMessage?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }
        if (onRetry != null || onCancel != null) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onRetry?.let { TextButton(onClick = it) { Text("Retry") } }
                onCancel?.let { TextButton(onClick = it) { Text("Cancel") } }
            }
        }
    }
}