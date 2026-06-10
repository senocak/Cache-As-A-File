package com.github.senocak.caaf

import com.github.senocak.caaf.core.FileCacheManager
import java.nio.file.Path
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@EnableConfigurationProperties(FileCacheProperties::class)
class CacheConfig {
    @Bean
    @Primary
    fun cacheManager(properties: FileCacheProperties): CacheManager =
        FileCacheManager(cacheDirectory = Path.of(properties.directory))
}

@ConfigurationProperties(prefix = "caaf.cache")
data class FileCacheProperties(
    var directory: String = "cache"
)
