package com.github.senocak.caaf.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.Callable
import org.springframework.cache.Cache
import org.springframework.cache.support.SimpleValueWrapper

/**
 * Spring Cache adapter backed by a typed file cache.
 */
@Suppress(names = ["UNCHECKED_CAST"])
class FileBackedSpringCache(
    private val name: String,
    private val fileCache: FileCache<String, SpringCacheEntry>,
    private val objectMapper: ObjectMapper,
    private val keySerializer: (Any) -> String = Any::toString
) : Cache {
    override fun getName(): String =
        name

    override fun getNativeCache(): Any =
        fileCache

    override fun get(key: Any): Cache.ValueWrapper? =
        fileCache.get(key.toCacheKey())?.toValue()?.let(block = ::SimpleValueWrapper)

    override fun <T : Any?> get(key: Any, type: Class<T>?): T? {
        val value: Any = fileCache.get(key.toCacheKey())?.toValue() ?: return null
        if (type == null) {
            return value as T
        }
        return if (type.isInstance(value)) type.cast(value) else null
    }

    override fun <T : Any?> get(key: Any, valueLoader: Callable<T>): T {
        val cacheKey: String = key.toCacheKey()
        val existingValue: Any? = fileCache.get(cacheKey)?.toValue()
        if (existingValue != null) {
            return existingValue as T
        }

        return try {
            val loadedValue: T = valueLoader.call()
            put(key = key, value = loadedValue)
            loadedValue
        } catch (ex: Exception) {
            throw Cache.ValueRetrievalException(key, valueLoader, ex)
        }
    }

    override fun put(key: Any, value: Any?) {
        if (value == null) {
            return
        }
        fileCache.put(
            key = key.toCacheKey(),
            value = SpringCacheEntry(
                type = value.javaClass.name,
                value = objectMapper.valueToTree(value)
            )
        )
    }

    override fun evict(key: Any) {
        fileCache.evict(key.toCacheKey())
    }

    override fun clear() {
        fileCache.clear()
    }

    override fun invalidate(): Boolean {
        clear()
        return true
    }

    private fun Any.toCacheKey(): String =
        keySerializer(this)

    private fun SpringCacheEntry.toValue(): Any {
        val valueClass: Class<*> = Class.forName(type)
        return objectMapper.treeToValue(value, valueClass)
    }
}

data class SpringCacheEntry(
    val type: String,
    val value: JsonNode
)
