package app.ytmon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ytmon.data.Channel
import app.ytmon.data.ChannelCreate
import app.ytmon.data.ChannelPatch
import app.ytmon.data.QualityOverride
import app.ytmon.data.YtRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChannelsState(
    val channels: List<Channel> = emptyList(),
    val loading: Boolean = false,
    val error: Throwable? = null,
    val message: String? = null,
    val scanning: Set<String> = emptySet(),
    val importPreview: String? = null,
    val importFormat: String = "csv",
)

class ChannelsViewModel(private val repo: YtRepository) : ViewModel() {

    private val _state = MutableStateFlow(ChannelsState())
    val state: StateFlow<ChannelsState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            repo.channels()
                .onSuccess { _state.value = _state.value.copy(channels = it, loading = false, error = null) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it) }
        }
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
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun scan(channel: Channel) {
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = _state.value.scanning + channel.channelId)
            repo.scanChannel(channel.channelId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        scanning = _state.value.scanning - channel.channelId,
                        message = "Scanned ${channel.displayTitle}",
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

    fun delete(channel: Channel) {
        viewModelScope.launch {
            repo.deleteChannel(channel.channelId)
                .onSuccess { _state.value = _state.value.copy(message = "Removed ${channel.displayTitle}"); refresh() }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
}