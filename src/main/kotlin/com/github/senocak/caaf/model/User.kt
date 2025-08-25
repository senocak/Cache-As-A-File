package com.github.senocak.caaf.model

import java.time.LocalDateTime

/**
 * User data model for demonstrating caching functionality.
 */
data class User(
    val id: String,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastLoginAt: LocalDateTime? = null
) {
    val fullName: String
        get() = "$firstName $lastName"
}
