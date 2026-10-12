package app.ytmon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ytmon.data.BulkDeleteResult
import app.ytmon.data.Video
import app.ytmon.data.VideoPage
import app.ytmon.data.YtRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("All"), UNWATCHED("Unwatched"), WATCHED("Watched"),
    PINNED("Pinned"), VIDEO("Video"), AUDIO("Audio"),
}

enum class LibrarySort(val label: String, val apiSort: String?, val apiOrder: String) {
    NEWEST("Newest", "published_at", "desc"),
    OLDEST("Oldest", "published_at", "asc"),
    TITLE("Title", "title", "asc"),
    SIZE("Size", "file_size", "desc"),
    DURATION("Duration", "duration", "desc"),
}

data class LibraryState(
    val videos: List<Video> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val error: Throwable? = null,
    val search: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.NEWEST,
    val page: Int = 1,
    val pageSize: Int = 50,
    val selection: Set<String> = emptySet(),
    val multiSelect: Boolean = false,
    val pendingDelete: DeletePreview? = null,
    val message: String? = null,
)

data class DeletePreview(
    val ids: List<String>,
    val result: BulkDeleteResult,
)

class LibraryViewModel(private val repo: YtRepository) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init { load() }

    fun setSearch(q: String) {
        _state.value = _state.value.copy(search = q, page = 1)
        searchJob?.cancel()
        // Debounce so each keystroke does not hit the server.
        searchJob = viewModelScope.launch {
            delay(350)
            load()
        }
    }

    fun setFilter(f: LibraryFilter) {
        _state.value = _state.value.copy(filter = f, page = 1)
        load()
    }

    fun setSort(s: LibrarySort) {
        _state.value = _state.value.copy(sort = s, page = 1)
        load()
    }

    fun nextPage() {
        val s = _state.value
        if (s.videos.size >= s.total) return
        _state.value = s.copy(page = s.page + 1, loading = true)
        load(page = _state.value.page, append = true)
    }

    fun refresh() = load()

    fun load(page: Int = _state.value.page, append: Boolean = false) {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            // The live server supports only: search, channel_id, status, kind,
            // watched, sort, order, page, page_size. It has NO media_type and NO
            // pinned query param — unknown params are silently ignored, so asking
            // for them would return the unfiltered list and look like it worked.
            //  - Pinned uses the dedicated GET /videos/pinned endpoint.
            //  - Video/Audio is applied client-side over the fetched page.
            val fetched = if (s.filter == LibraryFilter.PINNED) {
                repo.pinnedVideos().map { VideoPage(items = it, total = it.size, page = 1, pageSize = s.pageSize) }
            } else {
                repo.videos(
                    search = s.search.takeIf { it.isNotBlank() },
                    watched = if (s.filter == LibraryFilter.UNWATCHED) false else if (s.filter == LibraryFilter.WATCHED) true else null,
                    sort = s.sort.apiSort,
                    order = s.sort.apiOrder,
                    page = page,
                    pageSize = s.pageSize,
                )
            }

            fetched.onSuccess { p ->
                val filtered = when (s.filter) {
                    LibraryFilter.VIDEO -> p.items.filter { !it.isAudio }
                    LibraryFilter.AUDIO -> p.items.filter { it.isAudio }
                    else -> p.items
                }
                val current = _state.value
                val merged = if (append) current.videos + filtered else filtered
                _state.value = current.copy(
                    videos = merged.distinctBy { it.videoId },
                    // When filtering client-side the server total no longer describes
                    // the visible set, so report the filtered count instead.
                    total = if (s.filter == LibraryFilter.VIDEO || s.filter == LibraryFilter.AUDIO) filtered.size else p.total,
                    loading = false,
                    error = null,
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e)
            }
        }
    }

    fun toggleSelection(videoId: String) {
        val s = _state.value
        val sel = if (videoId in s.selection) s.selection - videoId else s.selection + videoId
        _state.value = s.copy(selection = sel)
    }

    fun setMultiSelect(on: Boolean) {
        _state.value = _state.value.copy(multiSelect = on, selection = if (on) _state.value.selection else emptySet())
    }

    fun clearSelection() = _state.value.copy(selection = emptySet(), multiSelect = false).let { _state.value = it }

    fun setWatched(video: Video, watched: Boolean) = act("Marked as ${if (watched) "watched" else "unwatched"}") {
        repo.setWatched(video.videoId, watched)
    }

    fun togglePin(video: Video) = act(if (video.pinned) "Unpinned" else "Pinned") {
        repo.setPinned(video.videoId, !video.pinned)
    }

    fun bulkPin(pinned: Boolean) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty()) return
        act(if (pinned) "Pinned ${ids.size} items" else "Unpinned ${ids.size} items") {
            repo.pinMany(ids, pinned)
        }
        clearSelection()
    }

    fun deleteSelected(deleteFiles: Boolean) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            // ALWAYS preview first: files_deleted is the count of files that would be erased.
            repo.bulkDeletePreview(ids, deleteFiles)
                .onSuccess { preview ->
                    _state.value = _state.value.copy(
                        loading = false,
                        pendingDelete = DeletePreview(ids, preview),
                    )
                }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e) }
        }
    }

    fun confirmDelete(deleteFiles: Boolean) {
        val pending = _state.value.pendingDelete ?: return
        _state.value = _state.value.copy(pendingDelete = null, loading = true)
        viewModelScope.launch {
            repo.bulkDelete(pending.ids, deleteFiles)
                .onSuccess { r ->
                    _state.value = _state.value.copy(
                        loading = false,
                        message = "Deleted ${r.deleted} videos, ${r.filesDeleted} files erased",
                    )
                    clearSelection()
                    load()
                }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e) }
        }
    }

    fun cancelDelete() { _state.value = _state.value.copy(pendingDelete = null) }
    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun act(message: String, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            block().onSuccess {
                _state.value = _state.value.copy(message = message)
                load()
            }.onFailure { e -> _state.value = _state.value.copy(error = e) }
        }
    }
}