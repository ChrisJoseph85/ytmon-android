package app.ytmon.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody

/**
 * Single entry point to the server. Every method returns a [Result] and maps
 * non-2xx into the server's typed error envelope.
 *
 * Slow calls (fetch / preview / scan) use [apiSlow] and are NEVER auto-retried,
 * because a retry would re-resolve the link and re-queue duplicates.
 *
 * [api] and [apiSlow] are getters, not constructor vals, so that changing the
 * server address in Settings takes effect immediately.
 */
class YtRepository {
    val api: YtApi get() = Network.api()
    private val apiSlow: YtApi get() = Network.slowApi()

    // ---------- HEALTH ----------

    suspend fun health(): Result<HealthResponse> = io { ApiResult.get(api.health()) }
    suspend fun serverInfo(): Result<ServerInfo> = io { ApiResult.get(api.serverInfo()) }

    // ---------- LIBRARY ----------

    suspend fun videos(
        search: String? = null,
        channelId: String? = null,
        status: String? = null,
        kind: String? = null,
        watched: Boolean? = null,
        pinned: Boolean? = null,
        mediaType: String? = null,
        sort: String? = null,
        order: String? = null,
        page: Int = 1,
        pageSize: Int = 50,
    ): Result<VideoPage> = io {
        ApiResult.get(api.videos(search, channelId, status, kind, watched, pinned, mediaType, sort, order, page, pageSize))
    }

    /** include_formats=true is slow (hits YouTube) — left off by default. */
    suspend fun videoById(videoId: String, includeFormats: Boolean = false): Result<Video> =
        io { ApiResult.get(api.videoById(videoId, includeFormats)) }

    suspend fun setWatched(videoId: String, watched: Boolean): Result<Unit> =
        io { ApiResult.unit(api.setWatched(videoId, WatchedPatch(watched))) }

    suspend fun setPinned(videoId: String, pinned: Boolean): Result<Unit> =
        io { ApiResult.unit(api.setPinned(videoId, PinnedPatch(pinned))) }

    suspend fun pinMany(videoIds: List<String>, pinned: Boolean): Result<Unit> =
        io { ApiResult.unit(api.pinVideos(PinRequest(videoIds, pinned))) }

    suspend fun pinnedVideos(): Result<List<Video>> = io { ApiResult.get(api.pinnedVideos()) }

    suspend fun deleteVideo(videoId: String, deleteFile: Boolean, dryRun: Boolean): Result<DeleteResult> =
        io { ApiResult.get(api.deleteVideo(videoId, deleteFile, dryRun)) }

    /** Preview mode: dryRun tells the server how many FILES would be erased. */
    suspend fun bulkDeletePreview(ids: List<String>, deleteFiles: Boolean): Result<BulkDeleteResult> =
        io { ApiResult.get(api.bulkDelete(BulkDeleteRequest(ids, deleteFiles, true))) }

    suspend fun bulkDelete(ids: List<String>, deleteFiles: Boolean): Result<BulkDeleteResult> =
        io { ApiResult.get(api.bulkDelete(BulkDeleteRequest(ids, deleteFiles, false))) }

    /** download=false is a PREVIEW — queues nothing and returns quality=null. */
    suspend fun fetchPreview(req: FetchRequest): Result<FetchResult> =
        io { ApiResult.get(apiSlow.fetch(req.copy(download = false))) }

    suspend fun fetch(req: FetchRequest): Result<FetchResult> = io { ApiResult.get(apiSlow.fetch(req)) }

    // ---------- CHANNELS ----------

    suspend fun channels(): Result<List<Channel>> = io { ApiResult.get(api.channels()) }
    suspend fun createChannel(body: ChannelCreate): Result<Channel> = io { ApiResult.get(api.createChannel(body)) }

    /** channelId must be "UC..." or "@handle" — never the numeric id. */
    suspend fun channel(channelId: String): Result<Channel> = io { ApiResult.get(api.channel(channelId)) }

    suspend fun patchChannel(channelId: String, body: ChannelPatch): Result<Channel> =
        io { ApiResult.get(api.patchChannel(channelId, body)) }

    suspend fun deleteChannel(channelId: String): Result<Unit> =
        io { ApiResult.unit(api.deleteChannel(channelId)) }

    suspend fun scanChannel(channelId: String): Result<ScanOutcome> = io {
        ApiResult.lenient(apiSlow.scanChannel(channelId), ScanOutcome()) { Wire.scanOutcome(it) }
    }

