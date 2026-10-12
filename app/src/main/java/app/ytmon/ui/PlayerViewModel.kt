package app.ytmon.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ytmon.data.FileMetadata
import app.ytmon.data.Video
import app.ytmon.data.YtRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.asFlow
import kotlinx.coroutines.withContext

data class PlayerState(
    val video: Video? = null,
    val metadata: FileMetadata? = null,
    val loading: Boolean = true,
    val error: Throwable? = null,
    val message: String? = null,
    val playing: Boolean = false,
    val exportPercent: Int? = null,
    val exporting: Boolean = false,
    val deletePreview: Boolean = false,
    val deleteFiles: Boolean = true,
) {
    val isAudio: Boolean get() = video?.isAudio ?: false
    val hasFile: Boolean get() = video?.hasFile ?: false
}

class PlayerViewModel(private val repo: YtRepository, private val appContext: Application) : ViewModel() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    lateinit var videoId: String

    fun load(id: String) {
        videoId = id
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val videoResult = repo.videoById(id)
            videoResult.onSuccess { v -> _state.value = _state.value.copy(video = v) }
            videoResult.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e)
                return@launch
            }
            // Metadata first: it tells us size + mime before we hand anything to the player.
            if (_state.value.video?.hasFile == true) {
                repo.fileMetadata(id).onSuccess { m -> _state.value = _state.value.copy(metadata = m) }
            }
            _state.value = _state.value.copy(loading = false)
        }
    }

    fun setPlaying(p: Boolean) { _state.value = _state.value.copy(playing = p) }

    fun markWatched(watched: Boolean) {
        viewModelScope.launch {
            repo.setWatched(videoId, watched)
                .onSuccess {
                    _state.value = _state.value.copy(
                        video = _state.value.video?.copy(watched = watched),
                        message = if (watched) "Marked watched" else "Marked unwatched",
                    )
                }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun togglePin() {
        val v = _state.value.video ?: return
        viewModelScope.launch {
            repo.setPinned(videoId, !v.pinned)
                .onSuccess {
                    _state.value = _state.value.copy(
                        video = _state.value.video?.copy(pinned = !v.pinned),
                        message = if (!v.pinned) "Pinned" else "Unpinned",
                    )
                }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    /** Preview the real file count before any irreversible deletion. */
    fun requestDelete() {
        viewModelScope.launch {
            repo.deleteVideo(videoId, true, dryRun = true)
                .onSuccess { _state.value = _state.value.copy(deletePreview = true) }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }

    fun setDeleteFiles(v: Boolean) { _state.value = _state.value.copy(deleteFiles = v) }

    fun confirmDelete(onDeleted: () -> Unit) {
        val files = _state.value.deleteFiles
        viewModelScope.launch {
            repo.deleteVideo(videoId, files, dryRun = false)
                .onSuccess {
                    _state.value = _state.value.copy(deletePreview = false)
                    onDeleted()
                }
                .onFailure { _state.value = _state.value.copy(deletePreview = false, error = it) }
        }
    }

    fun cancelDelete() { _state.value = _state.value.copy(deletePreview = false) }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    fun shareFailed(t: Throwable) { _state.value = _state.value.copy(error = t) }

    /**
     * Saving to the phone runs as a foreground WorkManager job so it survives
     * rotation and the app being backgrounded. Progress is observed from
     * WorkInfo rather than held in this ViewModel.
     */
    private var observeJob: kotlinx.coroutines.Job? = null

    fun exportToPhone(context: android.content.Context) {
        val v = _state.value.video ?: return
        val mime = _state.value.metadata?.mimeType
        val work = androidx.work.OneTimeWorkRequestBuilder<app.ytmon.worker.ExportWorker>()
            .setInputData(
                androidx.work.Data.Builder()
                    .putString(app.ytmon.worker.ExportWorker.KEY_VIDEO_ID, videoId)
                    .putString(app.ytmon.worker.ExportWorker.KEY_TITLE, v.displayTitle)
                    .putString(app.ytmon.worker.ExportWorker.KEY_MEDIA_TYPE, v.mediaType)
                    .putString(app.ytmon.worker.ExportWorker.KEY_MIME, mime)
                    .build()
            )
            .build()

        app.ytmon.worker.ExportWorker.ensureChannel(appContext)
        androidx.work.WorkManager.getInstance(appContext).enqueue(work)
        _state.value = _state.value.copy(exporting = true, exportPercent = 0)

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            androidx.work.WorkManager.getInstance(appContext)
                .getWorkInfoByIdLiveData(work.id)
                .asFlow()
                .collect { info ->
                    when (info?.state) {
                        androidx.work.WorkInfo.State.RUNNING ->
                            _state.value = _state.value.copy(
                                exporting = true,
                                exportPercent = info.progress.getInt(app.ytmon.worker.ExportWorker.KEY_PROGRESS, 0),
                            )
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            val name = info.outputData.getString(app.ytmon.worker.ExportWorker.KEY_DISPLAY_NAME)
                            _state.value = _state.value.copy(
                                exporting = false,
                                exportPercent = null,
                                message = "Saved to ${if (v.isAudio) "Music" else "Movies"} as $name",
                            )
                            observeJob?.cancel()
                        }
                        androidx.work.WorkInfo.State.FAILED -> {
                            _state.value = _state.value.copy(
                                exporting = false,
                                exportPercent = null,
                                error = IllegalStateException(
                                    info.outputData.getString(app.ytmon.worker.ExportWorker.KEY_ERROR) ?: "Export failed"
                                ),
                            )
                            observeJob?.cancel()
                        }
                        else -> Unit
                    }
                }
        }
    }
}