package app.ytmon.data

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The single HTTP client for the whole app.
 *
 * Hard rules encoded here:
 *  - Base URL is LOOPBACK (127.0.0.1). The LAN IP would be treated as a remote
 *    client and 401.
 *  - NO Origin header is ever added. OkHttp does not send one by default and we
 *    must not introduce one — the server returns 403 origin_not_allowed.
 *  - No bearer token: loopback is trusted by default.
 */
object Network {

    // No compile-time base URL: it comes from ServerConfig and can change at runtime.
    // Retrofit binds a base URL at construction, so the instances are rebuilt
    // whenever the configured address changes.
    @Volatile private var builtFor: String? = null
    @Volatile private var apiInstance: YtApi? = null
    @Volatile private var slowApiInstance: YtApi? = null

    @Synchronized
    fun invalidate() {
        builtFor = null
        apiInstance = null
        slowApiInstance = null
    }

    @Synchronized
    private fun ensureBuilt(): String {
        val target = ServerConfig.baseUrl
        if (builtFor != target || apiInstance == null || slowApiInstance == null) {
            apiInstance = build(target, client())
            slowApiInstance = build(target, slowClient())
            builtFor = target
        }
        return target
    }

    private fun build(baseUrl: String, client: OkHttpClient): YtApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(YtApi::class.java)

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun client(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            // Media streaming + thumbnails can be slow/large; do not cut streams short.
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    // Explicitly strip Origin if anything upstream ever added one.
                    // The server answers 403 origin_not_allowed to ANY request with one.
                    .removeHeader("Origin")
                    .removeHeader("Referer")
                    .header("Accept", "application/json, */*")
                    .apply {
                        // Read per-request so changing the token needs no client rebuild.
                        // Omitted entirely on loopback, where the server trusts us.
                        ServerConfig.token?.let { header("Authorization", "Bearer $it") }
                    }
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()
    }

    fun api(): YtApi {
        ensureBuilt()
        return apiInstance!!
    }

    /**
     * A client with long timeouts for the genuinely slow calls that proxy YouTube:
     * POST /videos/fetch, /sources/preview and any scan.
     */
    fun slowClient(): OkHttpClient = client().newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    fun slowApi(): YtApi {
        ensureBuilt()
        return slowApiInstance!!
    }

    fun textBody(text: String) = text.toRequestBody("text/plain".toMediaType())

    fun jsonBody(text: String) = text.toRequestBody("application/json".toMediaType())
}

/** Formats a byte count for display. */
fun Long.asSize(): String {
    if (this <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var i = 0
    var v = toDouble()
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (v >= 100 || i == 0) "${v.toInt()} ${units[i]}" else "%.1f %s".format(v, units[i])
}

/** Formats seconds as m:ss / h:mm:ss. */
fun Int?.asDuration(): String {
    val s = this ?: return "—"
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun Double?.asSpeed(): String {
    val b = this ?: return "—"
    if (b <= 0) return "—"
    return "${(b / 1024 / 1024).format1()} MB/s"
}

fun Double?.asEta(): String {
    val s = this ?: return "—"
    if (s <= 0 || s.isNaN()) return "—"
    val i = s.toInt()
    return if (i < 60) "${i}s" else if (i < 3600) "${i / 60}m ${i % 60}s" else "${i / 3600}h ${(i % 3600) / 60}m"
}

private fun Double.format1(): String = "%.1f".format(this)