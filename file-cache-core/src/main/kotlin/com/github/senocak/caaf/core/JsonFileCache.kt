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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * JSON file-backed cache that keeps entries in memory and persists each mutation to disk.
 *
 * Each cache is stored as one JSON object at `{cacheDirectory}/{cacheName}.json`.
 */
class JsonFileCache<K : Any, V : Any>(
    cacheName: String,
    private val keyType: Class<K>,
    private val valueType: Class<V>,
    private val cacheDirectory: Path = Path.of("cache"),
    private val objectMapper: ObjectMapper = defaultObjectMapper()
) : FileCache<K, V> {
    private val cache: ConcurrentHashMap<K, V> = ConcurrentHashMap()
    private val ioLock: ReentrantLock = ReentrantLock()
    private val cacheFile: Path = cacheDirectory.resolve("${sanitizeCacheName(cacheName)}.json")
    private val mapType: JavaType = objectMapper.typeFactory.constructMapType(
        LinkedHashMap::class.java,
        keyType,
        valueType
    )

    init {
        Files.createDirectories(cacheDirectory)
        loadFromFile()
    }

    override fun get(key: K): V? =
        cache[key]

    override fun put(key: K, value: V) {
        cache[key] = value
        saveToFile()
    }

    override fun evict(key: K): V? {
        val removed: V? = cache.remove(key)
        if (removed != null) {
            saveToFile()
        }
        return removed
    }

    override fun clear() {
        cache.clear()
        saveToFile()
    }

    override fun containsKey(key: K): Boolean =
        cache.containsKey(key)

    override fun size(): Int =
        cache.size

    override fun keys(): Set<K> =
        cache.keys.toSet()

    private fun loadFromFile() {
        ioLock.withLock {
            if (!Files.exists(cacheFile) || Files.size(cacheFile) == 0L) {
                return
            }

            Files.newInputStream(cacheFile).use { input ->
                val loadedData: Map<K, V> = objectMapper.readValue(input, mapType)
                cache.clear()
                cache.putAll(loadedData)
            }
        }
    }

    private fun saveToFile() {
        ioLock.withLock {
            Files.createDirectories(cacheDirectory)
            val snapshot: Map<K, V> = LinkedHashMap(cache)
            val tempFile: Path = Files.createTempFile(cacheDirectory, cacheFile.fileName.toString(), ".tmp")

            try {
                Files.newOutputStream(tempFile).use { output ->
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(output, snapshot)
                }
                moveTempFile(tempFile)
            } catch (ex: Exception) {
                Files.deleteIfExists(tempFile)
                throw ex
            }
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

    companion object {
        fun defaultObjectMapper(): ObjectMapper =
            ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
                .registerModule(JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        private fun sanitizeCacheName(cacheName: String): String {
            require(cacheName.isNotBlank()) { "Cache name must not be blank" }
            require(cacheName.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }) {
                "Cache name may only contain letters, digits, dots, dashes, and underscores"
            }
            return cacheName
        }
    }
}
