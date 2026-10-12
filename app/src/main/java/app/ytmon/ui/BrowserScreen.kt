package app.ytmon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ytmon.data.TreeItem
import app.ytmon.data.asDuration
import app.ytmon.data.asSize

private val ORDERS = listOf("newest", "oldest", "title", "duration")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(onOpen: (String) -> Unit) {
    val vm: BrowserViewModel = ytViewModel { BrowserViewModel(it.repo) }
    val s by vm.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    // In-app back navigation unwinds the folder stack before leaving the screen.
    BackHandler(enabled = s.stack.isNotEmpty()) { vm.pop() }

    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (s.stack.isNotEmpty()) {
                    IconButton(onClick = { vm.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        vm.current()?.title ?: "Library folders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // Breadcrumb.
                    Text(
                        (listOf("Library") + s.stack.map { it.title }).joinToString(" / "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.load() }) { Icon(Icons.Default.Refresh, "Refresh") }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Tune, "Tree settings") }
            }

            s.error?.let { ErrorPanel(it, onRetry = { vm.load() }, modifier = Modifier.padding(horizontal = 12.dp)) }
            if (s.loading) LoadingBox(Modifier.weight(1f))

            when (val node = vm.current()) {
                null -> FolderList(s, vm)
                is Node.Folder -> SubfolderList(node.title, node.folder, vm)
                is Node.Sub -> ItemList(node.sub, s.limitPerFolder, onOpen)
            }
        }
    }

    if (showSettings) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showSettings = false }, sheetState = sheetState) {
            Column(Modifier.padding(16.dp)) {
                Text("Library layout", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "This changes NEW downloads only — files already on the server are not moved.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LayoutPicker(vm)
                Text("Tree order", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ORDERS.forEach { o -> FilterPill(o, s.order == o) { vm.setOrder(o) } }
                }
                Text(
                    "Items shown per folder: ${s.limitPerFolder}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                TextButton(onClick = { vm.setLimit(10) }) { Text("Show 10") }
                TextButton(onClick = { vm.setLimit(100) }) { Text("Show 100") }
                TextButton(onClick = { vm.setLimit(1000) }) { Text("Show all (1000)") }
            }
        }
    }
}

@Composable
private fun LayoutPicker(vm: BrowserViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    var descriptions by remember { mutableStateOf(mapOf<String, String>()) }
    val repo = appRepo()

    LaunchedEffect(Unit) {
        repo.libraryLayouts().onSuccess { descriptions = it.descriptions }
        repo.librarySettings().onSuccess { vm.setCurrentLayout(it.mediaLayout) }
    }

    Column {
        s.layouts.forEach { key ->
            val selected = s.layout == key
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.applyLayout(key) }
                    .padding(vertical = 8.dp)
                    .then(if (selected) Modifier else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (selected) "● " else "○ ") + (descriptions[key] ?: key),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun FolderList(s: BrowserState, vm: BrowserViewModel) {
    if (s.tree.folders.isEmpty() && !s.loading) {
        EmptyState("No folders yet", "The library tree appears once videos are downloaded.", Modifier.padding(top = 30.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(s.tree.folders, key = { it.name }) { folder ->
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    Modifier.clickable { vm.push(Node.Folder(folder.displayName, folder)) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(folder.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "${folder.itemCount} item${if (folder.itemCount == 1) "" else "s"} · ${folder.totalBytes.asSize()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubfolderList(parentTitle: String, folder: app.ytmon.data.TreeFolder, vm: BrowserViewModel) {
    if (folder.subfolders.isEmpty()) {
        EmptyState("This folder is empty", null, Modifier.padding(top = 30.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(folder.subfolders, key = { it.name }) { sub ->
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    Modifier.clickable { vm.push(Node.Sub(sub.name, sub, parentTitle)) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.secondary)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(sub.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${sub.itemCount} item${if (sub.itemCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemList(sub: app.ytmon.data.TreeSubfolder, limit: Int, onOpen: (String) -> Unit) {
    Column {
        // item_count is the true total even when items are truncated for display.
        if (sub.items.size < sub.itemCount) {
            InfoNote("Showing ${sub.items.size} of ${sub.itemCount} items. Raise \"items per folder\" to see the rest.")
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(sub.items, key = { it.videoId }) { item -> TreeItemRow(item, onOpen) }
        }
    }
}

@Composable
private fun TreeItemRow(item: TreeItem, onOpen: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable { onOpen(item.videoId) }, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(
                item.videoId,
                isAudio = item.isAudio,
                modifier = Modifier.size(width = 80.dp, height = 45.dp),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.displayTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.duration?.asDuration(), item.quality, item.fileSize?.asSize()).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StateChip(item.status)
        }
    }
}

@Composable
private fun appRepo() = (LocalContext.current.applicationContext as app.ytmon.YtMonApp).repo