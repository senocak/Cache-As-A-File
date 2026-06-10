package com.github.senocak.caaf.core

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.context.ApplicationEventPublisher

/**
 * Spring CacheManager that creates one persistent JSON file per Spring cache name.
 */
class FileCacheManager(
    private val cacheDirectory: Path = Path.of("cache"),
    private val objectMapper: ObjectMapper = JsonFileCache.defaultObjectMapper(),
    private val keySerializer: (Any) -> String = Any::toString,
    private val clearInterval: Duration? = null,
    private val applicationEventPublisher: ApplicationEventPublisher? = null
) : CacheManager, AutoCloseable {
    private val caches: ConcurrentHashMap<String, Cache> = ConcurrentHashMap()
    private val clearExecutor: ScheduledExecutorService? = schedulePeriodicClear(clearInterval = clearInterval)

    override fun getCache(name: String): Cache =
        caches.computeIfAbsent(name) { cacheName: String ->
            val fileCache: JsonFileCache<String, SpringCacheEntry> = JsonFileCache(
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
                keySerializer = keySerializer,
                applicationEventPublisher = applicationEventPublisher
            )
        }

    override fun getCacheNames(): Collection<String> =
        caches.keys.toList()

    fun clearAll() {
        caches.values.forEach(action = Cache::clear)
    }

    fun getCacheCount(): Int =
        caches.size

    fun isPeriodicClearEnabled(): Boolean =
        clearExecutor != null

    override fun close() {
        clearExecutor?.shutdownNow()
    }

    private fun schedulePeriodicClear(clearInterval: Duration?): ScheduledExecutorService? {
        if (clearInterval == null || clearInterval.isZero) {
            return null
        }
        require(value = !clearInterval.isNegative) {
            "Clear interval must not be negative"
        }
        val delayMillis: Long = clearInterval.toMillis().coerceAtLeast(minimumValue = 1)
        return Executors.newSingleThreadScheduledExecutor { runnable: Runnable ->
            Thread(runnable, "file-cache-clearer").apply { isDaemon = true }
        }.also { executor: ScheduledExecutorService ->
            executor.scheduleWithFixedDelay(
                { clearAll() },
                delayMillis,
                delayMillis,
                TimeUnit.MILLISECONDS
            )
        }
    }
}
