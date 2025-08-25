package com.github.senocak.caaf.config

import com.github.senocak.caaf.cache.FileCacheManager
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Configuration class for the file-based caching system.
 * Enables Spring's caching abstraction and registers the FileCacheManager as the default cache manager.
 */
@Configuration
@EnableCaching
class CacheConfig {
    
    /**
     * Creates and registers the FileCacheManager as the primary cache manager.
     * This makes it the default cache manager for the application.
     * 
     * @return the FileCacheManager instance
     */
    @Bean
    @Primary
    fun cacheManager(): CacheManager {
        return FileCacheManager()
    }
}
