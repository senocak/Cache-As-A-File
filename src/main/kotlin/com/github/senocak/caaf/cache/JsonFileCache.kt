package com.github.senocak.caaf.cache

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.senocak.caaf.logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.getValue
import org.slf4j.Logger

/**
 * JSON file-based cache implementation that stores values in memory and persists them to JSON files.
 * 
 * @param K the type of the cache key
 * @param V the type of the cache value
 * @param cacheName the name of the cache (used for the JSON file name)
 */
class JsonFileCache<K, V>(
    private val cacheName: String,
    private val keyType: Class<K>,
    private val valueType: Class<V>
) {
    private val log: Logger by logger()
    private val cache: ConcurrentHashMap<K, V> = ConcurrentHashMap()
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val cacheFile: File
    
    init {
        // Ensure cache directory exists
        val cacheDir = File("cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        cacheFile = File(cacheDir, "$cacheName.json")
        // Load existing cache data at startup
        loadFromFile()
        log.info("Initialized JsonFileCache '$cacheName' with ${cache.size} entries")
    }

    /**
     * Retrieves a value from the cache by its key.
     *
     * @param key the cache key
     * @return the cached value, or null if not found
     */
    fun get(key: K): V? = cache[key]

    /**
     * Stores a value in the cache with the specified key.
     *
     * @param key the cache key
     * @param value the value to cache
     */
    fun put(key: K, value: V) {
        log.info("Putting key '$key' with value '$value' into cache '$cacheName'")
        cache[key] = value
        log.info("Cache '$cacheName' now has ${cache.size} entries")
        saveToFile()
    }

    /**
     * Removes a value from the cache by its key.
     *
     * @param key the cache key
     * @return the removed value, or null if not found
     */
    fun evict(key: K): V? {
        val removed: V? = cache.remove(key)
        if (removed != null) {
            saveToFile()
        }
        return removed
    }

    /**
     * Clears all entries from the cache.
     */
    fun clear() {
        cache.clear()
        saveToFile()
    }

    /**
     * Checks if the cache contains a value for the specified key.
     *
     * @param key the cache key
     * @return true if the key exists in the cache, false otherwise
     */
    fun containsKey(key: K): Boolean = cache.containsKey(key)

    /**
     * Returns the number of entries in the cache.
     *
     * @return the cache size
     */
    fun size(): Int = cache.size

    /**
     * Returns all keys in the cache.
     *
     * @return a set of all cache keys
     */
    fun keys(): Set<K> = cache.keys.toSet()
    
    /**
     * Loads cache data from the JSON file.
     */
    private fun loadFromFile() {
        if (!cacheFile.exists()) {
            log.debug("Cache file ${cacheFile.absolutePath} does not exist, starting with empty cache")
            return
        }
        try {
            val jsonContent: String = cacheFile.readText()
            if (jsonContent.isNotBlank()) {
                val typeRef: TypeReference<Map<K, V>> = object : TypeReference<Map<K, V>>() {}
                val loadedData: Map<K, V> = objectMapper.readValue(jsonContent, typeRef)
                cache.putAll(loadedData)
                log.info("Loaded ${loadedData.size} entries from cache file ${cacheFile.absolutePath}")
            }
        } catch (e: Exception) {
            log.error("Failed to load cache from file ${cacheFile.absolutePath}", e)
            // Continue with empty cache if loading fails
        }
    }
    
    /**
     * Saves cache data to the JSON file.
     */
    private fun saveToFile() {
        try {
            log.info("Attempting to save ${cache.size} entries to cache file ${cacheFile.absolutePath}")
            val jsonContent: String = objectMapper.writeValueAsString(cache)
            log.info("JSON content: $jsonContent")
            cacheFile.writeText(text = jsonContent)
            log.info("Successfully saved ${cache.size} entries to cache file ${cacheFile.absolutePath}")
        } catch (e: Exception) {
            log.error("Failed to save cache to file ${cacheFile.absolutePath}", e)
        }
    }
}
