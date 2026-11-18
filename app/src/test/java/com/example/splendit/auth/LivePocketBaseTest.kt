package com.example.splendit.auth

import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Hits the real backend. Skipped by default; run with
 * `./gradlew testDebugUnitTest -PliveTests --tests '*LivePocketBaseTest'`.
 * Kept to five requests because of the server's per-IP rate limits.
 */
class LivePocketBaseTest {
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val api = PocketBaseApi(client, "https://pb-demo.cbendix.dk".toHttpUrl())

    @Before
    fun requireOptIn() {
        assumeTrue("live tests need -PliveTests", System.getProperty("liveTests") == "true")
    }

    @Test
    fun demoLogin_thenRefresh() {
        val login = api.authWithPassword("demo@demo.local", "demo12345")
        assertEquals(200, login.code)
        val session = login.body!!.toSession()
        assertTrue(session.token.isNotEmpty())
        assertEquals("demo@demo.local", session.email)

        assertEquals(200, api.authRefresh(session.token).code)
    }

    @Test
    fun wrongPassword_is400() {
        assertEquals(400, api.authWithPassword("demo@demo.local", "wrong-password").code)
    }

    @Test
    fun signUp_thenLogin() {
        val email = "apptest-${System.currentTimeMillis()}@example.com"
        val password = randomPassword()

        assertEquals(200, api.createUser(email, password, "App Test").code)
        assertEquals(200, api.authWithPassword(email, password).code)
    }

    private fun randomPassword(): String {
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val random = SecureRandom()
        return (1..16).map { chars[random.nextInt(chars.size)] }.joinToString("")
    }
}
