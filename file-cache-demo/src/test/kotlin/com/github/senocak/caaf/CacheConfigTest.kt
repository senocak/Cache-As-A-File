package com.github.senocak.caaf

import com.github.senocak.caaf.core.FileCacheManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CacheConfigTest {
    @Test
    fun `creates file backed cache manager from properties`() {
        val manager = CacheConfig().cacheManager(FileCacheProperties(directory = "build/demo-cache-test"))

        assertIs<FileCacheManager>(manager)
        assertEquals(0, manager.cacheNames.size)
    }

    @Test
    fun `cache properties default to cache directory`() {
        assertEquals("cache", FileCacheProperties().directory)
    }
}
