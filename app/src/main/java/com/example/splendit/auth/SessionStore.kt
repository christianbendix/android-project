package com.example.splendit.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class Session(val token: String, val userId: String, val email: String, val name: String) {
    // Keep the token out of logs and crash reports.
    override fun toString() = "Session(userId=$userId, email=$email)"
}

interface SessionStore {
    val session: Flow<Session?>
    suspend fun save(session: Session)
    suspend fun clear()
}

// files/datastore/session.preferences_pb, excluded from backup and device
// transfer in res/xml/backup_rules.xml and data_extraction_rules.xml.
val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * The token is encrypted with [TokenCipher]; if it can't be decrypted (e.g.
 * the Keystore key is gone) the session reads as null and the user logs in again.
 */
class DataStoreSessionStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher
) : SessionStore {

    override val session: Flow<Session?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val token = prefs[TOKEN]?.let(cipher::decrypt) ?: return@map null
            Session(
                token = token,
                userId = prefs[USER_ID].orEmpty(),
                email = prefs[EMAIL].orEmpty(),
                name = prefs[NAME].orEmpty()
            )
        }
        .distinctUntilChanged()

    override suspend fun save(session: Session) {
        val encrypted = cipher.encrypt(session.token)
        dataStore.edit {
            it[TOKEN] = encrypted
            it[USER_ID] = session.userId
            it[EMAIL] = session.email
            it[NAME] = session.name
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val NAME = stringPreferencesKey("name")
    }
}
