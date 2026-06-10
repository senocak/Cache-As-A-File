package com.github.senocak.caaf.core

import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
    fun `reads current values from file for each get`() {
        val directory = Files.createTempDirectory("json-file-cache-test")
        val firstCache = JsonFileCache(
            cacheName = "users",
            keyType = String::class.java,
            valueType = CachedUser::class.java,
            cacheDirectory = directory
        )
        val secondCache = JsonFileCache(
            cacheName = "users",
            keyType = String::class.java,
            valueType = CachedUser::class.java,
            cacheDirectory = directory
        )

        firstCache.put("1", CachedUser(id = "1", username = "ada"))
        secondCache.put("1", CachedUser(id = "1", username = "grace"))

        assertEquals(CachedUser(id = "1", username = "grace"), firstCache.get("1"))
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

    @Test
    fun `rejects blank and unsafe cache names`() {
        val directory = Files.createTempDirectory("json-file-cache-test")

        assertFailsWith<IllegalArgumentException> {
            JsonFileCache(
                cacheName = " ",
                keyType = String::class.java,
                valueType = CachedUser::class.java,
                cacheDirectory = directory
            )
        }

        assertFailsWith<IllegalArgumentException> {
            JsonFileCache(
                cacheName = "users/active",
                keyType = String::class.java,
                valueType = CachedUser::class.java,
                cacheDirectory = directory
            )
        }
    }

    @Test
    fun `loads an empty cache file as an empty cache`() {
        val directory = Files.createTempDirectory("json-file-cache-test")
        Files.createFile(directory.resolve("users.json"))

        val cache = JsonFileCache(
            cacheName = "users",
            keyType = String::class.java,
            valueType = CachedUser::class.java,
            cacheDirectory = directory
        )

        assertEquals(0, cache.size())
        assertEquals(emptySet(), cache.keys())
    }

    @Test
    fun `supports cache names with dots dashes and underscores`() {
        val directory = Files.createTempDirectory("json-file-cache-test")
        val cache = JsonFileCache(
            cacheName = "users-v1.active_cache",
            keyType = String::class.java,
            valueType = CachedUser::class.java,
            cacheDirectory = directory
        )

        cache.put("1", CachedUser(id = "1", username = "ada"))

        assertTrue(Files.exists(directory.resolve("users-v1.active_cache.json")))
    }

    @Test
    fun `returns null when evicting a missing key`() {
        val directory = Files.createTempDirectory("json-file-cache-test")
        val cache = JsonFileCache(
            cacheName = "users",
            keyType = String::class.java,
            valueType = CachedUser::class.java,
            cacheDirectory = directory
        )

        cache.put("1", CachedUser(id = "1", username = "ada"))

        assertNull(cache.evict("missing"))
        assertEquals(CachedUser(id = "1", username = "ada"), cache.get("1"))
        assertEquals(1, cache.size())
    }

    @Test
    fun `persists java time values as reloadable json`() {
        val directory = Files.createTempDirectory("json-file-cache-test")
        val cache = JsonFileCache(
            cacheName = "events",
            keyType = String::class.java,
            valueType = CachedEvent::class.java,
            cacheDirectory = directory
        )

        cache.put("1", CachedEvent(id = "1", happenedOn = LocalDate.of(2026, 6, 10)))

        val reloaded = JsonFileCache(
            cacheName = "events",
            keyType = String::class.java,
            valueType = CachedEvent::class.java,
            cacheDirectory = directory
        )

        assertEquals(CachedEvent(id = "1", happenedOn = LocalDate.of(2026, 6, 10)), reloaded.get("1"))
    }

    data class CachedUser(
        val id: String,
        val username: String
    )

    data class CachedEvent(
        val id: String,
        val happenedOn: LocalDate
    )
}
