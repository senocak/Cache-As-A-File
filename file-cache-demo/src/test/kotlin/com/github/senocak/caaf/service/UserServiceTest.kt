package com.github.senocak.caaf.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserServiceTest {
    @Test
    fun `starts with sample users and exposes cache stats`() {
        val service = UserService()

        val users = service.getAllUsers()
        val stats = service.getCacheStats()

        assertEquals(3, users.size)
        assertEquals(3, stats["totalUsers"])
        assertEquals(setOf("user1", "user2", "user3"), (stats["usernames"] as List<*>).toSet())
        assertEquals(users.map { it.id }.toSet(), (stats["userIds"] as List<*>).toSet())
    }

    @Test
    fun `creates and finds users by id and username`() {
        val service = UserService()

        val created = service.createUser(
            username = "ada",
            email = "ada@example.com",
            firstName = "Ada",
            lastName = "Lovelace"
        )

        assertEquals(created, service.getUserById(created.id))
        assertEquals(created, service.getUserByUsername("ada"))
        assertTrue(created.id.isNotBlank())
        assertEquals("Ada Lovelace", created.fullName)
    }

    @Test
    fun `updates last login for existing user`() {
        val service = UserService()
        val created = service.createUser(
            username = "grace",
            email = "grace@example.com",
            firstName = "Grace",
            lastName = "Hopper"
        )

        val updated = service.updateLastLogin(created.id)

        assertNotNull(updated)
        assertEquals(created.id, updated.id)
        assertNotNull(updated.lastLoginAt)
        assertEquals(updated, service.getUserById(created.id))
    }

    @Test
    fun `returns null when updating missing user`() {
        val service = UserService()

        assertNull(service.updateLastLogin("missing"))
    }

    @Test
    fun `deletes existing users and reports missing deletes`() {
        val service = UserService()
        val created = service.createUser(
            username = "katherine",
            email = "katherine@example.com",
            firstName = "Katherine",
            lastName = "Johnson"
        )

        assertTrue(service.deleteUser(created.id))
        assertNull(service.getUserById(created.id))
        assertFalse(service.deleteUser(created.id))
    }
}
