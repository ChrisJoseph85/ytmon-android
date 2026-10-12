package app.ytmon.data

import android.content.Context
import android.content.SharedPreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The server address is a runtime setting, not a compile-time constant.
 *
 * Defaults to loopback, which is the supported configuration: the server trusts
 * 127.0.0.1 and needs no bearer token. It can be changed from the Settings
 * screen without rebuilding the app.
 *
 * The value is cached in memory so request building stays cheap; every access
 * goes through [baseUrl] so a change takes effect immediately.
 */
object ServerConfig : SharedPreferences.OnSharedPreferenceChangeListener {

    private const val PREFS = "ytmon_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_TOKEN = "token"

    const val DEFAULT_BASE_URL = "http://127.0.0.1:8765/api/v1/"

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        private set

    /**
     * Optional bearer token. Null/blank means "send no Authorization header",
     * which is the correct behaviour for loopback — the server trusts 127.0.0.1.
     * Only a non-loopback address needs one.
     */
    @Volatile
    var token: String? = null
        private set

    /** True once the user has saved a URL of their own. */
    @Volatile
    var isCustom: Boolean = false
        private set

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            p.registerOnSharedPreferenceChangeListener(this)
            val stored = p.getString(KEY_BASE_URL, null)
            baseUrl = normalize(stored ?: DEFAULT_BASE_URL)
            isCustom = stored != null
            token = p.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
            prefs = p
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == KEY_BASE_URL) {
            baseUrl = normalize(sharedPreferences?.getString(KEY_BASE_URL, null) ?: DEFAULT_BASE_URL)
            isCustom = sharedPreferences?.contains(KEY_BASE_URL) == true
            Network.invalidate()
        }
    }

    fun save(context: Context, url: String) {
        init(context)
        val normalised = normalize(url)
        baseUrl = normalised
        isCustom = url.isNotBlank()
        prefs?.edit()?.putString(KEY_BASE_URL, normalised)?.apply()
        // Apply() writes asynchronously; make sure this process sees it now.
        Network.invalidate()
    }

    fun resetToDefault(context: Context) {
        init(context)
        baseUrl = DEFAULT_BASE_URL
        isCustom = false
        prefs?.edit()?.remove(KEY_BASE_URL)?.apply()
        Network.invalidate()
    }

    /** Host:port for the user-facing banner, without the scheme or API path. */
    val displayAddress: String
        get() = baseUrl.toHttpUrlOrNull()?.let { "${it.host}:${it.port}" } ?: baseUrl

    /** The address exactly as the user typed it, for the settings field. */
    val editableValue: String get() = baseUrl

    fun saveToken(context: Context, value: String) {
        init(context)
        val trimmed = value.trim().takeIf { it.isNotBlank() }
        token = trimmed
        prefs?.edit()?.putString(KEY_TOKEN, trimmed)?.apply()
    }

    fun clearToken(context: Context) = saveToken(context, "")

    /**
     * True when the given address points at THIS phone's own loopback interface.
     */
    fun isLoopback(url: String = baseUrl): Boolean {
        val host = normalize(url).toHttpUrlOrNull()?.host ?: return false
        return host == "127.0.0.1" || host == "localhost" || host == "::1"
    }

    /**
     * Accepts what people actually type — "192.168.1.5:8765", a trailing slash,
     * or a full URL — and returns a Retrofit-safe base ending in "/api/v1/".
     */
    fun normalize(input: String): String {
        var u = input.trim()
        if (u.isEmpty()) return DEFAULT_BASE_URL
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
        while (u.endsWith("/")) u = u.dropLast(1)
        if (!u.endsWith("/api/v1")) u = "$u/api/v1"
        return "$u/"
    }
}