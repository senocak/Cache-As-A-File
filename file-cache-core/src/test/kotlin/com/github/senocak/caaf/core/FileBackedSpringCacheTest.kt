package com.github.senocak.caaf.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    data class CachedUser(
        val id: String,
        val username: String
    ) {
        val displayName: String
            get() = username.uppercase()
    }
}