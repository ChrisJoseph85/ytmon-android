package app.ytmon.ui

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ytmon.data.Channel
import app.ytmon.data.QualityOverride
import app.ytmon.data.SCHEDULE_DAYS
import app.ytmon.data.SubscriptionImportResult
import app.ytmon.data.SubscriptionsStatus
import app.ytmon.data.Wire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Short chips for the seven day codes the server accepts. */
private val DAY_CHIPS = SCHEDULE_DAYS.zip(listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"))

private fun dayLabel(code: String): String = DAY_CHIPS.firstOrNull { it.first == code }?.second ?: code

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var bulkSchedule by remember { mutableStateOf(false) }
    var subscriptionImport by remember { mutableStateOf(false) }

    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {

            if (s.multiSelect) {
                item {
                    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.setMultiSelect(false) }) { Text("Cancel") }
                            Text(
                                "${s.selection.size} selected",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.bulkPolicy(mode = "auto") }) { Text("Make auto") }
                            OutlinedButton(onClick = { vm.bulkPolicy(mode = "manual") }) { Text("Make manual") }
                            Button(onClick = { bulkSchedule = true }, enabled = s.selection.isNotEmpty()) {
                                Text("Set schedule")
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        buildString {
                            append("${s.channels.size} channel${if (s.channels.size == 1) "" else "s"}")
                            // The split only describes the whole list, so it is only true
                            // when nothing is filtered out.
                            if (s.filter == ChannelFilter.ALL) append(" · ${s.automatic} auto, ${s.manual} manual")
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    LastImportLine(s.subscriptionsStatus, Modifier.padding(top = 2.dp))
                }

                if (s.loading) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(ChannelFilter.entries.toList()) { f ->
                            FilterPill(f.label, s.filter == f) { vm.setFilter(f) }
                        }
                    }
                }

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
                        OutlinedButton(onClick = { importing = !importing }) {
                            Text(if (importing) "Hide import" else "Import list…")
                        }
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

                item {
                    OutlinedButton(
                        onClick = { importing = false; vm.loadStatus(); subscriptionImport = true },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Import YouTube subscriptions…") }
                }
            }

            s.error?.let { item { ErrorPanel(it, onRetry = { vm.refresh() }) } }

            if (s.channels.isEmpty() && !s.loading && s.error == null) {
                item {
                    val filtered = s.filter != ChannelFilter.ALL
                    EmptyState(
                        if (filtered) "No ${s.filter.label.lowercase()} channels" else "No channels yet",
                        if (filtered) {
                            "Nothing here matches this filter."
                        } else {
                            "Add one by URL, or import your YouTube subscriptions from a Takeout file."
                        },
                        Modifier.padding(top = 24.dp),
                    )
                }
            }

            items(s.channels, key = { it.channelId }) { channel ->
                ChannelCard(
                    channel = channel,
                    selected = channel.channelId in s.selection,
                    multiSelect = s.multiSelect,
                    scanning = channel.channelId in s.scanning,
                    // A plain tap must NOT select: a stray tap would otherwise sit in the
                    // selection and be picked up by the next long-press's bulk action.
                    onClick = { if (s.multiSelect) vm.toggleSelection(channel.channelId) },
                    onLongClick = { vm.setMultiSelect(true); vm.toggleSelection(channel.channelId) },
                    onToggle = { vm.toggleEnabled(channel) },
                    onMode = { vm.setMode(channel, it) },
                    onSchedule = { enabled, days, time, clearTime ->
                        vm.setSchedule(channel, enabled, days, time, clearTime)
                    },
                    onScan = { vm.scan(channel) },
                    onDelete = { deleting = channel },
                    onOverride = { vm.setOverride(channel, it) },
                )
            }
        }
    }

    if (bulkSchedule) {
        BulkScheduleSheet(
            count = s.selection.size,
            onDismiss = { bulkSchedule = false },
            onApply = { enabled, days, time ->
                vm.bulkPolicy(scheduleEnabled = enabled, scheduleDays = days, scheduleTime = time)
                bulkSchedule = false
            },
        )
    }

    if (subscriptionImport) {
        ImportSheet(onDismiss = { subscriptionImport = false })
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

/**
 * §5 of the server update: every import is stored server-side, which is why the
 * channel list is still here after the YouTube cookies expire. Saying so up front
 * stops the "my subscriptions vanished" support ticket.
 */
@Composable
private fun LastImportLine(status: SubscriptionsStatus?, modifier: Modifier = Modifier) {
    val at = status?.lastImportAt
    if (at == null) return
    Text(
        // No source reported means no source claimed — guessing "takeout" would lie
        // about a feed import.
        "Last imported ${prettyStamp(at)}${status.lastImportSource?.takeIf { it.isNotBlank() }?.let { " from $it" } ?: ""} — " +
            "the server keeps a copy, so this list stays even if the YouTube cookies expire.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(
    channel: Channel,
    selected: Boolean,
    multiSelect: Boolean,
    scanning: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: () -> Unit,
    onMode: (String) -> Unit,
    onSchedule: (Boolean, List<String>, String?, Boolean) -> Unit,
    onScan: () -> Unit,
    onDelete: () -> Unit,
    onOverride: (QualityOverride?) -> Unit,
) {
    // Keyed on the server value too, so a failed PATCH (or any change made elsewhere)
    // re-syncs the card instead of leaving an unsaved value showing as saved.
    var override by remember(channel.channelId, channel.qualityOverride) {
        mutableStateOf(channel.qualityOverride)
    }
    var editing by remember(channel.channelId) { mutableStateOf(false) }

    // Persist a genuine user change only. On mount `override` equals the server value,
    // so this stays quiet until the picker actually changes something.
    LaunchedEffect(override) {
        if (override != channel.qualityOverride) onOverride(override)
    }

    Surface(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (multiSelect) {
                    Checkbox(checked = selected, onCheckedChange = { onClick() })
                }
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

            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterPill("Auto", channel.isAuto) { if (channel.isManual) onMode("auto") }
                FilterPill("Manual", channel.isManual) { if (channel.isAuto) onMode("manual") }
                TextButton(onClick = { editing = !editing }) {
                    Text(if (editing) "Hide schedule" else "Schedule", style = MaterialTheme.typography.labelMedium)
                }
            }

            /**
             * Manual is NOT "paused": uploads are still discovered and listed in the
             * library with status "discovered". The user fetches them one by one.
             */
            if (channel.isManual) {
                Text(
                    "Manual — uploads are listed in your library, not downloaded. " +
                        "Tap one in the library to fetch it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (editing) {
                ScheduleEditor(
                    enabled = channel.scheduleEnabled,
                    days = channel.scheduleDays,
                    time = channel.scheduleTime,
                    canSchedule = channel.isAuto,
                    onSave = { en, d, t, clear -> onSchedule(en, d, t, clear); editing = false },
                )
            } else {
                ScheduleSummary(channel, Modifier.padding(top = 6.dp))
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

/**
 * due_now / next_due are computed by the server and read as-is — recomputing them
 * on the device is how a channel ends up showing "not due" while the server is
 * happily downloading it.
 */
@Composable
private fun ScheduleSummary(channel: Channel, modifier: Modifier = Modifier) {
    val due = when {
        !channel.isAuto -> null
        !channel.scheduleEnabled -> "Downloads run on every scan"
        channel.dueNow -> "Due now — downloads on the next scan"
        channel.nextDue != null -> "Next due ${prettyStamp(channel.nextDue)}"
        else -> "Scheduled, but no time set yet"
    }
    val schedule = "${if (channel.isScheduled) "Scheduled" else "No schedule"} · " +
        (if (channel.scheduleDays.isEmpty()) "every day" else channel.scheduleDays.joinToString(" ") { dayLabel(it) }) +
        (channel.scheduleTime?.let { " at $it" } ?: "")

    Column(modifier) {
        Text(
            // A schedule only means something once downloads are automatic.
            if (channel.isManual) "$schedule — unused while manual" else schedule,
            style = MaterialTheme.typography.labelMedium,
        )
        due?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditor(
    enabled: Boolean,
    days: List<String>,
    time: String?,
    canSchedule: Boolean,
    onSave: (Boolean, List<String>, String?, Boolean) -> Unit,
) {
    var on by remember { mutableStateOf(enabled) }
    var picked by remember { mutableStateOf(days) }
    var at by remember { mutableStateOf(time) }
    var cleared by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Only download when due", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Scheduling gates the download only — uploads are still listed on the days they appear.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = on, onCheckedChange = { on = it }, enabled = canSchedule)
        }

        if (canSchedule && on) {
            Row(
                Modifier
                    .padding(top = 10.dp)
                    // Seven chips do not fit a narrow card, so they scroll rather than clip.
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DAY_CHIPS.forEach { (day, label) ->
                    FilterPill(label, day in picked) {
                        picked = if (day in picked) picked - day else picked + day
                    }
                }
            }
            Text(
                if (picked.isEmpty()) "No days ticked = every day" else "On " + picked.joinToString(" ") { dayLabel(it) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (h, m) = parseHhMm(at)
                OutlinedButton(onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> at = "%02d:%02d".format(hour, minute); cleared = false },
                        h,
                        m,
                        true,
                    ).show()
                }) { Text("Time: ${at ?: "not set"}") }
                if (at != null) {
                    TextButton(onClick = { at = null; cleared = true }) { Text("Clear") }
                }
            }
        } else if (!canSchedule) {
            Text(
                "Switch this channel to Auto first — manual channels never download on their own.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            // Turning the schedule off leaves it configured, so an empty day list
            // (every day) and a null time are sent as well — a switch left behind is
            // what comes back when it is switched on again.
            TextButton(onClick = { onSave(false, emptyList(), null, true) }) { Text("Clear schedule") }
            Button(onClick = { onSave(on, picked, at, cleared) }, enabled = canSchedule) {
                Text("Save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkScheduleSheet(
    count: Int,
    onDismiss: () -> Unit,
    onApply: (Boolean, List<String>, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var on by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf(emptyList<String>()) }
    var at by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(16.dp)) {
            Text("Set schedule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Applies to $count selected channel${if (count == 1) "" else "s"}.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Only download when due", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = { on = it })
            }

            if (on) {
                Row(
                    Modifier
                        .padding(top = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DAY_CHIPS.forEach { (day, label) ->
                        FilterPill(label, day in picked) {
                            picked = if (day in picked) picked - day else picked + day
                        }
                    }
                }
                Text(
                    if (picked.isEmpty()) "No days ticked = every day" else "On " + picked.joinToString(" ") { dayLabel(it) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                val (h, m) = parseHhMm(at)
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, hour, minute -> at = "%02d:%02d".format(hour, minute) }, h, m, true).show()
                    },
                    modifier = Modifier.padding(top = 10.dp),
                ) { Text("Time: ${at ?: "leave unchanged"}") }
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onApply(on, picked, at) }) { Text("Apply") }
            }
        }
    }
}

/**
 * Subscription import. Takeout is the DEFAULT and the recommended path because it
 * needs nothing configured on the server; the feed option is only enabled when the
 * server reports cookies, so the user never meets a raw 400 import_failed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(onDismiss: () -> Unit) {
    val vm: ChannelsViewModel = ytViewModel { ChannelsViewModel(it.repo) }
    val s by vm.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var body by remember { mutableStateOf("") }
    var readError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (text.isNullOrBlank()) readError = "Could not read that file."
                else { body = text; readError = null }
            }
        }
    }

    val takeout = s.importSource != "feed"

    /**
     * Validating a Takeout paste means parsing the whole file — hundreds of KB to a
     * few MB. Doing that inline would run it on every keystroke AND on every
     * recomposition (the Import button asks too), so it is debounced and moved off
     * the main thread; the button stays disabled until [checked] matches what is typed.
     */
    var takeoutProblem by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(body, takeout) {
        if (!takeout || body.isBlank()) {
            takeoutProblem = null
            checked = null
            return@LaunchedEffect
        }
        val text = body
        delay(350)
        val problem = withContext(Dispatchers.Default) { Wire.takeoutProblem(text) }
        // The effect is cancelled when body changes, but a parse already past its last
        // suspension point can still land — never report a verdict about old text.
        if (body == text) {
            takeoutProblem = problem
            checked = text
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Text("Import subscriptions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                if (takeout) {
                    "Google Takeout → YouTube and Google Play → YouTube → history → subscriptions.json. " +
                        "Nothing has to be configured on the server."
                } else {
                    "Reads your subscriptions straight from YouTube with the server's cookies. " +
                        "Takeout is the safer option — it needs no cookies at all."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Takeout file", takeout) { vm.setImportSource("takeout") }
                FilterPill("Feed (needs cookies)", !takeout) { vm.setImportSource("feed") }
            }

            if (!takeout && s.subscriptionsStatus?.cookiesConfigured == false) {
                Column(Modifier.padding(top = 10.dp)) {
                    InfoNote(
                        "The server has no YouTube cookies, so a feed import would fail. Set YTMON_COOKIES_FILE " +
                            "in Termux, or use the Takeout file instead — it needs no cookies at all.",
                    )
                }
            }

            if (takeout) {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it; readError = null },
                    label = { Text("Paste subscriptions.json") },
                    minLines = 4,
                    isError = takeoutProblem != null || readError != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clip.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                        if (text.isNullOrBlank()) readError = "The clipboard is empty."
                        else { body = text; readError = null }
                    }) { Text("Paste") }
                    TextButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                        Text("Choose file…")
                    }
                    if (body.isNotBlank()) {
                        TextButton(onClick = { body = ""; readError = null }) { Text("Clear") }
                    }
                }
                // Only trust the verdict while it is about the text on screen — during the debounce
                // window it can still describe the previous paste.
                val note = readError ?: takeoutProblem.takeIf { checked == body }
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }

            LastImportLine(s.subscriptionsStatus, Modifier.padding(top = 8.dp))

            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Close") }
                Button(
                    onClick = { vm.importSubscriptions(s.importSource, if (takeout) body else null) },
                    enabled = !s.importing && when {
                        // Takeout: only after the current text has been validated clean.
                        takeout -> body.isNotBlank() && checked == body && takeoutProblem == null
                        // Feed: a missing cookie file is a guaranteed 400 import_failed.
                        // Left enabled when the status is unknown (older server), because
                        // the error hint then explains it better than a dead button.
                        else -> s.subscriptionsStatus?.cookiesConfigured != false
                    },
                ) {
                    if (s.importing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    Text("Import")
                }
            }

            s.importResult?.let {
                ImportResultCard(it, Modifier.padding(top = 12.dp))
            }

            s.error?.let { ErrorPanel(it, modifier = Modifier.padding(top = 12.dp)) }

            Text(
                "Imported channels start in Manual, so importing 300 subscriptions does not start 300 downloads. " +
                    "Switch the ones you want to Auto on this screen.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ImportResultCard(result: SubscriptionImportResult, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Imported from ${result.source}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "${result.imported} imported · ${result.updated} updated · ${result.skipped} skipped · ${result.total} in the file",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (result.errors.isNotEmpty()) {
                Text(
                    "${result.errors.size} problem${if (result.errors.size == 1) "" else "s"}:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                result.errorMessages.take(10).forEach {
                    Text("• $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                if (result.errors.size > 10) {
                    Text(
                        "…and ${result.errors.size - 10} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** "2026-10-12T08:30:00+02:00" -> "2026-10-12 08:30". Unrecognised input passes through. */
private fun prettyStamp(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val m = Regex("""^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})""").find(iso) ?: return iso
    return "${m.groupValues[1]} ${m.groupValues[2]}"
}

private fun parseHhMm(time: String?): Pair<Int, Int> {
    val parts = time?.split(":") ?: return 9 to 0
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return 9 to 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return 9 to 0
    return h.coerceIn(0, 23) to m.coerceIn(0, 59)
}