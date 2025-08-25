package com.github.senocak.caaf.cache

import org.springframework.cache.Cache
import org.springframework.cache.support.SimpleValueWrapper
import java.util.concurrent.Callable

/**
 * Spring Cache implementation that delegates to a JsonFileCache.
 * 
 * @param name the cache name
 * @param fileCache the underlying file cache implementation
 */
class FileBackedSpringCache<K, V>(
    private val name: String,
    private val fileCache: JsonFileCache<K, V>
) : Cache {
    override fun getName(): String = name
    
    override fun getNativeCache(): Any = fileCache
    
    override fun get(key: Any): Cache.ValueWrapper? {
        val value = fileCache.get(key = key as K)
        return if (value != null) SimpleValueWrapper(value) else null
    }
    
    override fun <T : Any?> get(key: Any, type: Class<T>?): T? {
        val value = fileCache.get(key = key as K)
        return if (type?.isInstance(value) == true) value as T else null
    }
    
    override fun <T : Any?> get(key: Any, valueLoader: Callable<T>): T {
        val k = key as K
        // Check if value exists in cache
        val existingValue = fileCache.get(key = k)
        if (existingValue != null) {
            return existingValue as T
        }
        // Load value using the value loader
        val newValue = valueLoader.call()
        fileCache.put(key = k, value = newValue as V)
        return newValue
    }
    
    override fun put(key: Any, value: Any?) {
        if (value != null) {
            fileCache.put(key = key as K, value = value as V)
        }
    }
    
    override fun evict(key: Any) {
        fileCache.evict(key = key as K)
    }
    
    override fun clear() {
        fileCache.clear()
    }
    
    override fun invalidate(): Boolean {
        clear()
        return true
    }
}
