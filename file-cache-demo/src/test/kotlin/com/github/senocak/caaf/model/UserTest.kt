package com.github.senocak.caaf.model

import kotlin.test.Test
import kotlin.test.assertEquals

class UserTest {
    @Test
    fun `full name joins first and last names`() {
        val user = User(
            id = "1",
            username = "ada",
            email = "ada@example.com",
            firstName = "Ada",
            lastName = "Lovelace"
        )

        assertEquals("Ada Lovelace", user.fullName)
    }
}
