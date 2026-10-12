package app.ytmon.data

import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * The complete server surface.
 *
 * NOTE: /queue, /sources, /channels, /logs and /retention/preview return BARE
 * JSON arrays/objects, while /videos and /library/tree return paged objects.
 * That asymmetry is real (verified against the live server) and is encoded here.
 *
 * The base URL is 127.0.0.1 — loopback is trusted, so no bearer token is sent.
 */
interface YtApi {

    @GET("health")
    suspend fun health(): Response<HealthResponse>

    @GET("server/info")
    suspend fun serverInfo(): Response<ServerInfo>

    // ---------- LIBRARY ----------

    @GET("videos")
    suspend fun videos(
        @Query("search") search: String? = null,
        @Query("channel_id") channelId: String? = null,
        @Query("status") status: String? = null,
        @Query("kind") kind: String? = null,
        @Query("watched") watched: Boolean? = null,
        @Query("pinned") pinned: Boolean? = null,
        @Query("media_type") mediaType: String? = null,
        @Query("sort") sort: String? = null,
        @Query("order") order: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 50,
    ): Response<VideoPage>

    @GET("videos/by-id/{videoId}")
    suspend fun videoById(
        @Path("videoId") videoId: String,
        @Query("include_formats") includeFormats: Boolean = false,
    ): Response<Video>

    @PATCH("videos/{videoId}/watched")
    suspend fun setWatched(@Path("videoId") videoId: String, @Body body: WatchedPatch): Response<Unit>

    @PATCH("videos/{videoId}/pinned")
    suspend fun setPinned(@Path("videoId") videoId: String, @Body body: PinnedPatch): Response<Unit>

    @DELETE("videos/{videoId}")
    suspend fun deleteVideo(
        @Path("videoId") videoId: String,
        @Query("delete_file") deleteFile: Boolean = true,
        @Query("dry_run") dryRun: Boolean = false,
    ): Response<DeleteResult>

    @POST("videos/bulk-delete")
    suspend fun bulkDelete(@Body body: BulkDeleteRequest): Response<BulkDeleteResult>

    @POST("videos/pin")
    suspend fun pinVideos(@Body body: PinRequest): Response<Unit>

    @GET("videos/pinned")
    suspend fun pinnedVideos(): Response<List<Video>>

    @POST("videos/fetch")
    suspend fun fetch(@Body body: FetchRequest): Response<FetchResult>

    // ---------- CHANNELS ----------

    @GET("channels")
    suspend fun channels(): Response<List<Channel>>

    @POST("channels")
    suspend fun createChannel(@Body body: ChannelCreate): Response<Channel>

    @GET("channels/{channelId}")
    suspend fun channel(@Path("channelId") channelId: String): Response<Channel>

    @PATCH("channels/{channelId}")
    suspend fun patchChannel(
        @Path("channelId") channelId: String,
        @Body body: ChannelPatch,
    ): Response<Channel>

    @DELETE("channels/{channelId}")
    suspend fun deleteChannel(@Path("channelId") channelId: String): Response<Unit>

    @POST("channels/{channelId}/scan")
    suspend fun scanChannel(@Path("channelId") channelId: String): Response<Unit>

    @GET("channels/export/{fmt}")
    suspend fun exportChannels(@Path("fmt") fmt: String): Response<ResponseBody>

    @POST("channels/import/{fmt}")
    suspend fun importChannels(@Path("fmt") fmt: String, @Body body: RequestBody): Response<Unit>

    @POST("channels/import")
    suspend fun importChannelsAuto(@Body body: RequestBody): Response<Unit>

    @POST("monitor/check")
    suspend fun monitorCheck(@Body body: MonitorCheckRequest): Response<Map<String, String?>>

    // ---------- MONITORING ----------

    @GET("monitor")
    suspend fun monitor(): Response<MonitorSettings>

    @PUT("monitor")
    suspend fun putMonitor(@Body body: MonitorSettings): Response<MonitorSettings>

    @POST("monitor/scan")
    suspend fun monitorScan(): Response<Unit>

    // ---------- SOURCES ----------

