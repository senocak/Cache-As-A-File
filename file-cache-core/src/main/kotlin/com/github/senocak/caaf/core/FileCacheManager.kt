package com.github.senocak.caaf.core

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

/**
 * Spring CacheManager that creates one persistent JSON file per Spring cache name.
 */
class FileCacheManager(
    private val cacheDirectory: Path = Path.of("cache"),
    private val objectMapper: ObjectMapper = JsonFileCache.defaultObjectMapper(),
    private val keySerializer: (Any) -> String = Any::toString
) : CacheManager {
    private val caches: ConcurrentHashMap<String, Cache> = ConcurrentHashMap()

    override fun getCache(name: String): Cache =
        caches.computeIfAbsent(name) { cacheName ->
            val fileCache = JsonFileCache(
                cacheName = cacheName,
                keyType = String::class.java,
                valueType = SpringCacheEntry::class.java,
                cacheDirectory = cacheDirectory,
                objectMapper = objectMapper
            )
            FileBackedSpringCache(
                name = cacheName,
                fileCache = fileCache,
                objectMapper = objectMapper,
                keySerializer = keySerializer
            )
        }

    override fun getCacheNames(): Collection<String> =
        caches.keys.toList()

    fun clearAll() {
        caches.values.forEach(Cache::clear)
    }

    fun getCacheCount(): Int =
        caches.size
}
