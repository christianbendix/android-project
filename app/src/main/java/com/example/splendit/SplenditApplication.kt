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
import com.example.splendit.data.RemoteGroupRepository
import com.example.splendit.data.remote.PocketBaseDataApi
import com.example.splendit.data.remote.PocketBaseGroupRemote
import com.example.splendit.data.remote.PocketBaseRealtime
import com.example.splendit.notifications.GroupNotifier
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Manual dependency injection: one repository for the whole app, so every ViewModel observes
 * the same data.
 */
class AppContainer(context: Context, private val scope: CoroutineScope) {
    private val baseUrl = BuildConfig.PB_BASE_URL.toHttpUrl()
    private val store = DataStoreSessionStore(context.sessionDataStore, TokenCipher())
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(baseUrl, store))
        .build()

    val notifier = GroupNotifier(context)

    /** Instrumented tests swap in a fake before the activity starts, to skip the backend. */
    var authRepository: AuthRepository =
        PocketBaseAuthRepository(PocketBaseApi(client, baseUrl), store)
        @VisibleForTesting set

    private var groups: GroupRepository? = null

    /**
     * Created on first use, so a test that swaps in a fake first never connects to the server.
     * The real one follows the stored session and raises a notification for each change made
     * by another member.
     */
    var groupRepository: GroupRepository
        get() = groups ?: createRemoteGroups().also { groups = it }

        @VisibleForTesting set(value) {
            groups = value
        }

    private fun createRemoteGroups(): GroupRepository {
        val api = PocketBaseDataApi(client, baseUrl)
        // The realtime stream is idle between changes, so it must not time out on reads.
        val streaming = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()
        val remote = PocketBaseGroupRemote(api, PocketBaseRealtime(streaming, api))
        return RemoteGroupRepository(remote, store.session, scope).also { repository ->
            scope.launch { repository.events.collect(notifier::show) }
        }
    }
}

class SplenditApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, MainScope())
        container.notifier.createChannels()
    }
}
