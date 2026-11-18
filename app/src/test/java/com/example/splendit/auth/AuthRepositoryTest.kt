package com.example.splendit.auth

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {
    private val server = MockWebServer()
    private val store = FakeSessionStore()
    private lateinit var repository: PocketBaseAuthRepository

    @Before
    fun setUp() {
        server.start()
        repository = repositoryFor(server)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repositoryFor(target: MockWebServer) =
        PocketBaseAuthRepository(PocketBaseApi(OkHttpClient(), target.url("/")), store)

    @Test
    fun login_success_parsesAndStoresSession() = runTest {
        server.enqueue(json(200, authBody("tok-1")))

        val result = repository.login("demo@demo.local", "demo12345")

        val expected = Session("tok-1", "qqw7x1psxmeuq72", "demo@demo.local", "Demo User")
        assertEquals(AuthResult.Success(expected), result)
        assertEquals(expected, store.session.value)
        val request = server.takeRequest()
        assertEquals("/api/collections/users/auth-with-password", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("demo@demo.local", body.getString("identity"))
        assertEquals("demo12345", body.getString("password"))
    }

    @Test
    fun login_400_isInvalidCredentials() = runTest {
        server.enqueue(
            json(400, """{"status":400,"message":"Failed to authenticate.","data":{}}""")
        )

        assertEquals(AuthResult.InvalidCredentials, repository.login("a@b.co", "wrongpass"))
        assertNull(store.session.value)
    }

    @Test
    fun signUp_200_thenLogsIn() = runTest {
        server.enqueue(json(200, RECORD))
        server.enqueue(json(200, authBody("tok-new")))

        val result = repository.signUp("  new@example.com ", "password123", " New ")

        assertEquals("tok-new", (result as AuthResult.Success).session.token)
        assertEquals("tok-new", store.session.value?.token)
        val create = server.takeRequest()
        assertEquals("/api/collections/users/records", create.path)
        val createBody = JSONObject(create.body.readUtf8())
        assertEquals("new@example.com", createBody.getString("email"))
        assertEquals("password123", createBody.getString("password"))
        assertEquals("password123", createBody.getString("passwordConfirm"))
        assertEquals("New", createBody.getString("name"))
        val login = server.takeRequest()
        assertEquals("/api/collections/users/auth-with-password", login.path)
        assertEquals("new@example.com", JSONObject(login.body.readUtf8()).getString("identity"))
    }

    @Test
    fun signUp_400_notUnique_isFieldErrors() = runTest {
        server.enqueue(
            json(
                400,
                """{"status":400,"message":"Failed to create record.","data":
                {"email":{"code":"validation_not_unique","message":"Value must be unique."}}}"""
            )
        )

        val result = repository.signUp("demo@demo.local", "password123", "")

        assertEquals(AuthResult.FieldErrors(mapOf("email" to "validation_not_unique")), result)
        assertEquals("only the create call is made", 1, server.requestCount)
    }

    @Test
    fun signUp_403_isSignupDisabled() = runTest {
        server.enqueue(
            json(
                403,
                """{"status":403,"message":"Only superusers can perform this action.","data":{}}"""
            )
        )

        assertEquals(AuthResult.SignupDisabled, repository.signUp("a@b.co", "password123", ""))
    }

    @Test
    fun rateLimited_jsonBody() = runTest {
        server.enqueue(json(429, """{"status":429,"message":"Too Many Requests.","data":{}}"""))

        assertEquals(AuthResult.RateLimited, repository.login("a@b.co", "password123"))
    }

    @Test
    fun rateLimited_nonJsonBody() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("<html>slow down</html>"))

        assertEquals(AuthResult.RateLimited, repository.signUp("a@b.co", "password123", ""))
    }

    @Test
    fun malformedSuccessBody_isUnknown() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

        assertEquals(AuthResult.Unknown, repository.login("a@b.co", "password123"))
        assertNull(store.session.value)
    }

    @Test
    fun networkFailure_isNetwork() = runTest {
        val offline = MockWebServer().apply {
            start()
            shutdown()
        }

        assertEquals(AuthResult.Network, repositoryFor(offline).login("a@b.co", "password123"))
    }

    @Test
    fun restoreSession_noStoredSession_makesNoRequest() = runTest {
        assertNull(repository.restoreSession())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun restoreSession_200_savesNewToken() = runTest {
        store.session.value = SESSION
        server.enqueue(json(200, authBody("tok-refreshed")))

        assertEquals(
            "tok-refreshed",
            (repository.restoreSession() as AuthResult.Success).session.token
        )
        assertEquals("tok-refreshed", store.session.value?.token)
        val request = server.takeRequest()
        assertEquals("/api/collections/users/auth-refresh", request.path)
        assertEquals("tok-old", request.getHeader("Authorization"))
    }

    @Test
    fun restoreSession_401_clearsSession() = runTest {
        store.session.value = SESSION
        server.enqueue(json(401, """{"status":401,"message":"...","data":{}}"""))

        repository.restoreSession()

        assertNull(store.session.value)
    }

    @Test
    fun restoreSession_networkFailure_keepsSession() = runTest {
        store.session.value = SESSION
        val offline = MockWebServer().apply {
            start()
            shutdown()
        }

        assertEquals(AuthResult.Network, repositoryFor(offline).restoreSession())
        assertEquals(SESSION, store.session.value)
    }

    @Test
    fun restoreSession_serverError_keepsSession() = runTest {
        store.session.value = SESSION
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(AuthResult.Unknown, repository.restoreSession())
        assertEquals(SESSION, store.session.value)
    }

    @Test
    fun logout_clearsStore() = runTest {
        store.session.value = SESSION

        repository.logout()

        assertNull(store.session.value)
    }

    companion object {
        val SESSION = Session("tok-old", "qqw7x1psxmeuq72", "demo@demo.local", "Demo User")

        const val RECORD = """{"id":"qqw7x1psxmeuq72","collectionId":"_pb_users_auth_",
            "collectionName":"users","email":"demo@demo.local","emailVisibility":false,
            "verified":false,"name":"Demo User","avatar":"","created":"2026-11-18 14:13:21.901Z",
            "updated":"2026-11-18 14:13:21.901Z","someFutureField":42}"""

        fun authBody(token: String) = """{"token":"$token","record":$RECORD}"""

        fun json(code: Int, body: String) = MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }
}
