package com.example.splendit.data.remote

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONException
import org.json.JSONObject

sealed interface RealtimeMessage {
    /** Subscribed; anything that changed before this point needs a reload to be seen. */
    data object Connected : RealtimeMessage

    data class Event(val action: String, val record: JSONObject) : RealtimeMessage
}

/**
 * PocketBase realtime over server-sent events. The server first sends `PB_CONNECT` with a
 * client id; the client then POSTs its topics, and the server pushes every create, update and
 * delete the user is allowed to see. The flow fails with an [IOException] when the connection
 * drops; the caller decides when to reconnect.
 *
 * [client] must have no read timeout, since the stream is idle between changes.
 */
class PocketBaseRealtime(private val client: OkHttpClient, private val api: PocketBaseDataApi) {

    fun connect(topics: List<String>): Flow<RealtimeMessage> = callbackFlow {
        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                val json = try {
                    JSONObject(data)
                } catch (e: JSONException) {
                    return
                }
                if (type == "PB_CONNECT") {
                    val clientId = json.optString("clientId")
                    launch(Dispatchers.IO) {
                        try {
                            val response = api.subscribe(clientId, topics)
                            if (response.code !in 200..299) throw HttpException(response.code)
                            send(RealtimeMessage.Connected)
                        } catch (e: IOException) {
                            close(e)
                        }
                    }
                } else {
                    val record = json.optJSONObject("record") ?: return
                    trySend(RealtimeMessage.Event(json.optString("action"), record))
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close(IOException("Realtime connection closed"))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t as? IOException ?: IOException("Realtime failed: ${response?.code}", t))
            }
        }
        val request = Request.Builder().url(api.realtimeUrl()).build()
        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }.buffer(Channel.UNLIMITED)
}
