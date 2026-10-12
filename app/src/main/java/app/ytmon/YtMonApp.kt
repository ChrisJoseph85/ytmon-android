package app.ytmon

import android.app.Application
import app.ytmon.data.YtRepository
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class YtMonApp : Application(), ImageLoaderFactory {

    lateinit var repo: YtRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Load the saved server address before anything builds a client.
        app.ytmon.data.ServerConfig.init(this)
        repo = YtRepository()
    }

    /**
     * Thumbnails come from the local server, so cache them aggressively in RAM and
     * on disk. No auth header is needed on loopback, and no Origin is ever sent.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("thumbs"))
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .build()
}