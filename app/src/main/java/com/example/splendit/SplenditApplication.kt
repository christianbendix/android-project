package com.example.splendit

import android.app.Application
import com.example.splendit.auth.AuthInterceptor
import com.example.splendit.auth.AuthRepository
import com.example.splendit.auth.DataStoreSessionStore
import com.example.splendit.auth.PocketBaseApi
import com.example.splendit.auth.PocketBaseAuthRepository
import com.example.splendit.auth.TokenCipher
import com.example.splendit.auth.sessionDataStore
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** Manual DI: app-wide singletons, created on first use. */
class SplenditApplication : Application() {

    val authRepository: AuthRepository by lazy {
        val baseUrl = BuildConfig.PB_BASE_URL.toHttpUrl()
        val store = DataStoreSessionStore(sessionDataStore, TokenCipher())
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(baseUrl, store))
            .build()
        PocketBaseAuthRepository(PocketBaseApi(client, baseUrl), store)
    }
}
