package com.github.senocak.caaf.controller

import com.github.senocak.caaf.model.CreateUserRequest
import com.github.senocak.caaf.model.User
import com.github.senocak.caaf.service.UserService
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody

/**
 * REST controller for demonstrating the file-based caching functionality.
 */
@RestController
@RequestMapping(value = ["/api/users"])
class UserController(private val userService: UserService) {

    /**
     * Get all users (not cached).
     */
    @GetMapping
    fun getAllUsers(): List<User> =
        userService.getAllUsers()
    
    /**
     * Get a user by ID (demonstrates @Cacheable).
     * First call will load from storage, subsequent calls will use cache.
     */
    @GetMapping(value = ["/{userId}"])
    fun getUserById(@PathVariable userId: String): User =
        userService.getUserById(userId = userId) ?: throw NoSuchElementException("User not found with ID: $userId")
    
    /**
     * Get a user by username (demonstrates @Cacheable with different cache).
     */
    @GetMapping(value = ["/username/{username}"])
    fun getUserByUsername(@PathVariable username: String): User =
        userService.getUserByUsername(username = username) ?: throw NoSuchElementException("User not found with username: $username")
    
    /**
     * Create a new user (demonstrates @CachePut).
     */
    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): User =
        userService.createUser(
            username = request.username,
            email = request.email,
            firstName = request.firstName,
            lastName = request.lastName
        )
    
    /**
     * Update user's last login (demonstrates @CachePut).
     */
    @PutMapping(value = ["/{userId}/login"])
    fun updateLastLogin(@PathVariable userId: String): User =
        userService.updateLastLogin(userId = userId) ?: throw NoSuchElementException("User not found with ID: $userId")
    
    /**
     * Delete a user (demonstrates @CacheEvict).
     */
    @DeleteMapping(value = ["/{userId}"])
    fun deleteUser(@PathVariable userId: String): Boolean =
        userService.deleteUser(userId = userId)
    
    /**
     * Clear all caches (demonstrates @CacheEvict with allEntries).
     */
    @DeleteMapping(value = ["/cache"])
    fun clearAllCaches(): Unit =
        userService.clearAllCaches()

    /**
     * Get cache statistics.
     */
    @GetMapping(value = ["/cache/stats"])
    fun getCacheStats(): Map<String, Any> =
        userService.getCacheStats()
}
