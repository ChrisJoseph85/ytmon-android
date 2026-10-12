package app.ytmon.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Temporary harness: verifies the subscription/policy wire behaviour against the app's Json. */
class WireCheck {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun channelDefaultsWhenServerOmitsPolicyFields() {
        val c = json.decodeFromString(
            Channel.serializer(),
            """{"id":3,"channel_id":"UCabc","title":"Some channel","enabled":true}""",
        )
        assertEquals("auto", c.mode)
        assertEquals(false, c.scheduleEnabled)
        assertEquals(emptyList<String>(), c.scheduleDays)
        assertNull(c.scheduleTime)
        assertEquals(false, c.dueNow)
        assertTrue(c.isAuto)
    }

    @Test
    fun channelReadsEveryNewField() {
        val c = json.decodeFromString(
            Channel.serializer(),
            """{"channel_id":"UCabc","mode":"manual","schedule_enabled":true,
               "schedule_days":["mon","wed"],"schedule_time":"09:30",
               "due_now":true,"next_due":"2026-10-13T09:30:00+02:00"}""",
        )
        assertTrue(c.isManual)
        assertTrue(c.isScheduled)
        assertEquals(listOf("mon", "wed"), c.scheduleDays)
        assertEquals("09:30", c.scheduleTime)
        assertTrue(c.dueNow)
        assertEquals("2026-10-13T09:30:00+02:00", c.nextDue)
    }

    @Test
    fun policyBodyOmitsUnsetKeysAndSendsExplicitNullToClearTime() {
        assertEquals(
            """{"mode":"manual"}""",
            json.encodeToString(PolicyBody.one(mode = "manual")),
        )
        assertEquals(
            """{"schedule_enabled":false,"schedule_days":[],"schedule_time":null}""",
            json.encodeToString(
                PolicyBody.one(scheduleEnabled = false, scheduleDays = emptyList(), clearScheduleTime = true),
            ),
        )
        assertEquals(
            """{"channel_ids":["UCa","@bob"],"schedule_enabled":true,"schedule_days":["sat"],"schedule_time":"07:00"}""",
            json.encodeToString(
                PolicyBody.bulk(listOf("UCa", "@bob"), scheduleEnabled = true, scheduleDays = listOf("sat"), scheduleTime = "07:00"),
            ),
        )
    }

    @Test
    fun importRequestDropsDataForFeed() {
        assertEquals(
            """{"source":"feed"}""",
            json.encodeToString(SubscriptionImportRequest("feed", null)),
        )
        assertEquals(
            """{"source":"takeout","data":"[{\"subscriptions\":[]}]"}""",
            json.encodeToString(SubscriptionImportRequest("takeout", "[{\"subscriptions\":[]}]")),
        )
    }

    @Test
    fun importResultDecodesDocumentedShape() {
        val r = json.decodeFromString(
            SubscriptionImportResult.serializer(),
            """{"source":"takeout","imported":2,"updated":1,"skipped":3,"total":6,"errors":["bad row"]}""",
        )
        assertEquals(2, r.imported)
        assertEquals(1, r.updated)
        assertEquals(3, r.skipped)
        assertEquals(6, r.total)
        assertEquals(listOf("bad row"), r.errorMessages)
    }

    @Test
    fun importResultToleratesObjectErrors() {
        val r = json.decodeFromString(
            SubscriptionImportResult.serializer(),
            """{"imported":1,"errors":[{"row":4,"why":"no channel id"}]}""",
        )
        assertEquals(1, r.errorMessages.size)
        assertTrue(r.errorMessages.single().contains("no channel id"))
    }

    @Test
    fun subscriptionsStatusReadsCookiesAndLastImport() {
        val s = Wire.subscriptionsStatus(
            """{"cookies_configured":true,"total":312,"last_import_source":"takeout","last_import_at":"2026-10-11T21:02:03Z"}""",
        )
        assertTrue(s.cookiesConfigured)
        assertEquals(312, s.total)
        assertEquals("takeout", s.lastImportSource)
        assertEquals("2026-10-11T21:02:03Z", s.lastImportAt)
    }

    @Test
    fun subscriptionsStatusToleratesAliasSpellings() {
        val s = Wire.subscriptionsStatus("""{"cookies_configured":false,"channels":9,"last_imported_at":"2026-01-02T03:04:05Z","last_source":"feed"}""")
        assertEquals(9, s.total)
        assertEquals("feed", s.lastImportSource)
        assertEquals("2026-01-02T03:04:05Z", s.lastImportAt)
    }

