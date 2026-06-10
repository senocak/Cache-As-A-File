package com.github.senocak.caaf.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonFileCacheTest {
    @Test
    fun `persists and reloads values`() {
        val directory = Files.createTempDirectory("json-file-cache-test")
        val cache = JsonFileCache(
            cacheName = "users",
            keyType = String::class.java,
            valueType = CachedUser::class.java,
            cacheDirectory = directory
        )

        cache.put("1", CachedUser(id = "1", username = "ada"))

        val reloaded = JsonFileCache(
            cacheName = "users",
            keyType = String::class.java,
            valueType = CachedUser::class.java,
            cacheDirectory = directory
        )

        assertEquals(CachedUser(id = "1", username = "ada"), reloaded.get("1"))
        assertTrue(reloaded.containsKey("1"))
        assertEquals(setOf("1"), reloaded.keys())
    }

    @Test
    fun `evicts and clears persisted values`() {
        val directory = Files.createTempDirectory("json-file-cache-test")
        val cache = JsonFileCache(
            cacheName = "users",
            keyType = String::class.java,
            valueType = CachedUser::class.java,
            cacheDirectory = directory
        )

        cache.put("1", CachedUser(id = "1", username = "ada"))
        assertEquals(CachedUser(id = "1", username = "ada"), cache.evict("1"))
        assertNull(cache.get("1"))

        cache.put("2", CachedUser(id = "2", username = "grace"))
        cache.clear()

        val reloaded = JsonFileCache(
            cacheName = "users",
            keyType = String::class.java,
            valueType = CachedUser::class.java,
            cacheDirectory = directory
        )

        assertFalse(reloaded.containsKey("2"))
        assertEquals(0, reloaded.size())
    }

    data class CachedUser(
        val id: String,
        val username: String
    )
}
