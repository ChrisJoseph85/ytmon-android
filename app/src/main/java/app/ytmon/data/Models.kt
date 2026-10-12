package app.ytmon.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models mirror the LIVE server responses verified against v1.0.0.
 *
 * Conventions:
 *  - Kotlin properties are camelCase; the wire format is pinned with @SerialName
 *    so the exact snake_case the server sends is preserved.
 *  - Nullability is intentional: the server returns null for anything not yet
 *    known (e.g. title/duration/quality on a queued job).
 */

@Serializable
data class Video(
    val id: Int = 0,
    @SerialName("video_id") val videoId: String,
    @SerialName("channel_id") val channelId: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val duration: Int? = null,
    @SerialName("is_short") val isShort: Boolean = false,
    val kind: String = "unknown",
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    val quality: String? = null,
    @SerialName("quality_height") val qualityHeight: Int? = null,
    val watched: Boolean = false,
    @SerialName("watched_at") val watchedAt: String? = null,
    val status: String = "discovered",
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("media_type") val mediaType: String = "video",
    @SerialName("thumbnail_path") val thumbnailPath: String? = null,
    val pinned: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val isAudio: Boolean get() = mediaType == "audio"
    val hasFile: Boolean get() = status == "completed" && !filePath.isNullOrBlank()
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: videoId
}

@Serializable
data class VideoPage(
    val items: List<Video> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 50,
)

@Serializable
data class Job(
    val id: Int = 0,
    @SerialName("video_id") val videoId: String,
    @SerialName("channel_id") val channelId: String? = null,
    val status: String = "queued",
    val quality: String? = null,
    val progress: Double = 0.0,
    val speed: Double? = null,
    @SerialName("downloaded_bytes") val downloadedBytes: Long = 0,
    @SerialName("total_bytes") val totalBytes: Long? = null,
    val eta: Double? = null,
    val attempts: Int = 0,
    @SerialName("next_attempt_at") val nextAttemptAt: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
) {
    /**
     * Retry is valid from failed / skipped only. `unavailable` means the content
     * is private or removed, so it must never be retried or offered to the user.
     */
    val isRetryable: Boolean get() = status == "failed" || status == "skipped"
    val isCancellable: Boolean get() = status == "queued"
}

@Serializable
data class FileMetadata(
    @SerialName("video_id") val videoId: String,
    val filename: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("mime_type") val mimeType: String? = null,
    val quality: String? = null,
    @SerialName("quality_height") val qualityHeight: Int? = null,
    val duration: Int? = null,
    /** The server sends an epoch *string* here (e.g. "1791727631.02"), not an ISO date. */
    @SerialName("modified_at") val modifiedAt: String? = null,
    @SerialName("supports_ranges") val supportsRanges: Boolean = false,
)

@Serializable
data class HealthResponse(
    val status: String = "unknown",
    val version: String? = null,
    val database: Boolean = false,
    val workers: Workers = Workers(),
    val monitor: MonitorSettings? = null,
)

@Serializable
data class Workers(
    val download: WorkerState = WorkerState(),
    val monitor: MonitorWorker = MonitorWorker(),
    val sources: SourcesWorker = SourcesWorker(),
    val retention: RetentionWorker = RetentionWorker(),
)

@Serializable
data class WorkerState(val active: Int = 0, val concurrency: Int = 0)

@Serializable
data class MonitorWorker(
    val enabled: Boolean = false,
    @SerialName("interval_minutes") val intervalMinutes: Int = 15,
    @SerialName("window_hours") val windowHours: Int = 24,
    @SerialName("future_only") val futureOnly: Boolean = false,
    @SerialName("content_filter") val contentFilter: String = "all",
    @SerialName("last_success_at") val lastSuccessAt: String? = null,
    @SerialName("last_error") val lastError: String? = null,
    val running: Boolean = false,
)

@Serializable
data class SourcesWorker(
    val sources: Int = 0,
    val enabled: Int = 0,
    val running: Boolean = false,
    @SerialName("last_error") val lastError: String? = null,
    /** false => Watch Later cannot be read. Must be surfaced, never shown as "empty". */
    @SerialName("cookies_configured") val cookiesConfigured: Boolean = false,
)

@Serializable
data class RetentionWorker(
    val running: Boolean = false,
    @SerialName("last_run") val lastRun: String? = null,
    @SerialName("last_result") val lastResult: String? = null,
)

@Serializable
data class ServerInfo(
    val version: String? = null,
    @SerialName("api_version") val apiVersion: String? = null,
    val platform: String? = null,
    val python: String? = null,
    @SerialName("yt_dlp") val ytDlp: String? = null,
    val ffmpeg: String? = null,
    val host: String? = null,
    val port: Int = 0,
    @SerialName("allow_lan") val allowLan: Boolean = false,
    @SerialName("media_dir") val mediaDir: String? = null,
    @SerialName("auth_required") val authRequired: Boolean = true,
)

