package com.example.splendit

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.splendit.auth.AuthInterceptor
import com.example.splendit.auth.AuthRepository
import com.example.splendit.auth.DataStoreSessionStore
import com.example.splendit.auth.PocketBaseApi
import com.example.splendit.auth.PocketBaseAuthRepository
import com.example.splendit.auth.TokenCipher
import com.example.splendit.auth.sessionDataStore
import com.example.splendit.data.GroupRepository
import com.example.splendit.data.GroupSnapshotStore
import com.example.splendit.data.InMemoryGroupRepository
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Manual dependency injection: one repository for the whole app, so every ViewModel observes
 * the same data. Group data is cached in files/groups.json so it survives restarts offline.
 */
class AppContainer(context: Context, scope: CoroutineScope) {
    val groupRepository: GroupRepository = InMemoryGroupRepository(
        scope,
        store = GroupSnapshotStore(File(context.filesDir, "groups.json"))
    )

    /** Instrumented tests swap in a fake before the activity starts, to skip the backend. */
    var authRepository: AuthRepository = run {
        val baseUrl = BuildConfig.PB_BASE_URL.toHttpUrl()
        val store = DataStoreSessionStore(context.sessionDataStore, TokenCipher())
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(baseUrl, store))
            .build()
        PocketBaseAuthRepository(PocketBaseApi(client, baseUrl), store)
    }
        @VisibleForTesting set
}

class SplenditApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, MainScope())
    }
}
