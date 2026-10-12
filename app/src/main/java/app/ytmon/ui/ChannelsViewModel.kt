package app.ytmon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ytmon.data.Channel
import app.ytmon.data.ChannelCreate
import app.ytmon.data.ChannelPatch
import app.ytmon.data.PolicyBody
import app.ytmon.data.QualityOverride
import app.ytmon.data.SubscriptionImportResult
import app.ytmon.data.SubscriptionsStatus
import app.ytmon.data.Wire
import app.ytmon.data.YtRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ChannelFilter(val label: String) {
    ALL("All"), AUTO("Auto"), MANUAL("Manual"), SCHEDULED("Scheduled");

    /** Server-side ?mode= filter; SCHEDULED has no server equivalent. */
    val modeParam: String? get() = when (this) {
        AUTO -> "auto"
        MANUAL -> "manual"
        else -> null
    }
}

data class ChannelsState(
    val channels: List<Channel> = emptyList(),
    val loading: Boolean = false,
    val error: Throwable? = null,
    val message: String? = null,
    val scanning: Set<String> = emptySet(),
    val importPreview: String? = null,
    val importFormat: String = "csv",
    val filter: ChannelFilter = ChannelFilter.ALL,
    val selection: Set<String> = emptySet(),
    val multiSelect: Boolean = false,
    val subscriptionsStatus: SubscriptionsStatus? = null,
    val importSource: String = "takeout",
    val importResult: SubscriptionImportResult? = null,
    val importing: Boolean = false,
) {
    val automatic: Int get() = channels.count { it.isAuto }
    val manual: Int get() = channels.count { it.isManual }
}

class ChannelsViewModel(private val repo: YtRepository) : ViewModel() {

    private val _state = MutableStateFlow(ChannelsState())
    val state: StateFlow<ChannelsState> = _state.asStateFlow()

    /** Bumped by every [refresh]; stale responses are dropped. */
    private var refreshGeneration = 0

    init {
        refresh()
        loadStatus()
    }

