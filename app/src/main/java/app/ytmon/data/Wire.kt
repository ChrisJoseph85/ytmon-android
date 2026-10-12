package app.ytmon.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decoders for the few endpoints whose exact payload is not pinned down by the
 * client spec. The rules they follow are the ones the rest of the app already
 * relies on: a surprising body must never turn a working call into a failure, so
 * every one of these returns a usable default instead of throwing.
 */
object Wire {

    /**
     * A channel list. `/channels` is a bare JSON array; `/subscriptions` is
     * documented the same way, but a paged or wrapped envelope is accepted too
     * (`items` / `channels` / `subscriptions`) rather than failing the screen.
     */
    fun channels(raw: String): List<Channel> {
        val root = Network.json.parseToJsonElement(raw)
        val array: JsonArray = when {
            root is JsonArray -> root
            root is JsonObject -> (root.firstArray("items", "channels", "subscriptions", "data")
                ?: JsonArray(emptyList()))
            else -> JsonArray(emptyList())
        }
        return channelArray(array)
    }

    /** Per-element: one unreadable row costs that row, not the whole list. */
    private fun channelArray(array: JsonArray): List<Channel> = array.mapNotNull { element ->
        if (element is JsonObject) runCatching {
            Network.json.decodeFromJsonElement(Channel.serializer(), element)
        }.getOrNull() else null
    }

    /**
     * GET /subscriptions/status. The spec only names `cookies_configured` and the
     * "last imported <time> from <source>" line, so the remaining keys are read by
     * name with a couple of spellings each rather than hard-failing on a surprise.
     */
    fun subscriptionsStatus(raw: String): SubscriptionsStatus {
        val obj = Network.json.parseToJsonElement(raw).jsonObject
        return SubscriptionsStatus(
            cookiesConfigured = obj.first("cookies_configured", "cookies")?.booleanOrNull ?: false,
            total = obj.first("total", "channels", "count", "subscriptions")?.intOrNull ?: 0,
            lastImportSource = obj.first("last_import_source", "last_source", "source")?.contentOrNull,
            lastImportAt = obj.first("last_import_at", "last_imported_at", "last_imported", "last_at")?.contentOrNull,
        )
    }

    /** Only the counts the app actually renders; unknown keys are ignored. */
    fun scanOutcome(raw: String): ScanOutcome = runCatching {
        Network.json.decodeFromJsonElement(ScanOutcome.serializer(), Network.json.parseToJsonElement(raw).jsonObject)
    }.getOrElse { ScanOutcome() }

    fun snapshot(raw: String): SubscriptionSnapshot = runCatching {
        val obj = Network.json.parseToJsonElement(raw).jsonObject
        SubscriptionSnapshot(
            source = obj.first("source")?.contentOrNull,
            imported = obj.first("imported")?.intOrNull ?: 0,
            updated = obj.first("updated")?.intOrNull ?: 0,
            skipped = obj.first("skipped")?.intOrNull ?: 0,
            total = obj.first("total")?.intOrNull ?: 0,
            createdAt = obj.first("created_at", "createdAt")?.contentOrNull,
            // Per-element, like channels(): one bad stored row must not empty the snapshot.
            channels = obj.firstArray("channels", "items", "subscriptions")?.let(::channelArray).orEmpty(),
        )
    }.getOrElse { SubscriptionSnapshot() }

    /**
     * Cheap client-side sanity check on pasted Takeout JSON, so an obviously wrong
     * paste is reported as such instead of coming back as a raw 400 invalid_takeout.
     * Returns null when the payload looks like a subscriptions export.
     */
    fun takeoutProblem(text: String): String? {
        // Editors happily leave a BOM on a saved file, and it breaks JSON parsing.
        val trimmed = text.trim().removePrefix("﻿")
        if (trimmed.isEmpty()) return "Paste the contents of subscriptions.json first."
        val root = runCatching { Network.json.parseToJsonElement(trimmed) }.getOrNull()
            ?: return "That is not valid JSON."
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root["subscriptions"] as? JsonArray
            else -> null
        } ?: return "Expected a subscriptions array — either [ … ] or { \"subscriptions\": [ … ] }."
        if (array.isEmpty()) return "That subscriptions array is empty."
        return null
    }

    private fun JsonObject.first(vararg keys: String): JsonPrimitive? =
        keys.firstNotNullOfOrNull { this[it] as? JsonPrimitive }

    private fun JsonObject.firstArray(vararg keys: String): JsonArray? =
        keys.firstNotNullOfOrNull { this[it] }?.let { runCatching { it.jsonArray }.getOrNull() }
}