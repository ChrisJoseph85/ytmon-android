package app.ytmon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ytmon.data.LibraryTree
import app.ytmon.data.TreeFolder
import app.ytmon.data.TreeItem
import app.ytmon.data.TreeSubfolder
import app.ytmon.data.YtRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One level of the library tree. Folders push, back pops. */
sealed interface Node {
    val title: String
    data class Folder(override val title: String, val folder: TreeFolder) : Node
    data class Sub(override val title: String, val sub: TreeSubfolder, val parentTitle: String) : Node
}

data class BrowserState(
    val tree: LibraryTree = LibraryTree(),
    val stack: List<Node> = emptyList(),
    val order: String = "newest",
    val limitPerFolder: Int = 100,
    val layout: String = "channel_id",
    val layouts: List<String> = listOf("channel_id", "channel_title", "channel_source", "flat"),
    val loading: Boolean = false,
    val error: Throwable? = null,
)

class BrowserViewModel(private val repo: YtRepository) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repo.libraryTree(_state.value.order, _state.value.limitPerFolder)
                .onSuccess { t -> _state.value = _state.value.copy(tree = t, loading = false) }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e) }
        }
    }

    fun setOrder(order: String) {
        _state.value = _state.value.copy(order = order, stack = emptyList())
        load()
    }

    fun setLimit(limit: Int) {
        _state.value = _state.value.copy(limitPerFolder = limit.coerceIn(1, 1000), stack = emptyList())
        load()
    }

    fun push(node: Node) { _state.value = _state.value.copy(stack = _state.value.stack + node) }

    /** Back pops one level; the root is the folder list itself. */
    fun pop(): Boolean {
        val s = _state.value
        if (s.stack.isEmpty()) return false
        _state.value = s.copy(stack = s.stack.dropLast(1))
        return true
    }

    fun current(): Node? = _state.value.stack.lastOrNull()

    fun consumeBack(): Boolean = pop()

    fun setCurrentLayout(layout: String) {
        _state.value = _state.value.copy(layout = layout)
    }

    /** Applies to NEW downloads only — the server does not move existing files. */
    fun applyLayout(layout: String) {
        _state.value = _state.value.copy(layout = layout)
        viewModelScope.launch {
            repo.putLibrarySettings(app.ytmon.data.LibrarySettingsPatch(mediaLayout = layout))
                .onSuccess { load() }
                .onFailure { _state.value = _state.value.copy(error = it) }
        }
    }
}