    /**
     * Auto / Manual filter server-side via GET /subscriptions?mode=; Scheduled is
     * not a server concept, so it filters the full channel list on the device.
     *
     * If /subscriptions is unavailable the same list is read from GET /channels and
     * filtered here — the fields are on both, so the screen keeps working either way.
     */
    fun refresh() {
        val filter = _state.value.filter
        // Every mutator calls refresh(), so responses can arrive out of order. Only the
        // newest one may write, or a slow All-load could overwrite the Manual list.
        val generation = ++refreshGeneration
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val mode = filter.modeParam
            val loaded = if (mode == null) {
                repo.channels()
            } else {
                val filtered = repo.subscriptions(mode)
                if (filtered.isSuccess) filtered else repo.channels().map { list ->
                    list.filter { it.mode == mode }
                }
            }
            if (generation != refreshGeneration) return@launch
            loaded
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        channels = list.let { if (filter == ChannelFilter.SCHEDULED) it.filter { c -> c.isScheduled } else it }
                            // Duplicate keys make LazyColumn throw; a duplicate row is never
                            // something the user needs to see twice.
                            .distinctBy { it.channelId },
                        loading = false,
                        error = null,
                    )
                }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it) }
        }
    }

    /** Import status carries cookies_configured and the "last imported …" line. */
    fun loadStatus() {
        viewModelScope.launch {
            repo.subscriptionsStatus()
                .onSuccess { _state.value = _state.value.copy(subscriptionsStatus = it) }
        }
    }

    fun setFilter(f: ChannelFilter) {
        _state.value = _state.value.copy(filter = f, selection = emptySet())
        refresh()
    }

    fun add(url: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repo.createChannel(ChannelCreate(url = url.trim()))
                .onSuccess { _state.value = _state.value.copy(loading = false, message = "Added ${it.displayTitle}"); refresh() }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it) }
        }
    }

    fun toggleEnabled(channel: Channel) {
        viewModelScope.launch {
            // path uses channel_id ("UC..." / "@handle"), never the numeric id
            repo.patchChannel(channel.channelId, ChannelPatch(enabled = !channel.enabled))
                .onSuccess { refresh() }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun setOverride(channel: Channel, override: QualityOverride?) {
        viewModelScope.launch {
            repo.patchChannel(channel.channelId, ChannelPatch(qualityOverride = override))
                .onSuccess { _state.value = _state.value.copy(message = "Updated ${channel.displayTitle}"); refresh() }
                // Re-read on failure too: the card shows local state, and without this a
                // rejected override would sit on screen looking like it was saved.
                .onFailure { _state.value = _state.value.copy(error = it); refresh() }
        }
    }

    /**
     * mode: "auto" downloads new uploads, "manual" only lists them.
     * Note the toggle is the whole policy change here — switching to manual never
     * deletes anything that was already downloaded.
     */
    fun setMode(channel: Channel, mode: String) {
        viewModelScope.launch {
            repo.setChannelPolicy(channel.channelId, PolicyBody.one(mode = mode))
                .onSuccess {
                    _state.value = _state.value.copy(
                        message = "${channel.displayTitle} is now ${if (mode == "manual") "manual" else "automatic"}",
                    )
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    /** One PATCH carries the enable flag, the day chips and the time. */
    fun setSchedule(
        channel: Channel,
        enabled: Boolean,
        days: List<String>,
        time: String?,
        clearTime: Boolean = false,
    ) {
        viewModelScope.launch {
            repo.setChannelPolicy(
                channel.channelId,
                PolicyBody.one(
                    scheduleEnabled = enabled,
                    scheduleDays = days,
                    scheduleTime = time,
                    clearScheduleTime = clearTime,
                ),
            )
                .onSuccess {
                    _state.value = _state.value.copy(message = "Schedule saved for ${channel.displayTitle}")
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun scan(channel: Channel) {
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = _state.value.scanning + channel.channelId)
            repo.scanChannel(channel.channelId)
                .onSuccess { outcome ->
                    _state.value = _state.value.copy(
                        scanning = _state.value.scanning - channel.channelId,
                        // held is not an error: the uploads were found, just not due yet.
                        message = outcome.heldSummary?.let { "Scanned ${channel.displayTitle} · $it" }
                            ?: "Scanned ${channel.displayTitle}",
                    )
                    refresh()
                }
                .onFailure {
                    _state.value = _state.value.copy(scanning = _state.value.scanning - channel.channelId, error = it)
                }
        }
    }

    fun export(fmt: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            repo.exportChannels(fmt)
                .onSuccess { body ->
                    val text = body.string()
                    _state.value = _state.value.copy(message = "Exported ${text.lines().count() - 1} channels")
                    onReady(text)
                }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun import(body: String, fmt: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repo.importChannels(fmt, body)
                .onSuccess {
                    _state.value = _state.value.copy(loading = false, message = "Imported channels")
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it) }
        }
    }

    // ---------- SUBSCRIPTION IMPORT ----------

    fun setImportSource(source: String) {
        _state.value = _state.value.copy(importSource = source, error = null)
    }

    /** Takeout is the default because it needs nothing configured on the server. */
    fun importSubscriptions(source: String, data: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(importing = true, error = null, importResult = null)
            repo.importSubscriptions(source, data?.takeIf { it.isNotBlank() })
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        importing = false,
                        importResult = result,
                        // Also a snackbar: the sheet can be dismissed while this runs.
                        message = "Imported ${result.imported} channel${if (result.imported == 1) "" else "s"} from ${result.source}",
                    )
                    refresh()
                    loadStatus()
                }
                .onFailure { _state.value = _state.value.copy(importing = false, error = it) }
        }
    }

    // ---------- MULTI-SELECT ----------

    fun toggleSelection(channelId: String) {
        val s = _state.value
        val sel = if (channelId in s.selection) s.selection - channelId else s.selection + channelId
        _state.value = s.copy(selection = sel)
    }

    fun setMultiSelect(on: Boolean) {
        _state.value = _state.value.copy(multiSelect = on, selection = if (on) _state.value.selection else emptySet())
    }

    fun clearSelection() { _state.value = _state.value.copy(selection = emptySet(), multiSelect = false) }

    /**
     * Bulk mode / schedule for the selection. Sends the "UC…" identifiers, the same
     * form the per-channel path takes.
     *
     * The ids come from the selection itself, not from the loaded list: under a filter
     * the loaded list is a subset, and intersecting would silently skip selected
     * channels that happen to be filtered out.
     */
    fun bulkPolicy(
        mode: String? = null,
        scheduleEnabled: Boolean? = null,
        scheduleDays: List<String>? = null,
        scheduleTime: String? = null,
    ) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repo.setChannelPolicyBulk(
                PolicyBody.bulk(ids, mode = mode, scheduleEnabled = scheduleEnabled, scheduleDays = scheduleDays, scheduleTime = scheduleTime),
            )
                .onSuccess {
                    val n = it.updated
                    val plural = "channel${if (n == 1) "" else "s"}"
                    val what = when {
                        mode != null -> "$n $plural set to ${if (mode == "manual") "manual" else "automatic"}"
                        scheduleEnabled == true -> "Schedule set on $n $plural"
                        scheduleEnabled == false -> "Schedule turned off on $n $plural"
                        else -> "Updated $n $plural"
                    }
                    _state.value = _state.value.copy(message = what, selection = emptySet(), multiSelect = false)
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun delete(channel: Channel) {
        viewModelScope.launch {
            repo.deleteChannel(channel.channelId)
                .onSuccess { _state.value = _state.value.copy(message = "Removed ${channel.displayTitle}"); refresh() }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
}