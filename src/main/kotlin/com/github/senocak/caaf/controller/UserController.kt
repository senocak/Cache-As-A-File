package com.github.senocak.caaf.controller

import com.github.senocak.caaf.model.User
import com.github.senocak.caaf.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for demonstrating the file-based caching functionality.
 */
@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {
    
    /**
     * Get a user by ID (demonstrates @Cacheable).
     * First call will load from storage, subsequent calls will use cache.
     */
    @GetMapping("/{userId}")
    fun getUserById(@PathVariable userId: String): ResponseEntity<User?> {
        val user = userService.getUserById(userId)
        return if (user != null) {
            ResponseEntity.ok(user)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    /**
     * Get a user by username (demonstrates @Cacheable with different cache).
     */
    @GetMapping("/username/{username}")
    fun getUserByUsername(@PathVariable username: String): ResponseEntity<User?> {
        val user = userService.getUserByUsername(username)
        return if (user != null) {
            ResponseEntity.ok(user)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    /**
     * Create a new user (demonstrates @CachePut).
     */
    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<User> {
        val user = userService.createUser(
            username = request.username,
            email = request.email,
            firstName = request.firstName,
            lastName = request.lastName
        )
        return ResponseEntity.ok(user)
    }
    
    /**
     * Update user's last login (demonstrates @CachePut).
     */
    @PutMapping("/{userId}/login")
    fun updateLastLogin(@PathVariable userId: String): ResponseEntity<User?> {
        val user = userService.updateLastLogin(userId)
        return if (user != null) {
            ResponseEntity.ok(user)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    /**
     * Delete a user (demonstrates @CacheEvict).
     */
    @DeleteMapping("/{userId}")
    fun deleteUser(@PathVariable userId: String): ResponseEntity<Map<String, Boolean>> {
        val deleted = userService.deleteUser(userId)
        return ResponseEntity.ok(mapOf("deleted" to deleted))
    }
    
    /**
     * Clear all caches (demonstrates @CacheEvict with allEntries).
     */
    @DeleteMapping("/cache")
    fun clearAllCaches(): ResponseEntity<Map<String, String>> {
        userService.clearAllCaches()
        return ResponseEntity.ok(mapOf("message" to "All caches cleared"))
    }
    
    /**
     * Get all users (not cached).
     */
    @GetMapping
    fun getAllUsers(): ResponseEntity<List<User>> {
        val users = userService.getAllUsers()
        return ResponseEntity.ok(users)
    }
    
    /**
     * Get cache statistics.
     */
    @GetMapping("/cache/stats")
    fun getCacheStats(): ResponseEntity<Map<String, Any>> {
        val stats = userService.getCacheStats()
        return ResponseEntity.ok(stats)
    }
}

/**
 * Request DTO for creating a user.
 */
data class CreateUserRequest(
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String
)
