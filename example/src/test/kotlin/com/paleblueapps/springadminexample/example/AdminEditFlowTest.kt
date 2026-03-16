package com.paleblueapps.springadminexample.example

import org.hamcrest.Matchers
import java.nio.file.Files
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = ["/data.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminEditFlowTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `detail page renders editable pre-populated form`() {
        mockMvc.get("/admin/user/1")
            .andExpect {
                status { isOk() }
                content { string(Matchers.containsString("<form id=\"entity-edit-form\" action=\"/admin/user/1\" method=\"post\">")) }
                content { string(Matchers.containsString("name=\"username\"")) }
                content { string(Matchers.containsString("value=\"john_doe\"")) }
                content { string(Matchers.containsString("value=\"john@example.com\"")) }
            }
    }

    @Test
    fun `save updates scalar fields`() {
        mockMvc.post("/admin/user/1") {
            param("id", "1")
            param("username", "john_updated")
            param("email", "john.updated@example.com")
            param("age", "40")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/admin/user/1")
            flash { attribute("message", "The User \"1\" was saved successfully.") }
        }

        mockMvc.get("/admin/user/1")
            .andExpect {
                content { string(Matchers.containsString("value=\"john_updated\"")) }
                content { string(Matchers.containsString("value=\"john.updated@example.com\"")) }
                content { string(Matchers.containsString("value=\"40\"")) }
            }
    }

    @Test
    fun `save updates many to one relation through dropdown`() {
        mockMvc.post("/admin/post/1") {
            param("id", "1")
            param("title", "First Post")
            param("content", "Welcome to the example application!")
            param("user", "2")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/admin/post/1")
            flash { attribute("message", "The Post \"1\" was saved successfully.") }
        }

        mockMvc.get("/admin/post/1")
            .andExpect {
                content { string(Matchers.containsString("<option value=\"2\"")) }
                content { string(Matchers.containsString("selected=\"selected\">User(id=2, username=alice_smith, email=alice@example.com, age=32)</option>")) }
            }
    }

    @Test
    fun `save rejects invalid values and shows error`() {
        mockMvc.post("/admin/user/1") {
            param("id", "1")
            param("username", "john_doe")
            param("email", "john@example.com")
            param("age", "not-a-number")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/admin/user/1")
            flash { attribute("error", "Invalid value for age: not-a-number") }
        }
    }

    companion object {
        private val tempDirectory = Files.createTempDirectory("spring-admin-edit-tests")
        private val databaseFile = tempDirectory.resolve("app.sqlite3")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                "jdbc:sqlite:${databaseFile.toAbsolutePath()}?foreign_keys=on"
            }
        }
    }
}
