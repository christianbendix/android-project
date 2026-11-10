package com.example.splendit

import android.app.Application
import com.example.splendit.data.GroupRepository
import com.example.splendit.data.InMemoryGroupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

/**
 * Manual dependency injection: one repository for the whole app, so every ViewModel observes
 * the same data. Swap [InMemoryGroupRepository] for a Room/network-backed one later without
 * touching the ViewModels.
 */
class AppContainer(scope: CoroutineScope) {
    val groupRepository: GroupRepository = InMemoryGroupRepository(scope)
}

class SplenditApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(MainScope())
    }
}
