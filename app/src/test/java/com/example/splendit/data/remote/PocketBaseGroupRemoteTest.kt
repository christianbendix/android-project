package com.example.splendit.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The PocketBase client against a scripted server; never the real backend. */
class PocketBaseGroupRemoteTest {
    private val server = MockWebServer()
    private lateinit var remote: PocketBaseGroupRemote

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        val api = PocketBaseDataApi(client, server.url("/"))
        remote = PocketBaseGroupRemote(api, PocketBaseRealtime(client, api))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun page(vararg items: String, page: Int = 1, totalPages: Int = 1) = json(
        200,
        """{"page":$page,"perPage":200,"totalPages":$totalPages,"items":[${items.joinToString()}]}"""
    )

    @Test
    fun fetchAll_followsPagesAndLoadsTheMembers() = runBlocking {
        server.enqueue(
            page(
                """{"collectionName":"groups","id":"g1","name":"Trip","admin":"u1",
                "members":["u1","u2"],"created":"2026-11-20 10:00:00.000Z"}""",
                totalPages = 2
            )
        )
        server.enqueue(
            page(
                """{"collectionName":"groups","id":"g2","name":"Flat","admin":"u2",
                "members":["u2"],"created":"2026-11-21 10:00:00.000Z"}""",
                page = 2,
                totalPages = 2
            )
        )
        server.enqueue(page()) // expenses
        server.enqueue(page()) // payments
        server.enqueue(page()) // messages
        server.enqueue(
            page(
                """{"collectionName":"users","id":"u1","name":"Peter","phone":"+4511111111"}""",
                """{"collectionName":"users","id":"u2","name":"Sofie","phone":"+4520000002"}"""
            )
        )

        val snapshot = remote.fetchAll()

        assertEquals(listOf("g1", "g2"), snapshot.groups.map { it.id })
        assertEquals(setOf("Peter", "Sofie"), snapshot.users.map { it.name }.toSet())
        val first = server.takeRequest()
        assertEquals("/api/collections/groups/records", first.requestUrl!!.encodedPath)
        assertEquals("created", first.requestUrl!!.queryParameter("sort"))
        assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
        repeat(3) { server.takeRequest() }
        val users = server.takeRequest().requestUrl!!.queryParameter("filter")!!
        assertTrue(users, "id='u1'" in users && "id='u2'" in users)
    }

    @Test
    fun create_sendsTheClientChosenId() = runBlocking {
        server.enqueue(json(200, "{}"))

        remote.create(MessageRecord("m1", "g1", "u1", "Hi", created = 0))

        val request = server.takeRequest()
        assertEquals("/api/collections/messages/records", request.path)
        assertEquals("m1", JSONObject(request.body.readUtf8()).getString("id"))
    }

    @Test
    fun create_retryOfARecordTheServerAlreadyHas_isSuccess() = runBlocking {
        // The first attempt reached the server but its response was lost.
        server.enqueue(json(400, """{"data":{"id":{"code":"validation_invalid_id"}}}"""))
        server.enqueue(json(200, """{"id":"m1"}"""))

        remote.create(MessageRecord("m1", "g1", "u1", "Hi", created = 0))

        server.takeRequest()
        assertEquals("/api/collections/messages/records/m1", server.takeRequest().path)
    }

    @Test
    fun create_rejected_throwsANonTransientError() = runBlocking {
        server.enqueue(json(403, "{}"))
        try {
            remote.create(PaymentRecord("p1", "g1", "e1", "u2", "u1", 100, created = 0))
            fail("expected HttpException")
        } catch (e: HttpException) {
            assertEquals(403, e.code)
            assertTrue(!e.isTransient)
        }
    }

    @Test
    fun create_offline_throwsIOException() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            remote.create(MessageRecord("m1", "g1", "u1", "Hi", created = 0))
            fail("expected IOException")
        } catch (e: IOException) {
            // expected
        }
    }

    @Test
    fun findUser_returnsNullWhenNobodyHasIt() = runBlocking {
        server.enqueue(json(200, """{"id":"u2","name":"Sofie","phone":"+4520000002"}"""))
        server.enqueue(json(404, """{"message":"Not found"}"""))

        assertEquals("Sofie", remote.findUser("+4520000002")!!.name)
        assertNull(remote.findUser("nobody@example.com"))
        assertEquals(
            "+4520000002",
            server.takeRequest().requestUrl!!.queryParameter("q")
        )
    }

    @Test
    fun changes_subscribesThenEmitsRecords() = runBlocking {
        val stream = buildString {
            append("id:c1\nevent:PB_CONNECT\ndata:{\"clientId\":\"c1\"}\n\n")
            append("event:expenses\n")
            append(
                "data:{\"action\":\"create\",\"record\":{\"collectionName\":\"expenses\"," +
                    "\"id\":\"e1\",\"group\":\"g1\",\"amount_ore\":45000}}\n\n"
            )
            // Keep the stream open while the subscription request goes out.
            repeat(200) { append(": keep-alive\n") }
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(stream)
                .throttleBody(512, 200, TimeUnit.MILLISECONDS)
        )
        server.enqueue(MockResponse().setResponseCode(204))

        val changes = withTimeout(5_000) { remote.changes().take(2).toList() }

        assertTrue(RemoteChange.Connected in changes)
        val upsert = changes.filterIsInstance<RemoteChange.Upsert>().single()
        assertEquals(45_000L, (upsert.record as ExpenseRecord).amountOre)
        assertEquals("/api/realtime", server.takeRequest().path)
        val subscribe = server.takeRequest()
        assertEquals("POST", subscribe.method)
        val body = JSONObject(subscribe.body.readUtf8())
        assertEquals("c1", body.getString("clientId"))
        assertEquals(4, body.getJSONArray("subscriptions").length())
    }
}