    @GET("sources")
    suspend fun sources(): Response<List<Source>>

    @POST("sources")
    suspend fun createSource(@Body body: SourceCreate): Response<Source>

    @GET("sources/{sourceId}")
    suspend fun source(@Path("sourceId") sourceId: String): Response<Source>

    @PATCH("sources/{sourceId}")
    suspend fun patchSource(
        @Path("sourceId") sourceId: String,
        @Body body: SourcePatch,
    ): Response<Source>

    @DELETE("sources/{sourceId}")
    suspend fun deleteSource(@Path("sourceId") sourceId: String): Response<Unit>

    @POST("sources/{sourceId}/scan")
    suspend fun scanSource(@Path("sourceId") sourceId: String): Response<Unit>

    @POST("sources/scan")
    suspend fun scanAllSources(): Response<Unit>

    /** POST, not GET. */
    @POST("sources/preview")
    suspend fun previewSource(@Body body: SourcePreviewRequest): Response<SourcePreview>

    @GET("sources/status")
    suspend fun sourcesStatus(): Response<SourceStatus>

    // ---------- LIBRARY ORGANISATION ----------

    @GET("library/layouts")
    suspend fun libraryLayouts(): Response<LibraryLayouts>

    @GET("library/settings")
    suspend fun librarySettings(): Response<LibrarySettings>

    /** MERGE — only the fields you send change. */
    @PUT("library/settings")
    suspend fun putLibrarySettings(@Body body: LibrarySettingsPatch): Response<LibrarySettings>

    @GET("library/tree")
    suspend fun libraryTree(
        @Query("order") order: String? = null,
        @Query("limit_per_folder") limitPerFolder: Int? = null,
    ): Response<LibraryTree>

    // ---------- QUEUE ----------

    @GET("queue")
    suspend fun queue(
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
    ): Response<List<Job>>

    @GET("queue/stats")
    suspend fun queueStats(): Response<QueueStats>

    @POST("queue/enqueue")
    suspend fun enqueue(@Body body: EnqueueRequest): Response<Unit>

    @GET("queue/{videoId}")
    suspend fun job(@Path("videoId") videoId: String): Response<Job>

    @POST("queue/{videoId}/retry")
    suspend fun retry(@Path("videoId") videoId: String): Response<Unit>

    @POST("queue/{videoId}/cancel")
    suspend fun cancel(@Path("videoId") videoId: String): Response<Unit>

    // ---------- FILES (raw, no envelope) ----------

    @GET("files/{videoId}/metadata")
    suspend fun fileMetadata(@Path("videoId") videoId: String): Response<FileMetadata>

    @GET("files/{videoId}/stream")
    suspend fun stream(@Path("videoId") videoId: String, @Header("Range") range: String? = null): Response<ResponseBody>

    @GET("files/{videoId}/download")
    suspend fun download(@Path("videoId") videoId: String, @Header("Range") range: String? = null): Response<ResponseBody>

    // ---------- STORAGE / RETENTION ----------

    @GET("storage")
    suspend fun storage(): Response<StorageInfo>

    @GET("retention")
    suspend fun retention(): Response<RetentionSettings>

    @PUT("retention")
    suspend fun putRetention(@Body body: RetentionSettings): Response<RetentionSettings>

    @GET("retention/preview")
    suspend fun retentionPreview(): Response<RetentionPreview>

    @POST("retention/apply")
    suspend fun applyRetention(@Query("dry_run") dryRun: Boolean = true): Response<RetentionPreview>

    // ---------- QUALITY ----------

    @GET("quality")
    suspend fun quality(): Response<QualitySettings>

    @PUT("quality")
    suspend fun putQuality(@Body body: QualitySettings): Response<QualitySettings>

    @GET("quality/options")
    suspend fun qualityOptions(): Response<QualityOptions>

    // ---------- LOGS ----------

    @GET("logs")
    suspend fun logs(
        @Query("level") level: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
    ): Response<List<LogEntry>>

    @POST("logs")
    suspend fun postLog(@Body body: LogPost): Response<Unit>
}