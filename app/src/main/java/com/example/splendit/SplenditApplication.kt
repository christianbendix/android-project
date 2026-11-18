package com.example.splendit

import android.app.Application
import android.content.Context
import com.example.splendit.auth.AuthInterceptor
import com.example.splendit.auth.AuthRepository
import com.example.splendit.auth.DataStoreSessionStore
import com.example.splendit.auth.PocketBaseApi
import com.example.splendit.auth.PocketBaseAuthRepository
import com.example.splendit.auth.TokenCipher
import com.example.splendit.auth.sessionDataStore
import com.example.splendit.data.GroupRepository
import com.example.splendit.data.InMemoryGroupRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Manual dependency injection: one repository for the whole app, so every ViewModel observes
 * the same data. Swap [InMemoryGroupRepository] for a Room/network-backed one later without
 * touching the ViewModels.
 */
class AppContainer(context: Context, scope: CoroutineScope) {
    val groupRepository: GroupRepository = InMemoryGroupRepository(scope)

    val authRepository: AuthRepository by lazy {
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
}

class SplenditApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, MainScope())
    }
}
