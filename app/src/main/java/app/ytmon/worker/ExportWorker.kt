package app.ytmon.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ytmon.R
import app.ytmon.data.MediaExporter
import app.ytmon.data.Network
import app.ytmon.data.YtRepository

/**
 * Copies a finished download into MediaStore so it appears in the gallery or the
 * music app.
 *
 * This runs as a foreground worker so it survives rotation and the app being
 * backgrounded. The phone can still suspend us — that is Android, not a bug.
 */
class ExportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: videoId
        val mediaType = inputData.getString(KEY_MEDIA_TYPE) ?: "video"
        val mimeType = inputData.getString(KEY_MIME)

        setForeground(foreground(videoId, title, 0, null))

        val repo = YtRepository()
        var lastPercent = 0

        val outcome = runCatching {
            MediaExporter.export(
                context = applicationContext,
                repo = repo,
                videoId = videoId,
                title = title,
                mediaType = mediaType,
                mimeType = mimeType,
                onProgress = { done, total ->
                    val percent = total?.takeIf { it > 0 }?.let { ((done * 100) / it).toInt() } ?: 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        setProgress(Data.Builder().putInt(KEY_PROGRESS, percent).build())
                        setForeground(foreground(videoId, title, percent, total))
                    }
                },
            )
        }

        return outcome.fold(
            onSuccess = {
                setProgress(Data.Builder().putInt(KEY_PROGRESS, 100).build())
                Result.success(
                    Data.Builder()
                        .putString(KEY_DISPLAY_NAME, it.displayName)
                        .putLong(KEY_BYTES, it.bytes)
                        .putString(KEY_URI, it.uri.toString())
                        .build()
                )
            },
            onFailure = {
                Result.failure(Data.Builder().putString(KEY_ERROR, it.message ?: "Export failed").build())
            },
        )
    }

    private fun foreground(videoId: String, title: String, percent: Int, total: Long?): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Saving \"$title\"")
            .setContentText(if (total != null) "$percent% of ${total / 1024 / 1024} MB" else "Downloading…")
            .setProgress(100, percent, total == null)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID + videoId.hashCode().and(0xFFF), notification)
    }

    companion object {
        const val CHANNEL_ID = "ytmon_export"
        private const val NOTIFICATION_ID = 4200
        const val KEY_VIDEO_ID = "video_id"
        const val KEY_TITLE = "title"
        const val KEY_MEDIA_TYPE = "media_type"
        const val KEY_MIME = "mime"
        const val KEY_PROGRESS = "progress"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_BYTES = "bytes"
        const val KEY_URI = "uri"
        const val KEY_ERROR = "error"

        fun enqueue(context: Context, videoId: String, title: String, mediaType: String, mime: String?) {
            ensureChannel(context)
            val request = OneTimeWorkRequestBuilder<ExportWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_VIDEO_ID, videoId)
                        .putString(KEY_TITLE, title)
                        .putString(KEY_MEDIA_TYPE, mediaType)
                        .putString(KEY_MIME, mime)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Saving to device", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }
}