@Serializable
data class FetchRequest(
    val url: String,
    val download: Boolean = true,
    @SerialName("media_type") val mediaType: String = "video",
    val quality: String? = null,
    val limit: Int? = null,
    val priority: Int? = null,
    val thumbnail: Boolean = true,
)

@Serializable
data class FetchResult(
    val kind: String? = null,
    val resolved: String? = null,
    val added: List<Video> = emptyList(),
    val queued: List<String> = emptyList(),
    val duplicates: List<String> = emptyList(),
    val unavailable: List<String> = emptyList(),
    val truncated: Boolean = false,
    /** Intentionally nullable: the server returns null when download=false (preview). */
    val quality: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
)

@Serializable
data class Channel(
    val id: Int = 0,
    /** "UC..." or "@handle" — this, never the numeric id, is the path segment. */
    @SerialName("channel_id") val channelId: String,
    val url: String? = null,
    val title: String? = null,
    val enabled: Boolean = true,
    @SerialName("quality_override") val qualityOverride: QualityOverride? = null,
    @SerialName("last_checked_at") val lastCheckedAt: String? = null,
    @SerialName("last_uploaded_at") val lastUploadedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: channelId
}

@Serializable
data class QualityOverride(
    val quality: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
)

@Serializable
data class ChannelCreate(
    val url: String,
    @SerialName("channel_id") val channelId: String? = null,
    val title: String? = null,
    val enabled: Boolean? = null,
)

@Serializable
data class ChannelPatch(
    val title: String? = null,
    val enabled: Boolean? = null,
    @SerialName("quality_override") val qualityOverride: QualityOverride? = null,
    val url: String? = null,
)

@Serializable
data class Source(
    val id: Int = 0,
    /** "PL..." or "WL" — this is the segment used in /sources/{source_id}. */
    @SerialName("source_id") val sourceId: String,
    val kind: String = "playlist",
    val url: String? = null,
    val title: String? = null,
    val enabled: Boolean = true,
    @SerialName("quality_override") val qualityOverride: QualityOverride? = null,
    @SerialName("last_checked_at") val lastCheckedAt: String? = null,
    @SerialName("last_added_at") val lastAddedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val counts: SourceCounts = SourceCounts(),
) {
    val isWatchLater: Boolean get() = kind == "watch_later" || sourceId == "WL"
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: sourceId
}

@Serializable
data class SourceCounts(val total: Int = 0, val downloaded: Int = 0, val failed: Int = 0)

@Serializable
data class SourceCreate(
    val url: String,
    val title: String? = null,
    val enabled: Boolean? = null,
    @SerialName("quality_override") val qualityOverride: QualityOverride? = null,
)

@Serializable
data class SourcePatch(
    val title: String? = null,
    val enabled: Boolean? = null,
    @SerialName("quality_override") val qualityOverride: QualityOverride? = null,
    val url: String? = null,
)

@Serializable
data class SourceStatus(
    val sources: Int = 0,
    val enabled: Int = 0,
    val running: Boolean = false,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("cookies_configured") val cookiesConfigured: Boolean = false,
    @SerialName("default_quality") val defaultQuality: String? = null,
)

@Serializable
data class SourcePreviewRequest(val url: String)

@Serializable
data class SourcePreview(
    @SerialName("source_id") val sourceId: String? = null,
    val title: String? = null,
    val count: Int = 0,
    val kind: String? = null,
)

@Serializable
data class MonitorSettings(
    val enabled: Boolean = true,
    @SerialName("interval_minutes") val intervalMinutes: Int = 15,
    @SerialName("window_hours") val windowHours: Int = 24,
    @SerialName("future_only") val futureOnly: Boolean = false,
    @SerialName("content_filter") val contentFilter: String = "all",
    @SerialName("youtube_api_key_set") val youtubeApiKeySet: Boolean? = null,
)

@Serializable
data class LibrarySettings(
    @SerialName("media_layout") val mediaLayout: String = "channel_id",
    @SerialName("tree_order") val treeOrder: String = "newest",
    @SerialName("retention_days") val retentionDays: Int? = null,
    @SerialName("max_library_items") val maxLibraryItems: Int? = null,
    @SerialName("delete_file_when_watched") val deleteFileWhenWatched: Boolean = false,
)

@Serializable
data class LibrarySettingsPatch(
    @SerialName("media_layout") val mediaLayout: String? = null,
    @SerialName("tree_order") val treeOrder: String? = null,
    @SerialName("retention_days") val retentionDays: Int? = null,
    @SerialName("max_library_items") val maxLibraryItems: Int? = null,
    @SerialName("delete_file_when_watched") val deleteFileWhenWatched: Boolean? = null,
)

