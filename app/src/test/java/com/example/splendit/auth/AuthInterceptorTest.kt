package com.example.splendit.auth

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {
    private val server = MockWebServer()
    private val store = FakeSessionStore(AuthRepositoryTest.SESSION)
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(server.url("/"), store))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Same server, reached through a different host name, so it counts as a foreign host. */
    private fun otherHostUrl() = server.url("/elsewhere").newBuilder()
        .host(if (server.hostName == "localhost") "127.0.0.1" else "localhost")
        .build()

    @Test
    fun addsHeader_forPocketBaseHost() {
        server.enqueue(MockResponse().setBody("{}"))

        PocketBaseApi(client, server.url("/")).getUser("qqw7x1psxmeuq72")

        assertEquals("tok-old", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun noHeader_forOtherHost() {
        server.enqueue(MockResponse())

        client.newCall(Request.Builder().url(otherHostUrl()).build()).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun keepsExplicitHeader() {
        server.enqueue(MockResponse().setBody("{}"))

        PocketBaseApi(client, server.url("/")).authRefresh("explicit-token")

        assertEquals("explicit-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun unauthorized_clearsSession() {
        server.enqueue(MockResponse().setResponseCode(401))

        PocketBaseApi(client, server.url("/")).getUser("qqw7x1psxmeuq72")

        assertNull(store.session.value)
    }

    @Test
    fun unauthorized_forOtherHost_keepsSession() {
        server.enqueue(MockResponse().setResponseCode(401))

        client.newCall(Request.Builder().url(otherHostUrl()).build()).execute().close()

        assertEquals(AuthRepositoryTest.SESSION, store.session.value)
    }

    @Test
    fun unauthorized_withStaleToken_keepsNewerSession() {
        server.enqueue(MockResponse().setResponseCode(401))

        PocketBaseApi(client, server.url("/")).authRefresh("stale-token")

        assertEquals(AuthRepositoryTest.SESSION, store.session.value)
    }
}
