package app.ytmon.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody

/**
 * The server's ONE error shape. Always read `error.code` / `error.message`.
 * Never look for `detail`.
 *
 *   {"error": {"code": "...", "message": "...", "details": null}}
 */
class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
    val details: String? = null,
) : Exception(message) {

    /** Human-facing guidance for the failures a user can actually act on. */
    val hint: String?
        get() = when (code) {
            "resolve_failed" -> "That link could not be resolved. Check it is a real, public YouTube link."
            "not_a_playlist" -> "Use a /playlist?list=... link, the 'watch_later' alias, or add the channel instead."
            "source_disabled" -> "This source is disabled. Enable it first."
            "not_retryable" -> "That job is not in a state that can be retried."
            "not_cancellable" -> "That job has already started, so it can no longer be cancelled."
            "invalid_sub_langs" -> "The subtitle language filter is not a valid regular expression."
            "payload_too_large" -> "That import is too large (max 5 MB)."
            "data_required" -> "Nothing was pasted. Takeout needs the contents of subscriptions.json."
            "invalid_takeout" ->
                "That is not a subscriptions export. Paste the whole file — either a " +
                    "subscriptions array, or the {\"subscriptions\": …} object from Takeout."
            "import_failed" ->
                "The server could not read your YouTube subscriptions. Its cookies have " +
                    "usually expired — set YTMON_COOKIES_FILE again, or use Takeout, which " +
                    "needs nothing configured."
            "unauthorized", "invalid_token" ->
                "The server rejected the request. If the address is not this phone's own " +
                    "loopback, add a bearer token in Settings."
            "origin_not_allowed" -> "The server rejected an Origin header. The app must not send one."
            "invalid_host" -> "The server's Host protection rejected this request."
            "validation_error" -> "The server rejected one of these values."
            else -> null
        }

    /** `unavailable` is the user's content being gone — never presented as their fault. */
    val isUserFault: Boolean get() = code != "unavailable"
companion object {
        /**
         * Wraps a transport-level failure (no HTTP response at all).
         * This is the "Termux server is not running / asleep" case.
         */
        fun fromTransport(t: Throwable): ApiException {
            val msg = when (t) {
                is java.net.ConnectException ->
                    "Cannot reach the server on ${ServerConfig.displayAddress}. " +
                        "Is Termux running ytmon? The server sleeps when the phone does."
                is java.net.SocketTimeoutException ->
                    "The server at ${ServerConfig.displayAddress} did not answer in time. " +
                        "It may be asleep — try again shortly."
                is java.net.UnknownHostException ->
                    "Could not resolve the server host."
                else -> t.message ?: "Network error"
            }
            return ApiException(0, "unreachable", msg)
        }
    }
}

object ErrorParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(status: Int, raw: String?): ApiException {
        if (!raw.isNullOrBlank()) {
            runCatching {
                val root = json.parseToJsonElement(raw).jsonObject
                val err = root["error"]?.jsonObject
                if (err != null) {
                    val code = err["code"]?.jsonPrimitive?.content ?: "unknown"
                    val msg = err["message"]?.jsonPrimitive?.content ?: "Request failed ($status)"
                    val details = err["details"]?.let { d ->
                        if (d is kotlinx.serialization.json.JsonNull) null else d.toString()
                    }
                    return ApiException(status, code, msg, details)
                }
            }
        }
        return ApiException(status, "http_$status", raw?.takeIf { it.isNotBlank() } ?: "Request failed ($status)")
    }
}

object ApiResult {
    /**
     * Unwraps a Retrofit response, converting non-2xx into a typed [ApiException]
     * parsed from the server's error envelope.
     */
    fun <T> get(response: retrofit2.Response<T>, body: String? = null): Result<T> {
        if (response.isSuccessful) {
            val b = response.body()
            return if (b != null) Result.success(b) else Result.failure(ApiException(response.code(), "empty_body", "Empty response"))
        }
        return Result.failure(ErrorParser.parse(response.code(), body ?: response.errorBody()?.string()))
    }

    fun unit(response: retrofit2.Response<*>, body: String? = null): Result<Unit> {
        if (response.isSuccessful) return Result.success(Unit)
        return Result.failure(ErrorParser.parse(response.code(), body ?: response.errorBody()?.string()))
    }

    /**
     * For endpoints whose payload shape is not pinned down (scan counts,
     * subscriptions, import status).
     *
     * Errors are still surfaced verbatim, but a 2xx body we cannot make sense of
     * yields [fallback] instead of a failure — a body the app does not recognise
     * must never break a screen that worked before.
     */
    fun <T> lenient(
        response: retrofit2.Response<ResponseBody>,
        fallback: T,
        parse: (String) -> T,
    ): Result<T> {
        if (!response.isSuccessful) {
            return Result.failure(ErrorParser.parse(response.code(), response.errorBody()?.string()))
        }
        val raw = runCatching { response.body()?.string() }.getOrNull()
        if (raw.isNullOrBlank()) return Result.success(fallback)
        return Result.success(runCatching { parse(raw) }.getOrDefault(fallback))
    }
}