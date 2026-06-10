package com.github.senocak.caaf.core

import java.nio.file.Files
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.springframework.cache.Cache

class FileCacheManagerTest {
    @Test
    fun `reuses caches and reports cache names`() {
        val directory = Files.createTempDirectory("file-cache-manager-test")
        val manager = FileCacheManager(cacheDirectory = directory)

        val users = manager.getCache("users")
        val usersAgain = manager.getCache("users")
        manager.getCache("products")

        assertSame(users, usersAgain)
        assertEquals(2, manager.getCacheCount())
        assertEquals(setOf("users", "products"), manager.cacheNames.toSet())
    }

    @Test
    fun `clears all managed caches`() {
        val directory = Files.createTempDirectory("file-cache-manager-test")
        val manager = FileCacheManager(cacheDirectory = directory)
        val users = manager.getCache("users")
        val products = manager.getCache("products")

        users.put("1", CachedUser(id = "1", username = "ada"))
        products.put("2", CachedUser(id = "2", username = "grace"))

        manager.clearAll()

        assertNull(users.get("1"))
        assertNull(products.get("2"))
    }

    @Test
    fun `uses custom key serializer for managed caches`() {
        val directory = Files.createTempDirectory("file-cache-manager-test")
        val manager = FileCacheManager(
            cacheDirectory = directory,
            keySerializer = { key -> (key as LookupKey).id }
        )
        val cache: Cache = manager.getCache("users")

        cache.put(LookupKey("42"), CachedUser(id = "42", username = "ada"))

        val reloadedManager = FileCacheManager(
            cacheDirectory = directory,
            keySerializer = { key -> (key as LookupKey).id }
        )

        assertEquals(
            CachedUser(id = "42", username = "ada"),
            reloadedManager.getCache("users").get(LookupKey("42"), CachedUser::class.java)
        )
    }

    @Test
    fun `publishes cache events from managed caches`() {
        val directory = Files.createTempDirectory("file-cache-manager-test")
        val events: MutableList<CacheEvent<String, Any>> = mutableListOf()
        val manager = FileCacheManager(
            cacheDirectory = directory,
            eventListeners = listOf(CacheEventListener { event: CacheEvent<String, Any> ->
                events.add(event)
            })
        )
        val cache: Cache = manager.getCache("users")

        cache.put("42", CachedUser(id = "42", username = "ada"))
        cache.evict("42")

        val inserted = assertIs<CacheInsertedEvent<String, Any>>(events[0])
        assertEquals("users", inserted.cacheName)
        assertEquals("42", inserted.key)
        assertEquals(CachedUser(id = "42", username = "ada"), inserted.value)

        val evicted = assertIs<CacheEvictedEvent<String, Any>>(events[1])
        assertEquals("users", evicted.cacheName)
        assertEquals("42", evicted.key)
        assertEquals(CachedUser(id = "42", username = "ada"), evicted.value)
    }

    @Test
    fun `periodically clears managed caches when clear interval is configured`() {
        val directory = Files.createTempDirectory("file-cache-manager-test")
        val manager = FileCacheManager(
            cacheDirectory = directory,
            clearInterval = Duration.ofMillis(25)
        )

        try {
            val users = manager.getCache("users")
            users.put("1", CachedUser(id = "1", username = "ada"))

            assertTrue(manager.isPeriodicClearEnabled())
            assertClearedWithin(timeout = Duration.ofSeconds(2)) {
                users.get("1") == null
            }
        } finally {
            manager.close()
        }
    }

    @Test
    fun `does not schedule periodic clearing when clear interval is absent or zero`() {
        val directory = Files.createTempDirectory("file-cache-manager-test")
        val managerWithoutInterval = FileCacheManager(cacheDirectory = directory)
        val managerWithZeroInterval = FileCacheManager(cacheDirectory = directory, clearInterval = Duration.ZERO)

        try {
            assertFalse(managerWithoutInterval.isPeriodicClearEnabled())
            assertFalse(managerWithZeroInterval.isPeriodicClearEnabled())
        } finally {
            managerWithoutInterval.close()
            managerWithZeroInterval.close()
        }
    }

    private fun assertClearedWithin(timeout: Duration, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10)
        }
        assertTrue(condition(), "Cache was not cleared within $timeout")
    }

    data class CachedUser(
        val id: String,
        val username: String
    )

    private class LookupKey(
        val id: String
    ) {
        override fun toString(): String =
            "lookup-key-$id"
    }
}
