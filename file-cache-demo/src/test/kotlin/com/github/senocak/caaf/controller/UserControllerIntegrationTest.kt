package com.github.senocak.caaf.controller

import com.github.senocak.caaf.service.UserService
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.util.AopTestUtils
import org.springframework.test.util.ReflectionTestUtils

@SpringBootTest(
    properties = [
        "caaf.cache.directory=\${java.io.tmpdir}/caaf-demo-controller-test-\${random.uuid}"
    ]
)
@AutoConfigureMockMvc
class UserControllerIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userService: UserService

    @Test
    fun `user endpoints support create read update delete and stats workflows`() {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(3))

        val createResult: MvcResult = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "ada",
                      "email": "ada@example.com",
                      "firstName": "Ada",
                      "lastName": "Lovelace"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andExpect(jsonPath("$.username").value("ada"))
            .andExpect(jsonPath("$.email").value("ada@example.com"))
            .andExpect(jsonPath("$.firstName").value("Ada"))
            .andExpect(jsonPath("$.lastName").value("Lovelace"))
            .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
            .andReturn()

        val userId: String = createResult.response.contentAsString.jsonString("id")
        assertTrue(actual = userId.isNotBlank())

        mockMvc.perform(get("/api/users/{userId}", userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.username").value("ada"))

        mockMvc.perform(get("/api/users/username/{username}", "ada"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.email").value("ada@example.com"))

        mockMvc.perform(put("/api/users/{userId}/login", userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.lastLoginAt").exists())

        mockMvc.perform(get("/api/users/cache/stats"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalUsers").value(4))
            .andExpect(jsonPath("$.userIds").isArray)
            .andExpect(jsonPath("$.usernames").isArray)

        mockMvc.perform(delete("/api/users/{userId}", userId))
            .andExpect(status().isOk)
            .andExpect(content().string("true"))

        mockMvc.perform(delete("/api/users/{userId}", userId))
            .andExpect(status().isOk)
            .andExpect(content().string("false"))
    }

    @Test
    fun `cache clear endpoint completes successfully`() {
        mockMvc.perform(delete("/api/users/cache"))
            .andExpect(status().isOk)
            .andExpect(content().string(""))
    }

    @Test
    fun `get user by id returns cached data when backing storage is missing`() {
        val createResult: MvcResult = createUser(
            username = "cached-id",
            email = "cached-id@example.com",
            firstName = "Cached",
            lastName = "Id"
        )
        val userId: String = createResult.response.contentAsString.jsonString("id")

        removeUserFromBackingStorage(userId = userId)

        mockMvc.perform(get("/api/users/{userId}", userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.username").value("cached-id"))
            .andExpect(jsonPath("$.email").value("cached-id@example.com"))
            .andExpect(jsonPath("$.fullName").value("Cached Id"))
    }

    @Test
    fun `get user by username returns cached data after the first lookup`() {
        val createResult: MvcResult = createUser(
            username = "cached-username",
            email = "cached-username@example.com",
            firstName = "Cached",
            lastName = "Username"
        )
        val userId: String = createResult.response.contentAsString.jsonString(fieldName = "id")

        mockMvc.perform(get("/api/users/username/{username}", "cached-username"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))

        removeUserFromBackingStorage(userId = userId)

        mockMvc.perform(get("/api/users/username/{username}", "cached-username"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.username").value("cached-username"))
            .andExpect(jsonPath("$.email").value("cached-username@example.com"))
            .andExpect(jsonPath("$.fullName").value("Cached Username"))
    }

    private fun createUser(username: String, email: String, firstName: String, lastName: String): MvcResult =
        mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "$username",
                      "email": "$email",
                      "firstName": "$firstName",
                      "lastName": "$lastName"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.firstName").value(firstName))
            .andExpect(jsonPath("$.lastName").value(lastName))
            .andExpect(jsonPath("$.fullName").value("$firstName $lastName"))
            .andReturn()

    @Suppress(names = ["UNCHECKED_CAST"])
    private fun removeUserFromBackingStorage(userId: String) {
        val target: UserService = AopTestUtils.getTargetObject(userService)
        val storage: ConcurrentHashMap<String, *> = ReflectionTestUtils.getField(target, "userStorage") as ConcurrentHashMap<String, *>
        storage.remove(key = userId)
    }

    private fun String.jsonString(fieldName: String): String =
        Regex(pattern = "\"${Regex.escape(literal = fieldName)}\"\\s*:\\s*\"([^\"]+)\"")
            .find(this)
            ?.groupValues
            ?.get(index = 1)
            ?: error("JSON string field not found: $fieldName")
}
