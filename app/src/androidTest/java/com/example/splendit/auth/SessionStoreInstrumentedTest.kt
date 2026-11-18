package com.example.splendit.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Keystore and DataStore only exist on a device, so these can't be JVM unit tests. */
@RunWith(AndroidJUnit4::class)
class SessionStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.filesDir, "datastore/session-test.preferences_pb")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
    private val cipher = TokenCipher(alias = "splendit_session_key_test")
    private val store = DataStoreSessionStore(dataStore, cipher)

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun cipher_roundTrips_andRejectsTampering() {
        val encrypted = cipher.encrypt("secret-token")

        assertFalse(encrypted.contains("secret-token"))
        assertEquals("secret-token", cipher.decrypt(encrypted))
        val tampered = encrypted.substring(0, encrypted.length - 4) + "AAAA"
        assertNull(cipher.decrypt(tampered))
        assertNull(cipher.decrypt("not base64 !!"))
    }

    @Test
    fun store_savesEncryptedToken_andClears() = runBlocking {
        val session = Session("secret-token", "qqw7x1psxmeuq72", "demo@demo.local", "Demo User")

        store.save(session)

        assertEquals(session, store.session.first())
        val raw = dataStore.data.first()[stringPreferencesKey("token")]
        assertFalse(raw!!.contains("secret-token"))

        store.clear()
        assertNull(store.session.first())
    }

    @Test
    fun undecryptableToken_readsAsSignedOut() = runBlocking {
        store.save(Session("secret-token", "id", "a@b.co", ""))
        TokenCipher(alias = "splendit_session_key_other").let { other ->
            assertNull(DataStoreSessionStore(dataStore, other).session.first())
        }
    }
}
