package com.example.splendit.data

import com.example.splendit.data.model.Group
import com.example.splendit.data.model.Person
import java.io.File
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Everything the repository needs to come back exactly as it was after a restart. */
@Serializable
data class Snapshot(val groups: List<Group>, val invited: Map<String, Person>)

/**
 * Offline cache for [InMemoryGroupRepository]: the whole state as one JSON file, so data the
 * user has seen stays readable with no network and after the process is killed.
 */
class GroupSnapshotStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Null when there is no cache yet or it can't be read; the caller falls back to seed data. */
    fun load(): Snapshot? = try {
        if (file.exists()) json.decodeFromString<Snapshot>(file.readText()) else null
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Writes to a temp file and renames it, so a kill mid-write never leaves a broken cache. */
    fun save(snapshot: Snapshot) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(snapshot))
        // rename(2) atomically replaces the old file on Android's Linux filesystems.
        if (!tmp.renameTo(file)) throw IOException("Could not replace $file")
    }
}
