package com.github.senocak.caaf.controller

import com.github.senocak.caaf.model.CreateUserRequest
import com.github.senocak.caaf.model.User
import com.github.senocak.caaf.service.UserService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for demonstrating file-backed caching.
 */
@RestController
@RequestMapping(value = ["/api/users"])
class UserController(private val userService: UserService) {
    @GetMapping
    fun getAllUsers(): List<User> =
        userService.getAllUsers()

    @GetMapping(value = ["/{userId}"])
    fun getUserById(@PathVariable userId: String): User =
        userService.getUserById(userId = userId) ?: throw NoSuchElementException("User not found with ID: $userId")

    @GetMapping(value = ["/username/{username}"])
    fun getUserByUsername(@PathVariable username: String): User =
        userService.getUserByUsername(username = username) ?: throw NoSuchElementException("User not found with username: $username")

    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): User =
        userService.createUser(
            username = request.username,
            email = request.email,
            firstName = request.firstName,
            lastName = request.lastName
        )

    @PutMapping(value = ["/{userId}/login"])
    fun updateLastLogin(@PathVariable userId: String): User =
        userService.updateLastLogin(userId = userId) ?: throw NoSuchElementException("User not found with ID: $userId")

    @DeleteMapping(value = ["/{userId}"])
    fun deleteUser(@PathVariable userId: String): Boolean =
        userService.deleteUser(userId = userId)

    @DeleteMapping(value = ["/cache"])
    fun clearAllCaches() {
        userService.clearAllCaches()
    }

    @GetMapping(value = ["/cache/stats"])
    fun getCacheStats(): Map<String, Any> =
        userService.getCacheStats()
}
