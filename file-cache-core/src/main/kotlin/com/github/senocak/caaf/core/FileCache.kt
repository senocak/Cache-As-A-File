package com.github.senocak.caaf.core

/**
 * Minimal typed cache contract backed by an implementation-defined persistence layer.
 */
interface FileCache<K : Any, V : Any> {
    fun get(key: K): V?

    fun put(key: K, value: V)

    fun evict(key: K): V?

    fun clear()

    fun containsKey(key: K): Boolean

    fun size(): Int

    fun keys(): Set<K>
}
