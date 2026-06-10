package com.github.senocak.caaf

import com.github.senocak.caaf.core.FileCacheManager
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CacheConfigTest {
    @Test
    fun `creates file backed cache manager from properties`() {
        val manager = CacheConfig().cacheManager(
            FileCacheProperties(
                directory = "build/demo-cache-test",
                clearInterval = Duration.ofSeconds(5)
            )
        )

        val fileCacheManager = assertIs<FileCacheManager>(manager)
        try {
            assertEquals(0, fileCacheManager.cacheNames.size)
            assertTrue(fileCacheManager.isPeriodicClearEnabled())
        } finally {
            fileCacheManager.close()
        }
    }

    @Test
    fun `cache properties default to cache directory`() {
        val properties = FileCacheProperties()

        assertEquals("cache", properties.directory)
        assertNull(properties.clearInterval)
    }

    @Test
    fun `zero clear interval disables periodic clearing`() {
        val manager = CacheConfig().cacheManager(
            FileCacheProperties(
                directory = "build/demo-cache-test",
                clearInterval = Duration.ZERO
            )
        )

        val fileCacheManager = assertIs<FileCacheManager>(manager)
        try {
            assertFalse(fileCacheManager.isPeriodicClearEnabled())
        } finally {
            fileCacheManager.close()
        }
    }
}
