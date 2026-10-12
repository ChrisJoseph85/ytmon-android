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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val FILTERS = listOf(
    null to "All",
    "downloading" to "Downloading",
    "queued" to "Queued",
    "failed" to "Failed",
    "completed" to "Completed",
    "skipped" to "Skipped",
    "unavailable" to "Unavailable",
)

@Composable
fun DownloadsScreen(onOpen: (String) -> Unit) {
    val vm: DownloadsViewModel = ytViewModel { DownloadsViewModel(it.repo) }
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Header chips straight from /queue/stats.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { ChipStat("Queued", s.stats.queued, MaterialTheme.colorScheme.primary) }
                item { ChipStat("Downloading", s.stats.downloading, MaterialTheme.colorScheme.secondary) }
                item { ChipStat("Done", s.stats.completed, MaterialTheme.colorScheme.onSurfaceVariant) }
                item { ChipStat("Failed", s.stats.failed, MaterialTheme.colorScheme.error) }
                item { ChipStat("Skipped", s.stats.skipped, MaterialTheme.colorScheme.onSurfaceVariant) }
                item { ChipStat("Unavailable", s.stats.unavailable, MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(FILTERS) { (value, label) ->
                    FilterPill(label, s.filter == value) { vm.setFilter(value) }
                }
            }

            s.error?.let { ErrorPanel(it, onRetry = { vm.refresh() }, modifier = Modifier.padding(horizontal = 12.dp)) }

            if (s.polling) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (s.jobs.isEmpty() && !s.loading) {
                EmptyState("No jobs", "Nothing is downloading right now.", Modifier.padding(top = 30.dp))
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(s.jobs, key = { it.id }) { job ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        JobProgressRow(
                            job = job,
                            onRetry = if (job.isRetryable) { { vm.retry(job) } } else null,
                            onCancel = if (job.isCancellable) { { vm.cancel(job) } } else null,
                        )
                        if (job.status == "completed") {
                            TextButton(onClick = { onOpen(job.videoId) }) {
                                Text("Open in player")
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipStat(label: String, value: Int, tint: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", style = MaterialTheme.typography.titleMedium, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}