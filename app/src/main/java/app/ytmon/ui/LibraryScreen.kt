package app.ytmon.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ytmon.data.Video
import app.ytmon.data.asDuration
import app.ytmon.data.asSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpen: (String) -> Unit, onBrowse: () -> Unit) {
    val vm: LibraryViewModel = ytViewModel { LibraryViewModel(it.repo) }
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var deleteFiles by remember { mutableStateOf(true) }

    LaunchedEffect(s.message) {
        s.message?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }

    // Infinite scroll: load the next page when the tail is reached.
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= s.videos.size - 5 && s.videos.isNotEmpty() && !s.loading
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.nextPage() }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Sort control + multi-select action bar (the title lives in the root Scaffold).
            if (s.multiSelect) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { vm.setMultiSelect(false) }) { Text("Cancel") }
                    Text(
                        "${s.selection.size} selected",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.bulkPin(true) }) { Icon(Icons.Default.PushPin, "Pin all") }
                    IconButton(onClick = { deleteFiles = true; vm.deleteSelected(true) }) {
                        Icon(Icons.Default.Delete, "Delete selected")
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(end = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterPill("Sorted: ${s.sort.label}", true) { }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Change sort") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            Text("Sort by", Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall)
                            LibrarySort.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label + if (opt == s.sort) " ✓" else "") },
                                    onClick = { vm.setSort(opt); menu = false },
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = s.search,
                onValueChange = vm::setSearch,
                label = { Text("Search title or channel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(LibraryFilter.entries.toList()) { f ->
                    FilterPill(f.label, f == s.filter) { vm.setFilter(f) }
                }
            }

            s.error?.let { ErrorPanel(it, onRetry = { vm.refresh() }, modifier = Modifier.padding(horizontal = 12.dp)) }

            if (s.videos.isEmpty() && !s.loading && s.error == null) {
                EmptyState(
                    "Nothing here yet",
                    if (s.search.isNotBlank()) "No matches for \"${s.search}\"" else "Add a link, channel or playlist to get started.",
                    Modifier.padding(top = 40.dp),
                )
            }

            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                items(s.videos, key = { it.videoId }) { video ->
                    VideoRow(
                        video = video,
                        selected = video.videoId in s.selection,
                        multiSelect = s.multiSelect,
                        onClick = {
                            if (s.multiSelect) vm.toggleSelection(video.videoId)
                            else if (video.hasFile) onOpen(video.videoId)
                            else onOpen(video.videoId)
                        },
                        onLongClick = { vm.setMultiSelect(true); vm.toggleSelection(video.videoId) },
                        onToggleWatched = { vm.setWatched(video, !video.watched) },
                        onTogglePin = { vm.togglePin(video) },
                    )
                }
                if (s.loading) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
                }
            }
        }
    }

    // Deletion always previews the real file count first. There is no undo or trash.
    s.pendingDelete?.let { pending ->
        ConfirmDialog(
            title = "Delete ${pending.ids.size} video${if (pending.ids.size == 1) "" else "s"}?",
            message = buildString {
                append("This permanently removes ${pending.ids.size} row${if (pending.ids.size == 1) "" else "s"} from the library.\n\n")
                if (deleteFiles) {
                    append("⚠ ${pending.result.filesDeleted} file${if (pending.result.filesDeleted == 1) "" else "s"} will be erased from disk. This cannot be undone.")
                } else {
                    append("The downloaded files will be kept on the server.")
                }
            },
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.confirmDelete(deleteFiles) },
            onDismiss = { vm.cancelDelete() },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoRow(
    video: Video,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleWatched: () -> Unit,
    onTogglePin: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }

    // Swipe the row to mark watched/unwatched. Returning false keeps the row in place.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.StartToEnd) {
                onToggleWatched()
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    if (video.watched) Icons.Default.Check else Icons.Default.Star,
                    null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (multiSelect) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }

            Thumb(
                videoId = video.videoId,
                isAudio = video.isAudio,
                modifier = Modifier.size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    video.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        listOfNotNull(
                            video.duration?.asDuration(),
                            video.qualityHeight?.let { "${it}p" },
                            video.fileSize?.asSize(),
                            video.channelId,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StateChip(video.status)
                    if (video.watched) {
                        Icon(Icons.Default.Check, "Watched", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    }
                    if (video.pinned) {
                        Icon(Icons.Default.PushPin, "Pinned", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    video.errorMessage?.takeIf { it.isNotBlank() && video.status != "completed" }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 1)
                    }
                }
            }

            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Actions") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (video.watched) "Mark unwatched" else "Mark watched") },
                        onClick = { onToggleWatched(); menu = false },
                        leadingIcon = { Icon(Icons.Default.Check, null) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (video.pinned) "Unpin" else "Pin") },
                        onClick = { onTogglePin(); menu = false },
                        leadingIcon = { Icon(Icons.Default.PushPin, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Select for bulk actions") },
                        onClick = { onLongClick(); menu = false },
                        leadingIcon = { Icon(Icons.Default.Star, null) },
                    )
                }
            }
        }
    }
    }
}