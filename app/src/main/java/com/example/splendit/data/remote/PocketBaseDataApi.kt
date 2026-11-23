package com.example.splendit.data.remote

import com.example.splendit.auth.ApiResponse
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * PocketBase record endpoints for the group collections. [client] must carry the
 * `AuthInterceptor`, which adds the signed-in user's token; the server's access rules then
 * decide what that user can see. Calls are blocking and throw [IOException] on network failure.
 */
class PocketBaseDataApi(private val client: OkHttpClient, private val baseUrl: HttpUrl) {

    /** All records the user may list, oldest first, following pagination. */
    fun list(collection: String, filter: String? = null): List<JSONObject> {
        val items = mutableListOf<JSONObject>()
        var page = 1
        while (true) {
            val url = baseUrl.newBuilder()
                .addPathSegments("api/collections/$collection/records")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("perPage", PER_PAGE.toString())
                .addQueryParameter("sort", "created")
                .apply { if (filter != null) addQueryParameter("filter", filter) }
                .build()
            val response = execute(Request.Builder().url(url).get().build())
            val body = response.body
            if (response.code != 200 || body == null) throw HttpException(response.code)
            val array = body.optJSONArray("items") ?: JSONArray()
            for (i in 0 until array.length()) items += array.getJSONObject(i)
            if (page >= body.optInt("totalPages", 1)) return items
            page++
        }
    }

    fun get(collection: String, id: String): ApiResponse =
        execute(Request.Builder().url(url("api/collections/$collection/records/$id")).get().build())

    fun create(collection: String, body: JSONObject): ApiResponse = execute(
        Request.Builder()
            .url(url("api/collections/$collection/records"))
            .post(body.toString().toRequestBody(JSON))
            .build()
    )

    /** Exact match on phone or email; see `pb_hooks/lookup.pb.js`. */
    fun lookupUser(query: String): ApiResponse {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/splendit/lookup")
            .addQueryParameter("q", query)
            .build()
        return execute(Request.Builder().url(url).get().build())
    }

    /** Points the realtime connection [clientId] at [topics]. */
    fun subscribe(clientId: String, topics: List<String>): ApiResponse = execute(
        Request.Builder()
            .url(url("api/realtime"))
            .post(
                JSONObject()
                    .put("clientId", clientId)
                    .put("subscriptions", JSONArray(topics))
                    .toString()
                    .toRequestBody(JSON)
            )
            .build()
    )

    fun realtimeUrl(): HttpUrl = url("api/realtime")

    private fun url(path: String) = baseUrl.newBuilder().addPathSegments(path).build()

    private fun execute(request: Request): ApiResponse = client.newCall(request).execute().use {
        val text = it.body?.string().orEmpty()
        val json = try {
            if (text.isBlank()) null else JSONObject(text)
        } catch (e: JSONException) {
            null
        }
        ApiResponse(it.code, json)
    }

    private companion object {
        const val PER_PAGE = 200
        val JSON = "application/json".toMediaType()
    }
}

/** The server answered, but not with success. */
class HttpException(val code: Int) : IOException("HTTP $code") {
    /** 5xx and 429 may succeed later; other 4xx won't. */
    val isTransient: Boolean get() = code >= 500 || code == 429 || code == 408
}
