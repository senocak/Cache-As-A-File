package com.github.senocak.caaf

import com.github.senocak.caaf.core.FileCacheManager
import java.nio.file.Path
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class CacheConfig {
    @Bean
    @Primary
    fun cacheManager(properties: FileCacheProperties): CacheManager =
        FileCacheManager(
            cacheDirectory = Path.of(properties.directory),
            clearInterval = properties.clearInterval
        )
}

@ConfigurationProperties(prefix = "caaf.cache")
data class FileCacheProperties(
    var directory: String = "cache",
    var clearInterval: Duration? = null
)
