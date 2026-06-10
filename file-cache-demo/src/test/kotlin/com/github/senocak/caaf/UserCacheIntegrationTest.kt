package com.github.senocak.caaf

import com.github.senocak.caaf.core.FileCacheManager
import com.github.senocak.caaf.model.User
import com.github.senocak.caaf.service.UserService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
class UserCacheIntegrationTest {
    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var cacheManager: CacheManager

    @AfterTest
    fun clearCaches() {
        userService.clearAllCaches()
    }

    @Test
    fun `cache put stores created users in the file backed users cache`() {
        val user = userService.createUser(
            username = "file-cache-ada",
            email = "ada@example.com",
            firstName = "Ada",
            lastName = "Lovelace"
        )

        val usersCache = requireNotNull(cacheManager.getCache("users"))

        assertEquals(user, usersCache.get(user.id)?.get())
        assertTrue(Files.exists(cacheDirectory.resolve("users.json")))
        assertEquals(user, reloadedUsersCache().get(user.id)?.get())
    }

    @Test
    fun `cacheable username lookup writes to a dedicated username cache file`() {
        val user = userService.createUser(
            username = "file-cache-grace",
            email = "grace@example.com",
            firstName = "Grace",
            lastName = "Hopper"
        )

        val found = userService.getUserByUsername("file-cache-grace")
        val usernameCache = requireNotNull(cacheManager.getCache("usersByUsername"))

        assertEquals(user, found)
        assertEquals(user, usernameCache.get("file-cache-grace")?.get())
        assertTrue(Files.exists(cacheDirectory.resolve("usersByUsername.json")))
    }

    @Test
    fun `cache put updates existing cached users after login`() {
        val user = userService.createUser(
            username = "file-cache-katherine",
            email = "katherine@example.com",
            firstName = "Katherine",
            lastName = "Johnson"
        )

        val updated = userService.updateLastLogin(user.id)
        val usersCache = requireNotNull(cacheManager.getCache("users"))

        assertNotNull(updated)
        assertEquals(user.id, updated.id)
        assertNotNull(updated.lastLoginAt)
        assertEquals(updated, usersCache.get(user.id)?.get())
        assertEquals(updated, reloadedUsersCache().get(user.id)?.get())
    }

    @Test
    fun `cache evict removes deleted users from the users cache`() {
        val user = userService.createUser(
            username = "file-cache-margaret",
            email = "margaret@example.com",
            firstName = "Margaret",
            lastName = "Hamilton"
        )
        val usersCache = requireNotNull(cacheManager.getCache("users"))

        assertEquals(user, usersCache.get(user.id)?.get())
        assertTrue(userService.deleteUser(user.id))

        assertEquals(null, usersCache.get(user.id))
        assertEquals(null, reloadedUsersCache().get(user.id))
    }

    private fun reloadedUsersCache() =
        FileCacheManager(cacheDirectory = cacheDirectory).getCache("users")

    companion object {
        private val cacheDirectory: Path = Files.createTempDirectory("caaf-demo-cache-integration")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("caaf.cache.directory") { cacheDirectory.toString() }
        }
    }
}
