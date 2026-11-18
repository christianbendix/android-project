package com.example.splendit.auth

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject

/** Status code plus the parsed JSON body, or null if the body was empty or not JSON. */
class ApiResponse(val code: Int, val body: JSONObject?)

/**
 * The four PocketBase endpoints the app uses. Calls are blocking and throw
 * [IOException] on network failure; callers must run them off the main thread.
 */
class PocketBaseApi(private val client: OkHttpClient, private val baseUrl: HttpUrl) {

    fun createUser(email: String, password: String, name: String): ApiResponse = post(
        "api/collections/users/records",
        JSONObject()
            .put("email", email)
            .put("password", password)
            .put("passwordConfirm", password)
            .put("name", name)
    )

    fun authWithPassword(email: String, password: String): ApiResponse = post(
        "api/collections/users/auth-with-password",
        JSONObject().put("identity", email).put("password", password)
    )

    fun authRefresh(token: String): ApiResponse =
        execute(request("api/collections/users/auth-refresh", token).post(EMPTY_BODY).build())

    fun getUser(id: String, token: String? = null): ApiResponse =
        execute(request("api/collections/users/records/$id", token).get().build())

    private fun post(path: String, json: JSONObject): ApiResponse =
        execute(request(path, token = null).post(json.toString().toRequestBody(JSON)).build())

    private fun request(path: String, token: String?): Request.Builder {
        val url = baseUrl.newBuilder().addPathSegments(path).build()
        return Request.Builder().url(url).apply {
            if (token != null) header(AUTHORIZATION, token)
        }
    }

    private fun execute(request: Request): ApiResponse = client.newCall(request).execute().use {
        ApiResponse(it.code, it.body?.string()?.let(::parseJsonOrNull))
    }

    companion object {
        const val AUTHORIZATION = "Authorization"
        private val JSON = "application/json".toMediaType()
        private val EMPTY_BODY = "{}".toRequestBody(JSON)

        private fun parseJsonOrNull(text: String): JSONObject? = try {
            JSONObject(text)
        } catch (e: JSONException) {
            null
        }
    }
}

/** Reads `{"token", "record"}` from an auth response. Throws [JSONException] if malformed. */
fun JSONObject.toSession(): Session {
    val record = getJSONObject("record")
    return Session(
        token = getString("token").ifEmpty { throw JSONException("empty token") },
        userId = record.getString("id"),
        email = record.optString("email"),
        name = record.optString("name")
    )
}

/** Maps `data.<field>.code` from a PocketBase error body, e.g. `email -> validation_not_unique`. */
fun JSONObject.fieldErrorCodes(): Map<String, String> {
    val data = optJSONObject("data") ?: return emptyMap()
    return data.keys().asSequence().associateWith { field ->
        data.optJSONObject(field)?.optString("code").orEmpty()
    }
}
