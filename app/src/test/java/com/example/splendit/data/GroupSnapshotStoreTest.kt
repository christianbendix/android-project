package com.example.splendit.data

import com.example.splendit.data.model.Message
import com.example.splendit.data.model.Person
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Offline availability: group data survives a restart, and a bad cache never crashes. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupSnapshotStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val file get() = tmp.root.resolve("groups.json")

    private fun TestScope.repo() = InMemoryGroupRepository(
        backgroundScope,
        store = GroupSnapshotStore(file),
        io = StandardTestDispatcher(testScheduler)
    )

    @Test
    fun stateSurvivesRestart() = runTest {
        val before = repo()
        val invite = Person("tel12345678", "+45 12 34 56 78", "+45 12 34 56 78", "#", "")
        val id = before.createGroup("Offline trip", null, listOf(invite.id), listOf(invite), 200.0)
        before.sendText(id, "still here?")
        runCurrent()

        val after = repo()

        assertEquals(before.groups.value, after.groups.value)
        val last = after.group(id)!!.messages.last() as Message.Text
        assertEquals("still here?", last.text)
        assertEquals("+45 12 34 56 78", after.person(invite.id).fullName)
    }

    @Test
    fun noCacheStartsFromSeedData() = runTest {
        assertNull(GroupSnapshotStore(file).load())
        assertEquals(SeedData.groups, repo().groups.value)
    }

    @Test
    fun corruptCacheFallsBackToSeedData() = runTest {
        file.writeText("{not json")

        assertNull(GroupSnapshotStore(file).load())
        assertEquals(SeedData.groups, repo().groups.value)
    }

    @Test
    fun cacheIsRewrittenAfterCorruption() = runTest {
        file.writeText("{not json")
        val repo = repo()
        repo.markRead("g1")
        runCurrent()

        assertEquals(repo.groups.value, GroupSnapshotStore(file).load()!!.groups)
    }
}
