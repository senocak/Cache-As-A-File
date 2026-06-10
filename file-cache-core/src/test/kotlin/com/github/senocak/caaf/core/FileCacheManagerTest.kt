package com.github.senocak.caaf.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
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