    suspend fun exportChannels(fmt: String): Result<ResponseBody> =
        io { ApiResult.get(api.exportChannels(if (fmt == "csv") "csv" else "json")) }

    suspend fun importChannels(fmt: String, body: String): Result<Unit> =
        io { ApiResult.unit(api.importChannels(if (fmt == "csv") "csv" else "json", Network.textBody(body))) }

    // ---------- CHANNEL POLICY (auto/manual + schedule) ----------

    /** Body is built by [PolicyBody.one]; the server merges, so unset keys are omitted. */
    suspend fun setChannelPolicy(channelId: String, body: JsonObject): Result<Channel> =
        io { ApiResult.get(api.patchChannelPolicy(channelId, body)) }

    suspend fun setChannelPolicyBulk(body: JsonObject): Result<BulkPolicyResult> =
        io { ApiResult.get(api.bulkChannelPolicy(body)) }

    // ---------- SUBSCRIPTIONS ----------

    /** mode = "auto" | "manual" filters server-side; null returns everything. */
    suspend fun subscriptions(mode: String? = null): Result<List<Channel>> = io {
        ApiResult.lenient(api.subscriptions(mode), emptyList()) { Wire.channels(it) }
    }

    suspend fun subscriptionsStatus(): Result<SubscriptionsStatus> = io {
        ApiResult.lenient(api.subscriptionsStatus(), SubscriptionsStatus()) { Wire.subscriptionsStatus(it) }
    }

    /** Re-reads a stored import, which is why the list survives cookies expiring. */
    suspend fun subscriptionSnapshot(id: String): Result<SubscriptionSnapshot> = io {
        ApiResult.lenient(api.subscriptionSnapshot(id), SubscriptionSnapshot()) { Wire.snapshot(it) }
    }

    /** data is the pasted subscriptions.json; it is null (omitted) for source="feed". */
    suspend fun importSubscriptions(source: String, data: String?): Result<SubscriptionImportResult> =
        io { ApiResult.get(api.importSubscriptions(SubscriptionImportRequest(source, data))) }

    suspend fun monitorCheck(url: String): Result<Map<String, String?>> =
        io { ApiResult.get(apiSlow.monitorCheck(MonitorCheckRequest(url))) }

    // ---------- MONITORING ----------

    suspend fun monitor(): Result<MonitorSettings> = io { ApiResult.get(api.monitor()) }
    suspend fun putMonitor(body: MonitorSettings): Result<MonitorSettings> =
        io { ApiResult.get(api.putMonitor(body)) }

    suspend fun monitorScan(): Result<ScanOutcome> = io {
        ApiResult.lenient(apiSlow.monitorScan(), ScanOutcome()) { Wire.scanOutcome(it) }
    }

    // ---------- SOURCES ----------

    suspend fun sources(): Result<List<Source>> = io { ApiResult.get(api.sources()) }
    suspend fun sourcesStatus(): Result<SourceStatus> = io { ApiResult.get(api.sourcesStatus()) }
    suspend fun createSource(body: SourceCreate): Result<Source> = io { ApiResult.get(api.createSource(body)) }
    suspend fun patchSource(id: String, body: SourcePatch): Result<Source> =
        io { ApiResult.get(api.patchSource(id, body)) }

    suspend fun deleteSource(id: String): Result<Unit> = io { ApiResult.unit(api.deleteSource(id)) }
    suspend fun scanSource(id: String): Result<Unit> = io { ApiResult.unit(apiSlow.scanSource(id)) }
    suspend fun scanAllSources(): Result<Unit> = io { ApiResult.unit(apiSlow.scanAllSources()) }
    suspend fun previewSource(url: String): Result<SourcePreview> =
        io { ApiResult.get(apiSlow.previewSource(SourcePreviewRequest(url))) }

    // ---------- LIBRARY ORGANISATION ----------

    suspend fun libraryLayouts(): Result<LibraryLayouts> = io { ApiResult.get(api.libraryLayouts()) }
    suspend fun librarySettings(): Result<LibrarySettings> = io { ApiResult.get(api.librarySettings()) }
    suspend fun putLibrarySettings(body: LibrarySettingsPatch): Result<LibrarySettings> =
        io { ApiResult.get(api.putLibrarySettings(body)) }

