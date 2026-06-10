package com.github.senocak.caaf.core

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.springframework.context.ApplicationEventPublisher

/**
 * JSON file cache that reads from and writes to disk for every operation.
 *
 * Each cache is stored as one JSON object at `{cacheDirectory}/{cacheName}.json`.
 */
class JsonFileCache<K : Any, V : Any>(
    cacheName: String,
    private val keyType: Class<K>,
    private val valueType: Class<V>,
    private val cacheDirectory: Path = Path.of("cache"),
    private val objectMapper: ObjectMapper = defaultObjectMapper(),
    private val applicationEventPublisher: ApplicationEventPublisher? = null
) : FileCache<K, V> {
    private val cacheName: String = sanitizeCacheName(cacheName = cacheName)
    private val ioLock: ReentrantLock = ReentrantLock()
    private val cacheFile: Path = cacheDirectory.resolve("${this.cacheName}.json")
    private val mapType: JavaType = objectMapper.typeFactory.constructMapType(
        LinkedHashMap::class.java,
        keyType,
        valueType
    )

    init {
        Files.createDirectories(cacheDirectory)
    }

    override fun get(key: K): V? =
        ioLock.withLock {
            readFromFile()[key]
        }

    override fun put(key: K, value: V) {
        val event: CacheInsertedEvent<K, V> = ioLock.withLock {
            val snapshot: LinkedHashMap<K, V> = readFromFile()
            val previousValue: V? = snapshot[key]
            snapshot[key] = value
            saveToFile(snapshot = snapshot)
            CacheInsertedEvent(
                cacheName = cacheName,
                key = key,
                value = value,
                previousValue = previousValue
            )
        }
        fireEvent(event = event)
    }

    override fun evict(key: K): V? {
        val event: CacheEvictedEvent<K, V>? = ioLock.withLock {
            val snapshot: LinkedHashMap<K, V> = readFromFile()
            val removed: V? = snapshot.remove(key)
            if (removed != null) {
                saveToFile(snapshot = snapshot)
                CacheEvictedEvent(
                    cacheName = cacheName,
                    key = key,
                    value = removed
                )
            } else {
                null
            }
        }
        event?.let { fireEvent(event = it) }
        return event?.value
    }

    override fun clear() {
        val events: List<CacheEvictedEvent<K, V>> = ioLock.withLock {
            val snapshot: LinkedHashMap<K, V> = readFromFile()
            saveToFile(snapshot = emptyMap())
            snapshot.map { (key: K, value: V) ->
                CacheEvictedEvent(
                    cacheName = cacheName,
                    key = key,
                    value = value
                )
            }
        }
        events.forEach { event: CacheEvictedEvent<K, V> ->
            fireEvent(event = event)
        }
    }

    override fun containsKey(key: K): Boolean =
        ioLock.withLock {
            readFromFile().containsKey(key)
        }

    override fun size(): Int =
        ioLock.withLock {
            readFromFile().size
        }

    override fun keys(): Set<K> =
        ioLock.withLock {
            readFromFile().keys.toSet()
        }

    private fun readFromFile(): LinkedHashMap<K, V> {
        if (!Files.exists(cacheFile) || Files.size(cacheFile) == 0L) {
            return LinkedHashMap()
        }

        Files.newInputStream(cacheFile).use { input ->
            val loadedData: Map<K, V> = objectMapper.readValue(input, mapType)
            return LinkedHashMap(loadedData)
        }
    }

    private fun saveToFile(snapshot: Map<K, V>) {
        Files.createDirectories(cacheDirectory)
        val tempFile: Path = Files.createTempFile(cacheDirectory, cacheFile.fileName.toString(), ".tmp")

        try {
            Files.newOutputStream(tempFile).use { output ->
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(output, snapshot)
            }
            moveTempFile(tempFile = tempFile)
        } catch (ex: Exception) {
            Files.deleteIfExists(tempFile)
            throw ex
        }
    }

    private fun moveTempFile(tempFile: Path) {
        try {
            Files.move(
                tempFile,
                cacheFile,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun fireEvent(event: CacheEvent<K, V>) {
        applicationEventPublisher?.publishEvent(event)
    }

    companion object {
        fun defaultObjectMapper(): ObjectMapper =
            ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
                .registerModule(JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        private fun sanitizeCacheName(cacheName: String): String {
            require(value = cacheName.isNotBlank()) { "Cache name must not be blank" }
            require(value = cacheName.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }) {
                "Cache name may only contain letters, digits, dots, dashes, and underscores"
            }
            return cacheName
        }
    }
}
