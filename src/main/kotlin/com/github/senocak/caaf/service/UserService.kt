package com.github.senocak.caaf.service

import com.github.senocak.caaf.model.User
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Example service that demonstrates the file-based caching functionality.
 * Uses @Cacheable, @CachePut, and @CacheEvict annotations to show how the custom cache works.
 */
@Service
class UserService {
    private val log = LoggerFactory.getLogger(javaClass)
    
    // In-memory storage for demonstration (in a real app, this would be a database)
    private val userStorage = ConcurrentHashMap<String, User>()
    
    init {
        // Initialize with some sample users
        createUser(username = "user1", email = "john.doe@example.com", firstName = "John", lastName = "Doe")
        createUser(username = "user2", email = "jane.smith@example.com", firstName = "Jane", lastName = "Smith")
        createUser(username = "user3", email = "bob.wilson@example.com", firstName = "Bob", lastName = "Wilson")
    }
    
    /**
     * Retrieves a user by ID with caching.
     * If the user is not in the cache, it will be loaded and cached.
     * Subsequent calls with the same ID will return the cached value.
     * 
     * @param userId the user ID
     * @return the user, or null if not found
     */
    @Cacheable(value = ["users"], key = "#userId")
    fun getUserById(userId: String): User? {
        log.info("Loading user from storage: $userId")
        // Simulate some processing time
        Thread.sleep(100)
        return userStorage[userId]
    }
    
    /**
     * Retrieves a user by username with caching.
     * Uses a different cache name to demonstrate multiple caches.
     * 
     * @param username the username
     * @return the user, or null if not found
     */
    @Cacheable(value = ["usersByUsername"], key = "#username")
    fun getUserByUsername(username: String): User? {
        log.info("Loading user by username from storage: $username")
        // Simulate some processing time
        Thread.sleep(100)
        return userStorage.values.find { it.username == username }
    }
    
    /**
     * Creates or updates a user and caches the result.
     * The @CachePut annotation ensures the cache is updated with the new value.
     * 
     * @param username the username
     * @param email the email
     * @param firstName the first name
     * @param lastName the last name
     * @return the created or updated user
     */
    @CachePut(value = ["users"], key = "#result.id")
    fun createUser(username: String, email: String, firstName: String, lastName: String): User {
        log.info("Creating new user: $username")
        val user = User(
            id = UUID.randomUUID().toString(),
            username = username,
            email = email,
            firstName = firstName,
            lastName = lastName
        )
        userStorage.put(user.id, user)
        return user
    }
    
    /**
     * Updates a user's last login time and caches the result.
     * 
     * @param userId the user ID
     * @return the updated user, or null if not found
     */
    @CachePut(value = ["users"], key = "#userId")
    fun updateLastLogin(userId: String): User? {
        log.info("Updating last login for user: $userId")
        val user = userStorage[userId] ?: return null
        val updatedUser = user.copy(lastLoginAt = LocalDateTime.now())
        userStorage[userId] = updatedUser
        return updatedUser
    }
    
    /**
     * Deletes a user and removes it from the cache.
     * The @CacheEvict annotation removes the entry from the cache.
     * 
     * @param userId the user ID
     * @return true if the user was deleted, false if not found
     */
    @CacheEvict(value = ["users"], key = "#userId")
    fun deleteUser(userId: String): Boolean {
        log.info("Deleting user: $userId")
        val user = userStorage.remove(userId)
        return user != null
    }
    
    /**
     * Clears all user caches.
     * This is useful for maintenance or when you want to refresh all cached data.
     */
    @CacheEvict(value = ["users", "usersByUsername"], allEntries = true)
    @Scheduled(fixedDelay = 10 * 1_000, initialDelay = 500, timeUnit = TimeUnit.MILLISECONDS)
    fun clearAllCaches() {
        log.info("Clearing all user caches")
    }
    
    /**
     * Gets all users (not cached for demonstration purposes).
     * 
     * @return list of all users
     */
    fun getAllUsers(): List<User> =
        userStorage.values.toList()

    /**
     * Gets cache statistics for demonstration.
     * 
     * @return a map with cache information
     */
    fun getCacheStats(): Map<String, Any> =
        mapOf(
            "totalUsers" to userStorage.size,
            "userIds" to userStorage.keys.toList(),
            "usernames" to userStorage.values.map { it.username }
        )
}
