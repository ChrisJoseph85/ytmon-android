package app.ytmon.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import app.ytmon.data.ShareUtil
import app.ytmon.data.asDuration
import app.ytmon.data.asSize
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(videoId: String, onBack: () -> Unit) {
    val vm: PlayerViewModel = ytViewModel(key = "player-$videoId") { PlayerViewModel(it.repo, it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(videoId) { vm.load(videoId) }
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(s.video?.displayTitle ?: "Player", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {

            if (s.loading) {
                LoadingBox(Modifier.heightIn(min = 200.dp))
            }

            s.error?.let { ErrorPanel(it) }

            val video = s.video

            if (video != null) {
                // Branch on media_type, never on the file extension.
                if (s.isAudio) {
                    AudioPlayerView(repo = vmRepo(context), videoId = video.videoId, title = video.displayTitle,
                        subtitle = listOfNotNull(video.channelId, video.duration?.asDuration()).joinToString(" · "),
                        onEnded = { vm.markWatched(true) })
                } else if (s.hasFile) {
                    VideoPlayerView(
                        streamUrl = vmRepo(context).streamUrl(video.videoId),
                        onEnded = { vm.markWatched(true) },
                        onStateChanged = vm::setPlaying,
                    )
                } else {
                    UnplayablePlaceholder(video.status, video.errorMessage)
                }
            }

            Column(Modifier.padding(16.dp)) {
                Text(video?.displayTitle ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                if (video != null) {
                    Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StateChip(video.status)
                        // Show what was actually delivered, not the requested ceiling.
                        if (video.qualityHeight != null) {
                            StateChip("${video.qualityHeight}p")
                        }
                        if (video.watched) {
                            Icon(Icons.Default.Check, "Watched", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        }
                        if (video.pinned) {
                            Icon(Icons.Default.PushPin, "Pinned", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    s.metadata?.let { m ->
                        Column(Modifier.padding(top = 12.dp)) {
                            LabeledRow("File", m.filename ?: "—")
                            LabeledRow("Size", m.sizeBytes.asSize())
                            LabeledRow("Type", m.mimeType ?: "—")
                            LabeledRow("Duration", m.duration?.asDuration() ?: "—")
                        }
                    }

                    video.errorMessage?.takeIf { video.status != "completed" && it.isNotBlank() }?.let {
                        InfoNote(video.errorMessage!!)
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { vm.markWatched(!video.watched) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            Text(if (video.watched) " Mark unwatched" else " Mark watched", Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = vm::togglePin, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PushPin, null, Modifier.size(18.dp))
                            Text(if (video.pinned) " Unpin" else " Pin", Modifier.padding(start = 6.dp))
                        }
                    }

                    // Share LINK is always available and implies nothing about a file.
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { ShareUtil.shareLink(context, video.videoId) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                            Text(" Share link", Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = vm::requestDelete, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                            Text(" Delete", Modifier.padding(start = 6.dp))
                        }
                    }

                    // Share FILE only makes sense when a file actually exists.
                    if (s.hasFile) {
                        Text(
                            "Sharing a file copies it out of Termux first — the server's own path is not reachable by other apps.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val v = video
                                    runCatching {
                                        ShareUtil.shareFile(
                                            context, vmRepo(context), v.videoId, v.displayTitle,
                                            s.metadata?.mimeType,
                                        ) { _, _ -> }
                                    }.onFailure { vm.shareFailed(it) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                            Text(" Share file…", Modifier.padding(start = 6.dp))
                        }

                        Button(
                            onClick = { vm.exportToPhone(context) },
                            enabled = !s.exporting,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                            Text(
                                if (video.isAudio) " Save MP3 to Music" else " Save to Movies",
                                Modifier.padding(start = 6.dp),
                            )
                        }

                        s.exportPercent?.let { pct ->
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            Text(
                                "Saving to the device… $pct%",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }

                    if (!s.hasFile && video.status != "completed") {
                        InfoNote(
                            "No file on disk yet. Status is \"${video.status}\" — downloads happen in the Downloads tab."
                        )
                    }
                }
            }
        }
    }

    if (s.deletePreview) {
        ConfirmDialog(
            title = "Delete this video?",
            message = if (s.deleteFiles) {
                "The library entry and its downloaded file will be permanently erased from the server. There is no trash and no undo."
            } else {
                "The library entry will be removed, but the downloaded file will stay on the server."
            },
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.confirmDelete(onBack) },
            onDismiss = vm::cancelDelete,
        )
    }
}

private fun vmRepo(context: android.content.Context) = (context.applicationContext as app.ytmon.YtMonApp).repo

@Composable
private fun UnplayablePlaceholder(status: String, error: String?) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF101014)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.MusicNote, null, Modifier.size(40.dp), tint = Color.White.copy(alpha = 0.5f))
            Text(status, color = Color.White, modifier = Modifier.padding(top = 8.dp))
            error?.let { Text(it, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/**
 * ExoPlayer streams straight from GET /files/{id}/stream.
 * ExoPlayer issues its own Range requests, so seeking works without
 * pre-downloading the file. No Authorization header is needed on loopback.
 */
@Composable
private fun VideoPlayerView(
    streamUrl: String,
    onEnded: () -> Unit,
    onStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    // ExoPlayer uses its own HTTP stack, so the bearer token must be attached here
    // too or a remote server would answer 401 for the stream itself.
    val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .apply {
            app.ytmon.data.ServerConfig.token?.let {
                setDefaultRequestProperties(mapOf("Authorization" to "Bearer $it"))
            }
        }

    // Keyed on the URL and token so changing either rebuilds the player.
    val exo = remember(streamUrl, app.ytmon.data.ServerConfig.token) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) onEnded()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) = onStateChanged(isPlaying)
            })
        }
    }

    DisposableEffect(Unit) {
        onDispose { exo.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exo
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        update = { it.player = exo },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
    )
}

/** Audio: no video surface, cover art plus metadata. Audio items are real MP3s. */
@Composable
private fun AudioPlayerView(
    repo: app.ytmon.data.YtRepository,
    videoId: String,
    title: String,
    subtitle: String,
    onEnded: () -> Unit,
) {
    val context = LocalContext.current
    val streamUrl = repo.streamUrl(videoId)
    val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .apply {
            app.ytmon.data.ServerConfig.token?.let {
                setDefaultRequestProperties(mapOf("Authorization" to "Bearer $it"))
            }
        }
    val exo = remember(streamUrl, app.ytmon.data.ServerConfig.token) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) onEnded()
                }
            })
        }
    }
    DisposableEffect(exo) { onDispose { exo.release() } }

    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Thumb(
            videoId = videoId,
            isAudio = true,
            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = true
                    controllerShowTimeoutMs = 2000
                }
            },
            update = { it.player = exo },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).padding(horizontal = 24.dp),
        )
    }
}