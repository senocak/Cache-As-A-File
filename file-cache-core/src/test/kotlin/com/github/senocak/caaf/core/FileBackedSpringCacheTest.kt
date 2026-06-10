package com.github.senocak.caaf.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.springframework.cache.Cache

class FileBackedSpringCacheTest {
    @Test
    fun `reloads typed values from disk`() {
        val directory: Path = Files.createTempDirectory("spring-file-cache-test")
        val manager = FileCacheManager(cacheDirectory = directory)
        val cache: Cache = manager.getCache("users")

        cache.put("42", CachedUser(id = "42", username = "ada"))

        val reloadedManager = FileCacheManager(cacheDirectory = directory)
        val reloadedValue = reloadedManager.getCache("users").get("42")?.get()

        assertIs<CachedUser>(value = reloadedValue)
        assertEquals(expected = CachedUser(id = "42", username = "ada"), actual = reloadedValue)
    }

    @Test
    fun `uses loader result when key is missing`() {
        val directory: Path = Files.createTempDirectory("spring-file-cache-test")
        val manager = FileCacheManager(cacheDirectory = directory)
        val cache: Cache = manager.getCache("users")

        val loaded: CachedUser? = cache.get("42") { CachedUser(id = "42", username = "ada") }

        assertEquals(expected = CachedUser(id = "42", username = "ada"), actual = loaded)
        assertEquals(expected = CachedUser(id = "42", username = "ada"), actual = cache.get("42", CachedUser::class.java))
    }

    @Test
    fun `returns wrapped typed and untyped values`() {
        val backingCache = InMemoryFileCache()
        val cache = FileBackedSpringCache(
            name = "users",
            fileCache = backingCache,
            objectMapper = JsonFileCache.defaultObjectMapper()
        )

        cache.put("42", CachedUser(id = "42", username = "ada"))

        assertEquals("users", cache.name)
        assertSame(backingCache, cache.nativeCache)
        assertEquals(CachedUser(id = "42", username = "ada"), cache.get("42")?.get())
        assertEquals(CachedUser(id = "42", username = "ada"), cache.get("42", CachedUser::class.java))
        assertEquals(CachedUser(id = "42", username = "ada"), cache.get("42", null as Class<Any>?))
        assertNull(cache.get("42", String::class.java))
        assertNull(cache.get("missing"))
    }

    @Test
    fun `does not cache null values`() {
        val backingCache = InMemoryFileCache()
        val cache = FileBackedSpringCache(
            name = "users",
            fileCache = backingCache,
            objectMapper = JsonFileCache.defaultObjectMapper()
        )

        cache.put("42", null)

        assertFalse(backingCache.containsKey("42"))
        assertNull(cache.get("42"))
    }

    @Test
    fun `uses cached value instead of invoking loader`() {
        val backingCache = InMemoryFileCache()
        val cache = FileBackedSpringCache(
            name = "users",
            fileCache = backingCache,
            objectMapper = JsonFileCache.defaultObjectMapper()
        )
        cache.put("42", CachedUser(id = "42", username = "ada"))

        val cached: CachedUser? = cache.get("42") {
            throw IllegalStateException("loader should not be called")
        }

        assertEquals(CachedUser(id = "42", username = "ada"), cached)
    }

    @Test
    fun `wraps loader exceptions in value retrieval exception`() {
        val cache = FileBackedSpringCache(
            name = "users",
            fileCache = InMemoryFileCache(),
            objectMapper = JsonFileCache.defaultObjectMapper()
        )

        val exception = assertFailsWith<Cache.ValueRetrievalException> {
            cache.get("42") {
                throw IllegalStateException("boom")
            }
        }

        assertIs<IllegalStateException>(exception.cause)
        assertEquals("boom", exception.cause?.message)
    }

    @Test
    fun `evicts clears and invalidates entries`() {
        val backingCache = InMemoryFileCache()
        val cache = FileBackedSpringCache(
            name = "users",
            fileCache = backingCache,
            objectMapper = JsonFileCache.defaultObjectMapper()
        )

        cache.put("42", CachedUser(id = "42", username = "ada"))
        cache.evict("42")
        assertNull(cache.get("42"))

        cache.put("43", CachedUser(id = "43", username = "grace"))
        cache.clear()
        assertEquals(0, backingCache.size())

        cache.put("44", CachedUser(id = "44", username = "katherine"))
        assertTrue(cache.invalidate())
        assertEquals(0, backingCache.size())
    }

    @Test
    fun `fires decoded spring cache insert and evict events`() {
        val events: MutableList<CacheEvent<String, Any>> = mutableListOf()
        val cache = FileBackedSpringCache(
            name = "users",
            fileCache = InMemoryFileCache(),
            objectMapper = JsonFileCache.defaultObjectMapper(),
            eventListeners = listOf(CacheEventListener { event: CacheEvent<String, Any> ->
                events.add(event)
            })
        )

        cache.put("42", CachedUser(id = "42", username = "ada"))
        cache.put("42", CachedUser(id = "42", username = "grace"))
        cache.evict("42")

        val inserted = assertIs<CacheInsertedEvent<String, Any>>(events[0])
        assertEquals("users", inserted.cacheName)
        assertEquals("42", inserted.key)
        assertEquals(CachedUser(id = "42", username = "ada"), inserted.value)
        assertNull(inserted.previousValue)

        val updated = assertIs<CacheInsertedEvent<String, Any>>(events[1])
        assertEquals(CachedUser(id = "42", username = "grace"), updated.value)
        assertEquals(CachedUser(id = "42", username = "ada"), updated.previousValue)

        val evicted = assertIs<CacheEvictedEvent<String, Any>>(events[2])
        assertEquals("42", evicted.key)
        assertEquals(CachedUser(id = "42", username = "grace"), evicted.value)
    }

    @Test
    fun `fires decoded evict events when clearing spring cache`() {
        val events: MutableList<CacheEvent<String, Any>> = mutableListOf()
        val cache = FileBackedSpringCache(
            name = "users",
            fileCache = InMemoryFileCache(),
            objectMapper = JsonFileCache.defaultObjectMapper(),
            eventListeners = listOf(CacheEventListener { event: CacheEvent<String, Any> ->
                events.add(event)
            })
        )
        cache.put("42", CachedUser(id = "42", username = "ada"))
        cache.put("43", CachedUser(id = "43", username = "grace"))
        events.clear()

        cache.clear()

        assertEquals(
            setOf(
                "42" to CachedUser(id = "42", username = "ada"),
                "43" to CachedUser(id = "43", username = "grace")
            ),
            events.map { event: CacheEvent<String, Any> ->
                val evicted = assertIs<CacheEvictedEvent<String, Any>>(event)
                evicted.key to evicted.value
            }.toSet()
        )
    }

    data class CachedUser(
        val id: String,
        val username: String
    ) {
        val displayName: String
            get() = username.uppercase()
    }

    private class InMemoryFileCache : FileCache<String, SpringCacheEntry> {
        private val entries: LinkedHashMap<String, SpringCacheEntry> = LinkedHashMap()

        override fun get(key: String): SpringCacheEntry? =
            entries[key]

        override fun put(key: String, value: SpringCacheEntry) {
            entries[key] = value
        }

        override fun evict(key: String): SpringCacheEntry? =
            entries.remove(key)

        override fun clear() {
            entries.clear()
        }

        override fun containsKey(key: String): Boolean =
            entries.containsKey(key)

        override fun size(): Int =
            entries.size

        override fun keys(): Set<String> =
            entries.keys
    }
}
