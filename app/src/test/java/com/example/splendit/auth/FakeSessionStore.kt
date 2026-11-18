package com.example.splendit.auth

import kotlinx.coroutines.flow.MutableStateFlow

class FakeSessionStore(initial: Session? = null) : SessionStore {
    override val session = MutableStateFlow(initial)

    override suspend fun save(session: Session) {
        this.session.value = session
    }

    override suspend fun clear() {
        session.value = null
    }
}
