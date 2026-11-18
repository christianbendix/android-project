package com.example.splendit.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: <token>` to requests for the PocketBase host only, so
 * the token never leaks to another server. A 401 on an authorized request
 * clears the session, which sends the user back to the login screen.
 */
class AuthInterceptor(private val baseUrl: HttpUrl, private val store: SessionStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.scheme != baseUrl.scheme || url.host != baseUrl.host || url.port != baseUrl.port) {
            return chain.proceed(request)
        }

        val explicit = request.header(PocketBaseApi.AUTHORIZATION)
        val token = explicit ?: runBlocking { store.session.first()?.token }
        val response = chain.proceed(
            if (explicit == null && token != null) {
                request.newBuilder().header(PocketBaseApi.AUTHORIZATION, token).build()
            } else {
                request
            }
        )
        if (response.code == 401 && token != null) {
            // Only clear if the rejected token is still the stored one; a fresh
            // login may have replaced it while this request was in flight.
            runBlocking {
                if (store.session.first()?.token == token) store.clear()
            }
        }
        return response
    }
}
