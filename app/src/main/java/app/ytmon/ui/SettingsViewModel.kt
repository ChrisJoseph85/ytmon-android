package app.ytmon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ytmon.data.LibrarySettings
import app.ytmon.data.MonitorSettings
import app.ytmon.data.QualitySettings
import app.ytmon.data.RetentionPreview
import app.ytmon.data.RetentionSettings
import app.ytmon.data.ServerInfo
import app.ytmon.data.StorageInfo
import app.ytmon.data.YtRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val quality: QualitySettings = QualitySettings(),
    val monitor: MonitorSettings = MonitorSettings(),
    val retention: RetentionSettings = RetentionSettings(),
    val library: LibrarySettings = LibrarySettings(),
    val storage: StorageInfo = StorageInfo(),
    val info: ServerInfo? = null,
    val preview: RetentionPreview? = null,
    val loading: Boolean = false,
    val error: Throwable? = null,
    val message: String? = null,
    val scanning: Boolean = false,
    /** Set when the user turns delete-on-watch ON, so the UI can demand confirmation. */
    val confirmDeleteFileWhenWatched: Boolean = false,
    val applyingRetention: Boolean = false,
)

class SettingsViewModel(private val repo: YtRepository) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            repo.quality().onSuccess { _state.value = _state.value.copy(quality = it) }
            repo.monitor().onSuccess { _state.value = _state.value.copy(monitor = it) }
            repo.retention().onSuccess { _state.value = _state.value.copy(retention = it) }
            repo.librarySettings().onSuccess { _state.value = _state.value.copy(library = it) }
            repo.storage().onSuccess { _state.value = _state.value.copy(storage = it) }
            repo.serverInfo().onSuccess { _state.value = _state.value.copy(info = it) }
            _state.value = _state.value.copy(loading = false)
        }
    }

    /** Quality/media type: PUT /quality is a MERGE. */
    fun putQuality(quality: String, mediaType: String) {
        _state.value = _state.value.copy(quality = _state.value.quality.copy(quality = quality, mediaType = mediaType))
        viewModelScope.launch {
            repo.putQuality(QualitySettings(quality = quality, mediaType = mediaType))
                .onSuccess { _state.value = _state.value.copy(message = "Saved default quality") }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun putMonitor(m: MonitorSettings) {
        _state.value = _state.value.copy(monitor = m)
        viewModelScope.launch {
            repo.putMonitor(m)
                .onSuccess { _state.value = _state.value.copy(message = "Monitoring saved") }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun scanNow() {
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = true)
            repo.monitorScan()
                .onSuccess { _state.value = _state.value.copy(scanning = false, message = "Scan finished") }
                .onFailure { _state.value = _state.value.copy(scanning = false, error = it) }
        }
    }

    fun setRetentionDays(days: Int?) {
        _state.value = _state.value.copy(retention = _state.value.retention.copy(retentionDays = days))
    }

    fun setMaxItems(n: Int?) {
        _state.value = _state.value.copy(retention = _state.value.retention.copy(maxLibraryItems = n))
    }

    fun requestDeleteOnWatch(on: Boolean) {
        if (on) {
            // Turning this ON is destructive; require an explicit confirmation.
            _state.value = _state.value.copy(confirmDeleteFileWhenWatched = true)
        } else {
            saveRetention(_state.value.retention.copy(deleteFileWhenWatched = false))
        }
    }

    fun confirmDeleteOnWatch() {
        _state.value = _state.value.copy(confirmDeleteFileWhenWatched = false)
        saveRetention(_state.value.retention.copy(deleteFileWhenWatched = true))
    }

    fun cancelDeleteOnWatch() { _state.value = _state.value.copy(confirmDeleteFileWhenWatched = false) }

    /** Show the REAL count before anything is deleted. Read-only. */
    fun previewRetention() {
        viewModelScope.launch {
            _state.value = _state.value.copy(applyingRetention = true)
            repo.retentionPreview()
                .onSuccess { _state.value = _state.value.copy(preview = it, applyingRetention = false) }
                .onFailure { _state.value = _state.value.copy(applyingRetention = false, error = it) }
        }
    }

    /** Only reached after the user has seen the preview and confirmed. */
    fun applyRetention() {
        val preview = _state.value.preview ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(applyingRetention = true)
            repo.applyRetention(dryRun = false)
                .onSuccess {
                    _state.value = _state.value.copy(
                        applyingRetention = false,
                        preview = null,
                        message = "Retention applied to ${preview.wouldDelete} items",
                    )
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(applyingRetention = false, error = it) }
        }
    }

    fun dismissPreview() { _state.value = _state.value.copy(preview = null) }

    private fun saveRetention(r: RetentionSettings) {
        _state.value = _state.value.copy(retention = r)
        viewModelScope.launch {
            repo.putRetention(r)
                .onSuccess {
                    _state.value = _state.value.copy(message = "Retention settings saved")
                    repo.retentionPreview().onSuccess { _state.value = _state.value.copy(preview = it) }
                }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun saveToken(context: android.content.Context, value: String) {
        app.ytmon.data.ServerConfig.saveToken(context, value)
        _state.value = _state.value.copy(
            message = if (value.isBlank()) "Token cleared" else "Token saved",
        )
    }

    fun clearToken(context: android.content.Context) = saveToken(context, "")

    /** Re-checks connectivity after a token change. */
    fun testConnection(context: android.content.Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repo.health()
                .onSuccess { h ->
                    _state.value = _state.value.copy(
                        loading = false,
                        message = "Connected to ${app.ytmon.data.ServerConfig.displayAddress} " +
                            "(server ${h.version ?: "?"}, ${h.status})",
                    )
                    refresh()
                }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e) }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    /**
     * Saves the server address and verifies it. The Retrofit client is rebuilt on
     * save, so the change applies immediately without restarting the app.
     */
    fun saveServerUrl(context: android.content.Context, raw: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            app.ytmon.data.ServerConfig.save(context, raw)
            val result = repo.health()
            result.onSuccess { h ->
                _state.value = _state.value.copy(
                    loading = false,
                    message = "Connected to ${app.ytmon.data.ServerConfig.displayAddress} " +
                        "(server ${h.version ?: "?"}, ${h.status})",
                )
                refresh()
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = e,
                    message = "Saved, but could not connect.",
                )
            }
        }
    }

    fun resetServerUrl(context: android.content.Context) {
        viewModelScope.launch {
            app.ytmon.data.ServerConfig.resetToDefault(context)
            _state.value = _state.value.copy(message = "Reset to ${app.ytmon.data.ServerConfig.displayAddress}")
            refresh()
        }
    }
}