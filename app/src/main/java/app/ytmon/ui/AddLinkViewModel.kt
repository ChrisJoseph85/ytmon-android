package app.ytmon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ytmon.data.FetchRequest
import app.ytmon.data.FetchResult
import app.ytmon.data.YtRepository
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val QUALITY_OPTIONS = listOf("144p", "240p", "360p", "480p", "720p", "1080p", "1440p", "2160p", "best")

data class AddLinkState(
    val url: String = "",
    val mediaType: String = "video",
    val quality: String = "best",
    val limit: Int = 10,
    val phase: Phase = Phase.INPUT,
    val preview: FetchResult? = null,
    val result: FetchResult? = null,
    val loading: Boolean = false,
    val error: Throwable? = null,
    val progress: Map<String, app.ytmon.data.Job> = emptyMap(),
) {
    enum class Phase { INPUT, PREVIEW, DOWNLOADING, DONE }
}

class AddLinkViewModel(private val repo: YtRepository) : ViewModel() {

    private val _state = MutableStateFlow(AddLinkState())
    val state: StateFlow<AddLinkState> = _state.asStateFlow()

    private var pollJob: CoroutineJob? = null

    fun setUrl(v: String) { _state.value = _state.value.copy(url = v) }
    fun setMediaType(v: String) { _state.value = _state.value.copy(mediaType = v) }
    fun setQuality(v: String) { _state.value = _state.value.copy(quality = v) }
    fun setLimit(v: Int) { _state.value = _state.value.copy(limit = v.coerceIn(1, 50)) }

    /** Step 1: preview only. download=false queues nothing; quality comes back null. */
    fun preview() {
        val s = _state.value
        if (s.url.isBlank()) return
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            repo.fetchPreview(
                FetchRequest(
                    url = s.url.trim(),
                    download = false,
                    mediaType = s.mediaType,
                    quality = s.quality,
                    limit = s.limit,
                )
            ).onSuccess { r ->
                _state.value = _state.value.copy(loading = false, preview = r, phase = AddLinkState.Phase.PREVIEW)
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e)
            }
        }
    }

    /** Step 2: the real call, with the same options the user previewed. */
    fun confirmDownload() {
        val s = _state.value
        if (s.url.isBlank()) return
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            repo.fetch(
                FetchRequest(
                    url = s.url.trim(),
                    download = true,
                    mediaType = s.mediaType,
                    quality = s.quality,
                    limit = s.limit,
                )
            ).onSuccess { r ->
                _state.value = _state.value.copy(
                    loading = false,
                    result = r,
                    phase = AddLinkState.Phase.DOWNLOADING,
                    progress = r.queued.associateWith { queuedPlaceholder(it) },
                )
                startPolling(r.queued)
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e)
            }
        }
    }

    private fun queuedPlaceholder(id: String) = app.ytmon.data.Job(videoId = id, status = "queued", progress = 0.0)

    /** Poll only while work is outstanding, then stop. Never poll forever. */
    private fun startPolling(ids: List<String>) {
        pollJob?.cancel()
        if (ids.isEmpty()) {
            _state.value = _state.value.copy(phase = AddLinkState.Phase.DONE)
            return
        }
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(1500)
                var anyActive = false
                val snapshot = _state.value.progress
                if (snapshot.isEmpty()) break
                val updated = snapshot.toMutableMap()
                updated.keys.toList().forEach { id ->
                    repo.job(id).onSuccess { j ->
                        updated[id] = j
                        if (j.status == "downloading" || j.status == "queued") anyActive = true
                    }
                }
                _state.value = _state.value.copy(progress = updated)
                if (!anyActive) {
                    _state.value = _state.value.copy(phase = AddLinkState.Phase.DONE)
                    break
                }
            }
        }
    }

    fun reset() {
        pollJob?.cancel()
        _state.value = AddLinkState()
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}