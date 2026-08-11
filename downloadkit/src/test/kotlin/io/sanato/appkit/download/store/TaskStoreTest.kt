package io.sanato.appkit.download.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TaskStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var store: TaskStore

    @Before
    fun setUp() {
        store = TaskStore(tempFolder.newFolder("downloads"))
    }

    private fun sampleMeta(id: String = "task1") =
        TaskMetadata(
            id = id,
            url = "https://example.com/$id.zip",
            fileName = "$id.zip",
            destDir = "/tmp",
            bytesDownloaded = 512L,
            totalBytes = 1024L,
            etag = "\"abc123\"",
            state = PersistedState.PAUSED,
        )

    @Test
    fun `save then load round-trips every field`() {
        val meta = sampleMeta()

        store.save(meta)
        val loaded = store.load(meta.id)

        assertEquals(meta, loaded)
    }

    @Test
    fun `load returns null when no meta file exists`() {
        assertNull(store.load("nonexistent"))
    }

    @Test
    fun `save overwrites a previous checkpoint for the same id`() {
        store.save(sampleMeta().copy(bytesDownloaded = 100L))
        store.save(sampleMeta().copy(bytesDownloaded = 900L))

        assertEquals(900L, store.load("task1")?.bytesDownloaded)
    }

    @Test
    fun `loadAll returns every persisted task`() {
        store.save(sampleMeta("task1"))
        store.save(sampleMeta("task2"))
        store.save(sampleMeta("task3"))

        val ids = store.loadAll().map { it.id }.toSet()

        assertEquals(setOf("task1", "task2", "task3"), ids)
    }

    @Test
    fun `loadAll is empty for a freshly created store`() {
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `delete removes both the meta and part files`() {
        val meta = sampleMeta()
        store.save(meta)
        store.partFile(meta.id).writeBytes(ByteArray(10))

        store.delete(meta.id)

        assertFalse(store.metaFile(meta.id).exists())
        assertFalse(store.partFile(meta.id).exists())
        assertNull(store.load(meta.id))
    }

    @Test
    fun `destinationFile falls back to the store's own downloadDir when destDir is null`() {
        val file = store.destinationFile(destDir = null, fileName = "movie.mp4")

        assertEquals("movie.mp4", file.name)
        assertEquals(tempFolder.root.resolve("downloads"), file.parentFile)
    }

    @Test
    fun `destinationFile honors an explicit destDir override`() {
        val override = tempFolder.newFolder("elsewhere")

        val file = store.destinationFile(destDir = override, fileName = "movie.mp4")

        assertEquals(override, file.parentFile)
    }
}
