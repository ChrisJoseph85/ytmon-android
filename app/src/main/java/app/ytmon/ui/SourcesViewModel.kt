package app.ytmon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ytmon.data.QualityOverride
import app.ytmon.data.Source
import app.ytmon.data.SourceCreate
import app.ytmon.data.SourcePatch
import app.ytmon.data.SourcePreview
import app.ytmon.data.SourceStatus
import app.ytmon.data.YtRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SourcesState(
    val sources: List<Source> = emptyList(),
    val status: SourceStatus = SourceStatus(),
    val loading: Boolean = false,
    val error: Throwable? = null,
    val message: String? = null,
    val scanning: Set<String> = emptySet(),
    val preview: SourcePreview? = null,
    val editing: Source? = null,
)

class SourcesViewModel(private val repo: YtRepository) : ViewModel() {

    private val _state = MutableStateFlow(SourcesState())
    val state: StateFlow<SourcesState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            repo.sources().onSuccess { s -> _state.value = _state.value.copy(sources = s, error = null) }
            repo.sourcesStatus().onSuccess { st -> _state.value = _state.value.copy(status = st, loading = false) }
            repo.sourcesStatus().onFailure { e -> _state.value = _state.value.copy(loading = false, error = e) }
        }
    }

    fun preview(url: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repo.previewSource(url.trim())
                .onSuccess { p -> _state.value = _state.value.copy(preview = p, loading = false) }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e) }
        }
    }

    fun add(preview: SourcePreview, rawUrl: String, override: QualityOverride?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repo.createSource(SourceCreate(url = rawUrl.trim(), title = preview.title, qualityOverride = override))
                .onSuccess {
                    _state.value = _state.value.copy(
                        loading = false,
                        preview = null,
                        message = "Added ${preview.title ?: preview.sourceId}",
                    )
                    refresh()
                }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e) }
        }
    }

    fun toggleEnabled(source: Source) {
        viewModelScope.launch {
            repo.patchSource(source.sourceId, SourcePatch(enabled = !source.enabled))
                .onSuccess { refresh() }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun setOverride(source: Source, override: QualityOverride?) {
        viewModelScope.launch {
            repo.patchSource(source.sourceId, SourcePatch(qualityOverride = override))
                .onSuccess {
                    _state.value = _state.value.copy(message = "Quality override updated for ${source.displayTitle}")
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun scan(source: Source) {
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = _state.value.scanning + source.sourceId, error = null)
            // A re-scan that adds nothing is CORRECT, not a failure.
            repo.scanSource(source.sourceId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        scanning = _state.value.scanning - source.sourceId,
                        message = "Scanned ${source.displayTitle}. Anything already downloaded is skipped.",
                    )
                    refresh()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(scanning = _state.value.scanning - source.sourceId, error = e)
                }
        }
    }

    fun scanAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = _state.value.scanning + "*", error = null)
            repo.scanAllSources()
                .onSuccess { _state.value = _state.value.copy(scanning = emptySet(), message = "Scanned all sources"); refresh() }
                .onFailure { _state.value = _state.value.copy(scanning = emptySet(), error = it) }
        }
    }

    fun delete(source: Source) {
        viewModelScope.launch {
            repo.deleteSource(source.sourceId)
                .onSuccess { _state.value = _state.value.copy(message = "Removed ${source.displayTitle}"); refresh() }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun cancelPreview() { _state.value = _state.value.copy(preview = null) }
    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
}