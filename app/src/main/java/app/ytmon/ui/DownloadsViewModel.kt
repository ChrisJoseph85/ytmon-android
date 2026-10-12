package app.ytmon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ytmon.data.Job
import app.ytmon.data.QueueStats
import app.ytmon.data.YtRepository
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DownloadsState(
    val stats: QueueStats = QueueStats(),
    val jobs: List<Job> = emptyList(),
    val filter: String? = null,
    val loading: Boolean = false,
    val error: Throwable? = null,
    val message: String? = null,
    val polling: Boolean = false,
)

class DownloadsViewModel(private val repo: YtRepository) : ViewModel() {

    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state.asStateFlow()

    private var pollJob: CoroutineJob? = null

    init { refresh() }

    fun setFilter(status: String?) {
        _state.value = _state.value.copy(filter = status)
        restartPolling()
    }

    fun refresh() = restartPolling()

    /**
     * Polls every 1.5s ONLY while something is downloading, then stops.
     * Polling forever would just drain the battery.
     */
    private fun restartPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                _state.value = _state.value.copy(loading = true, polling = true)
                val filter = _state.value.filter
                val statsResult = repo.queueStats()
                val jobsResult = repo.queue(filter, null, 100)

                val stats = statsResult.getOrNull()
                val jobs = jobsResult.getOrNull()
                statsResult.exceptionOrNull()?.let { e -> _state.value = _state.value.copy(error = e) }
                jobsResult.exceptionOrNull()?.let { e -> _state.value = _state.value.copy(error = e) }

                val active = (stats?.downloading ?: 0) + (stats?.queued ?: 0)
                _state.value = _state.value.copy(
                    stats = stats ?: _state.value.stats,
                    jobs = jobs ?: _state.value.jobs,
                    loading = false,
                    error = null,
                )

                if (active == 0) {
                    _state.value = _state.value.copy(polling = false)
                    break
                }
                delay(1500)
            }
        }
    }

    /**
     * Retry goes through POST /queue/{id}/retry.
     * Never re-POST /videos/fetch — that would just report the video as a duplicate.
     * `unavailable` items are deliberately not retryable.
     */
    fun retry(job: Job) {
        viewModelScope.launch {
            repo.retry(job.videoId)
                .onSuccess { _state.value = _state.value.copy(message = "Retrying ${job.videoId}"); restartPolling() }
                .onFailure { e -> _state.value = _state.value.copy(error = e) }
        }
    }

    fun cancel(job: Job) {
        viewModelScope.launch {
            repo.cancel(job.videoId)
                .onSuccess { _state.value = _state.value.copy(message = "Cancelled ${job.videoId}"); restartPolling() }
                .onFailure { e -> _state.value = _state.value.copy(error = e) }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
}