    suspend fun libraryTree(order: String?, limitPerFolder: Int?): Result<LibraryTree> =
        io { ApiResult.get(api.libraryTree(order, limitPerFolder)) }

    // ---------- QUEUE ----------

    suspend fun queue(status: String?, page: Int?, pageSize: Int?): Result<List<Job>> =
        io { ApiResult.get(api.queue(status, page, pageSize)) }

    suspend fun queueStats(): Result<QueueStats> = io { ApiResult.get(api.queueStats()) }
    suspend fun job(videoId: String): Result<Job> = io { ApiResult.get(api.job(videoId)) }
    suspend fun retry(videoId: String): Result<Unit> = io { ApiResult.unit(api.retry(videoId)) }
    suspend fun cancel(videoId: String): Result<Unit> = io { ApiResult.unit(api.cancel(videoId)) }
    suspend fun enqueue(ids: List<String>, priority: Int = 0): Result<Unit> =
        io { ApiResult.unit(api.enqueue(EnqueueRequest(ids, priority))) }

    // ---------- FILES ----------

    suspend fun fileMetadata(videoId: String): Result<FileMetadata> =
        io { ApiResult.get(api.fileMetadata(videoId)) }

    /** Media URLs are built from the CONFIGURED address, not a compile-time constant. */
    fun streamUrl(videoId: String) = "${ServerConfig.baseUrl}files/$videoId/stream"
    fun downloadUrl(videoId: String) = "${ServerConfig.baseUrl}files/$videoId/download"
    fun thumbnailUrl(videoId: String) = "${ServerConfig.baseUrl}videos/$videoId/thumbnail"

    /** Copies /files/{id}/download into [out] with progress. Used for gallery export and Share FILE. */
    suspend fun downloadTo(
        videoId: String,
        out: java.io.OutputStream,
        onProgress: suspend (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.download(videoId, null)
            if (!response.isSuccessful) {
                throw ErrorParser.parse(response.code(), response.errorBody()?.string())
            }
            val body = response.body() ?: throw ApiException(response.code(), "empty_body", "No file returned")
            val total = body.contentLength().takeIf { it > 0 }
            var copied = 0L
            body.byteStream().use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    copied += read
                    onProgress(copied, total)
                }
            }
            onProgress(copied, total)
            copied
        }
    }

    // ---------- STORAGE / RETENTION ----------

    suspend fun storage(): Result<StorageInfo> = io { ApiResult.get(api.storage()) }
    suspend fun retention(): Result<RetentionSettings> = io { ApiResult.get(api.retention()) }
    suspend fun putRetention(body: RetentionSettings): Result<RetentionSettings> =
        io { ApiResult.get(api.putRetention(body)) }

    suspend fun retentionPreview(): Result<RetentionPreview> = io { ApiResult.get(api.retentionPreview()) }
    suspend fun applyRetention(dryRun: Boolean): Result<RetentionPreview> =
        io { ApiResult.get(api.applyRetention(dryRun)) }

    // ---------- QUALITY ----------

    suspend fun quality(): Result<QualitySettings> = io { ApiResult.get(api.quality()) }
    suspend fun putQuality(body: QualitySettings): Result<QualitySettings> =
        io { ApiResult.get(api.putQuality(body)) }

    suspend fun qualityOptions(): Result<QualityOptions> = io { ApiResult.get(api.qualityOptions()) }

    // ---------- LOGS ----------

    suspend fun logs(level: String?): Result<List<LogEntry>> = io { ApiResult.get(api.logs(level)) }
    suspend fun postLog(level: String, logger: String, message: String): Result<Unit> =
        io { ApiResult.unit(api.postLog(LogPost(level, logger, message))) }

    /**
 * Every call is funnelled through here so that TRANSPORT failures become
 * Result.failure instead of escaping as thrown exceptions.
 *
 * Retrofit's suspend functions throw IOException (ConnectException,
 * SocketTimeoutException, UnknownHostException) BEFORE returning a Response,
 * so a plain `try { api.x() }` inside the Result-producing helper is not
 * enough — the throw happens at the call boundary and would otherwise
 * propagate out of the repository and crash the app when the Termux server
 * is not running.
 */
private suspend fun <T> io(block: suspend () -> Result<T>): Result<T> =
    withContext(Dispatchers.IO) {
        try {
            block()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Result.failure(ApiException.fromTransport(t))
        }
    }
}