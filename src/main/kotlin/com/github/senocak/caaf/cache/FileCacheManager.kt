package com.github.senocak.caaf.cache

import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Spring CacheManager implementation that creates and manages FileBackedSpringCache instances.
 */
@Component
@Primary
class FileCacheManager : CacheManager {
    private val log = LoggerFactory.getLogger(javaClass)
    private val caches: ConcurrentHashMap<String, Cache> = ConcurrentHashMap()
    
    override fun getCache(name: String): Cache =
        caches.computeIfAbsent(name) { cacheName ->
            log.info("Creating new file-backed cache: $cacheName")
            // For simplicity, we'll use String keys and Object values
            // In a more sophisticated implementation, you might want to use reflection
            // to determine the actual key and value types from the method signatures
            val fileCache = JsonFileCache<String, Any>(cacheName, String::class.java, Any::class.java)
            FileBackedSpringCache(name = cacheName, fileCache = fileCache)
        }

    override fun getCacheNames(): Collection<String> = caches.keys.toList()
    
    /**
     * Clears all caches.
     */
    fun clearAll() {
        caches.values.forEach { it.clear() }
        log.info("Cleared all caches")
    }
    
    /**
     * Gets the number of caches currently managed.
     */
    fun getCacheCount(): Int = caches.size
}
