package app.ytmon.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Moves a server file into the phone's real media collections so the gallery /
 * music apps can see it.
 *
 * The server's own media dir lives inside Termux and is NOT visible to other apps,
 * so the bytes are always streamed through the API into MediaStore.
 * On API 29+ this needs no storage permission at all.
 */
object MediaExporter {

    data class Result(val uri: Uri, val bytes: Long, val displayName: String)

    suspend fun export(
        context: Context,
        repo: YtRepository,
        videoId: String,
        title: String,
        mediaType: String,
        mimeType: String?,
        onProgress: suspend (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val isVideo = mediaType == "video"
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        val safeTitle = title.replace(Regex("""[\\/:*?"<>|\n\r]"""), "_").trim().take(80).ifBlank { videoId }
        val ext = when {
            mimeType?.contains("mpeg") == true && !isVideo -> "mp3"
            isVideo -> "mp4"
            else -> "bin"
        }
        val displayName = "$safeTitle.$ext"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: if (isVideo) "video/mp4" else "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create the media entry")

        var written = 0L
        try {
            resolver.openOutputStream(uri)?.use { out ->
                val r = repo.downloadTo(videoId, out) { done, total -> onProgress(done, total) }
                written = r.getOrThrow()
            } ?: throw IllegalStateException("Could not open the media entry for writing")

            // IS_PENDING=0 publishes the file to the gallery/music apps.
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            Result(uri, written, displayName)
        } catch (t: Throwable) {
            // Never leave a half-written pending row behind.
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }
}

/**
 * Shares an ACTUAL file, not a link.
 *
 * Downloads /files/{id}/download into cacheDir/exports/, exposes it through a
 * content:// URI, and deletes the cache copy once the chooser has been handed the URI.
 */
object ShareUtil {

    fun shareLink(context: Context, videoId: String) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(intent, "Share link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun shareFile(
        context: Context,
        repo: YtRepository,
        videoId: String,
        title: String,
        mimeType: String?,
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Clear previous temp copies so the cache cannot grow without bound.
        dir.listFiles()?.forEach { it.delete() }
        val ext = when {
            mimeType?.contains("mpeg") == true && mimeType?.startsWith("audio") == true -> "mp3"
            mimeType?.startsWith("video") == true -> "mp4"
            else -> "bin"
        }
        val safe = title.replace(Regex("""[\\/:*?"<>|\n\r]"""), "_").trim().take(60).ifBlank { videoId }
        val file = File(dir, "$safe.$ext")

        withContext(Dispatchers.IO) {
            file.outputStream().use { out -> repo.downloadTo(videoId, out, onProgress).getOrThrow() }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType ?: "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Share file")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        // The receiver has its own permission grant; drop our copy shortly after.
        scheduleCleanup(dir)
    }

    private fun scheduleCleanup(dir: File) {
        Thread {
            Thread.sleep(120_000)
            dir.listFiles()?.forEach { runCatching { it.delete() } }
        }.apply { isDaemon = true }.start()
    }
}