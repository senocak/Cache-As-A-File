package com.github.senocak.caaf.core

import java.time.Instant

sealed interface CacheEvent<K : Any, V : Any> {
    val cacheName: String
    val key: K
    val value: V
    val occurredAt: Instant
}

data class CacheInsertedEvent<K : Any, V : Any>(
    override val cacheName: String,
    override val key: K,
    override val value: V,
    val previousValue: V? = null,
    override val occurredAt: Instant = Instant.now()
) : CacheEvent<K, V>

data class CacheEvictedEvent<K : Any, V : Any>(
    override val cacheName: String,
    override val key: K,
    override val value: V,
    override val occurredAt: Instant = Instant.now()
) : CacheEvent<K, V>
