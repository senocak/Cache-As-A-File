package com.github.senocak.caaf.service

import com.github.senocak.caaf.logger
import com.github.senocak.caaf.model.User
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.Logger
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

/**
 * Example service that demonstrates file-backed caching with Spring annotations.
 */
@Service
class UserService {
    private val log: Logger by logger()
    private val userStorage = ConcurrentHashMap<String, User>()

    init {
        createUser(username = "user1", email = "john.doe@example.com", firstName = "John", lastName = "Doe")
        createUser(username = "user2", email = "jane.smith@example.com", firstName = "Jane", lastName = "Smith")
        createUser(username = "user3", email = "bob.wilson@example.com", firstName = "Bob", lastName = "Wilson")
    }

    @Cacheable(value = ["users"], key = "#userId")
    fun getUserById(userId: String): User? {
        log.info("Loading user from storage: $userId")
        Thread.sleep(100)
        return userStorage[userId]
    }

    @Cacheable(value = ["usersByUsername"], key = "#username")
    fun getUserByUsername(username: String): User? {
        log.info("Loading user by username from storage: $username")
        Thread.sleep(100)
        return userStorage.values.find { it.username == username }
    }

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
        userStorage[user.id] = user
        return user
    }

    @CachePut(value = ["users"], key = "#userId")
    fun updateLastLogin(userId: String): User? {
        log.info("Updating last login for user: {}", userId)
        val user: User = userStorage[userId] ?: return null
        val updatedUser: User = user.copy(lastLoginAt = LocalDateTime.now())
        userStorage[userId] = updatedUser
        return updatedUser
    }

    @CacheEvict(value = ["users"], key = "#userId")
    fun deleteUser(userId: String): Boolean {
        log.info("Deleting user: $userId")
        return userStorage.remove(userId) != null
    }

    fun getAllUsers(): List<User> =
        userStorage.values.toList()

    fun getCacheStats(): Map<String, Any> =
        mapOf(
            "totalUsers" to userStorage.size,
            "userIds" to userStorage.keys.toList(),
            "usernames" to userStorage.values.map { it.username }
        )
}