    @Test
    fun channelListAcceptsArrayAndWrappedEnvelopes() {
        val one = """{"channel_id":"UCa","mode":"manual"}"""
        assertEquals(1, Wire.channels("[$one]").size)
        assertEquals(1, Wire.channels("""{"items":[$one],"total":1}""").size)
        assertEquals(1, Wire.channels("""{"channels":[$one]}""").size)
        assertEquals(0, Wire.channels("{}").size)
    }

    @Test
    fun channelListSkipsUnreadableRowsInsteadOfFailing() {
        val rows = Wire.channels("""[{"channel_id":"UCa"},"not an object",{"nope":1}]""")
        assertEquals(listOf("UCa"), rows.map { it.channelId })
    }

    @Test
    fun scanOutcomeReadsHeld() {
        val o = Wire.scanOutcome("""{"channels":4,"discovered":7,"queued":2,"held":3}""")
        assertEquals(3, o.held)
        assertEquals("3 uploads found but held until their schedule is due", o.heldSummary)
    }

    @Test
    fun scanOutcomeFallsBackOnUnusableBodies() {
        assertEquals(0, Wire.scanOutcome("null").held)
        assertEquals(0, Wire.scanOutcome("[]").held)
        assertNull(Wire.scanOutcome("{}").heldSummary)
    }

    @Test
    fun takeoutProblemAcceptsBothExportShapesAndRejectsJunk() {
        assertNull(Wire.takeoutProblem("""[{"channelId":"UC1"}]"""))
        assertNull(Wire.takeoutProblem("""{"subscriptions":[{"channelId":"UC1"}]}"""))
        assertTrue(Wire.takeoutProblem("")!!.contains("Paste"))
        assertTrue(Wire.takeoutProblem("not json")!!.contains("not valid JSON"))
        assertTrue(Wire.takeoutProblem("""{"foo":1}""")!!.contains("subscriptions array"))
        assertTrue(Wire.takeoutProblem("""{"subscriptions":[]}""")!!.contains("empty"))
    }

    @Test
    fun takeoutProblemToleratesAByteOrderMark() {
        assertNull(Wire.takeoutProblem("\uFEFF[{\"channelId\":\"UC1\"}]"))
        assertNull(Wire.takeoutProblem("  \uFEFF{\"subscriptions\":[{\"channelId\":\"UC1\"}]}  "))
    }

    @Test
    fun snapshotKeepsTheChannelsItCanRead() {
        val snap = Wire.snapshot(
            """{"source":"takeout","imported":2,"total":3,"created_at":"2026-10-12T09:00:00Z",
               "channels":[{"channel_id":"UCa"},{"broken":true},{"channel_id":"UCb"}]}""",
        )
        assertEquals("takeout", snap.source)
        assertEquals(2, snap.imported)
        assertEquals(listOf("UCa", "UCb"), snap.channels.map { it.channelId })
    }

    @Test
    fun unreadableServerReplyIsNotReportedAsTheServerBeingAsleep() {
        val decode = ApiException.fromTransport(
            kotlinx.serialization.SerializationException("Unexpected JSON token at offset 12"),
        )
        assertEquals("bad_response", decode.code)
        assertTrue(decode.message.contains("answered"))

        val dead = ApiException.fromTransport(java.net.ConnectException("refused"))
        assertEquals("unreachable", dead.code)
        assertTrue(dead.message.contains("Termux"))
    }

    @Test
    fun errorEnvelopeForNewCodesIsParsed() {
        val e = ErrorParser.parse(400, """{"error":{"code":"invalid_takeout","message":"bad json","details":null}}""")
        assertEquals("invalid_takeout", e.code)
        assertEquals("bad json", e.message)
        assertTrue(e.hint!!.contains("subscriptions"))

        val feed = ErrorParser.parse(400, """{"error":{"code":"import_failed","message":"cookies expired","details":null}}""")
        assertTrue(feed.hint!!.contains("YTMON_COOKIES_FILE"))

        val data = ErrorParser.parse(400, """{"error":{"code":"data_required","message":"no data","details":null}}""")
        assertEquals("data_required", data.code)
        assertTrue(data.hint!!.contains("subscriptions.json"))
    }

    @Test
    fun bulkPolicyResultDecodes() {
        assertEquals(7, json.decodeFromString(BulkPolicyResult.serializer(), """{"updated":7}""").updated)
    }
}