@Serializable
data class LibraryLayouts(
    val options: List<String> = emptyList(),
    val descriptions: Map<String, String> = emptyMap(),
    val orders: List<String> = emptyList(),
)

@Serializable
data class LibraryTree(
    val folders: List<TreeFolder> = emptyList(),
    val order: String? = null,
    val total: Int = 0,
)

@Serializable
data class TreeFolder(
    val name: String,
    @SerialName("channel_id") val channelId: String? = null,
    @SerialName("channel_title") val channelTitle: String? = null,
    @SerialName("item_count") val itemCount: Int = 0,
    @SerialName("total_bytes") val totalBytes: Long = 0,
    val subfolders: List<TreeSubfolder> = emptyList(),
) {
    val displayName: String get() = channelTitle?.takeIf { it.isNotBlank() } ?: name
}

@Serializable
data class TreeSubfolder(
    val name: String,
    @SerialName("source_id") val sourceId: Int? = null,
    val items: List<TreeItem> = emptyList(),
    @SerialName("item_count") val itemCount: Int = 0,
)

@Serializable
data class TreeItem(
    @SerialName("video_id") val videoId: String,
    val title: String? = null,
    val duration: Int? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    val quality: String? = null,
    @SerialName("media_type") val mediaType: String = "video",
    val status: String = "discovered",
    val watched: Boolean = false,
    @SerialName("is_short") val isShort: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: videoId
    val isAudio: Boolean get() = mediaType == "audio"
    val hasFile: Boolean get() = status == "completed"
}

@Serializable
data class QueueStats(
    val queued: Int = 0,
    val downloading: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val unavailable: Int = 0,
)

@Serializable
data class WatchedPatch(val watched: Boolean)
@Serializable
data class PinnedPatch(val pinned: Boolean)
@Serializable
data class PinRequest(
    @SerialName("video_ids") val videoIds: List<String>,
    val pinned: Boolean = true,
)

@Serializable
data class BulkDeleteRequest(
    @SerialName("video_ids") val videoIds: List<String>,
    @SerialName("delete_files") val deleteFiles: Boolean = true,
    @SerialName("dry_run") val dryRun: Boolean = true,
)

@Serializable
data class BulkDeleteResult(
    val deleted: Int = 0,
    @SerialName("files_deleted") val filesDeleted: Int = 0,
)

@Serializable
data class DeleteResult(
    val deleted: Boolean? = null,
    @SerialName("file_deleted") val fileDeleted: Boolean? = null,
)

@Serializable
data class StorageInfo(
    @SerialName("total_files") val totalFiles: Int = 0,
    @SerialName("total_bytes") val totalBytes: Long = 0,
    @SerialName("watched_bytes") val watchedBytes: Long = 0,
    @SerialName("unwatched_bytes") val unwatchedBytes: Long = 0,
    @SerialName("orphaned_bytes") val orphanedBytes: Long = 0,
    @SerialName("media_dir") val mediaDir: String? = null,
    @SerialName("free_bytes") val freeBytes: Long = 0,
)

@Serializable
data class RetentionSettings(
    @SerialName("retention_days") val retentionDays: Int? = null,
    @SerialName("max_library_items") val maxLibraryItems: Int? = null,
    @SerialName("delete_file_when_watched") val deleteFileWhenWatched: Boolean = false,
)

@Serializable
data class RetentionPreview(
    @SerialName("retention_days") val retentionDays: Int? = null,
    @SerialName("max_library_items") val maxLibraryItems: Int? = null,
    @SerialName("completed_total") val completedTotal: Int = 0,
    @SerialName("would_delete") val wouldDelete: Int = 0,
    val bytes: Long = 0,
    @SerialName("video_ids") val videoIds: List<String> = emptyList(),
)

@Serializable
data class QualityOptions(
    val options: List<String> = emptyList(),
    val default: String = "best",
    @SerialName("per_channel_overrides") val perChannelOverrides: Map<String, QualityOverride> = emptyMap(),
)

@Serializable
data class QualitySettings(
    val quality: String = "best",
    @SerialName("media_type") val mediaType: String = "video",
    val default: String? = null,
    val options: List<String> = emptyList(),
)

@Serializable
data class EnqueueRequest(
    @SerialName("video_ids") val videoIds: List<String>,
    val priority: Int = 0,
)

@Serializable
data class LogEntry(
    val id: Int = 0,
    val ts: String? = null,
    val level: String = "INFO",
    val logger: String? = null,
    val message: String? = null,
)

@Serializable
data class LogPost(val level: String = "ERROR", val logger: String = "android", val message: String)

@Serializable
data class MonitorCheckRequest(val url: String)