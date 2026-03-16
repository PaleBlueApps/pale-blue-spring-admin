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
import org.springframework.util.LinkedMultiValueMap

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = ["/data.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminDeleteFlowTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `delete confirmation shows selected object details`() {
        mockMvc.get("/admin/user/1/delete")
            .andExpect {
                status { isOk() }
                content { string(Matchers.containsString("Are you sure you want to delete the following User?")) }
                content { string(Matchers.containsString("User(id=1, username=john_doe, email=john@example.com, age=28)")) }
            }
    }

    @Test
    fun `delete confirmation removes object and redirects to list`() {
        mockMvc.post("/admin/user/12/delete")
            .andExpect {
                status { is3xxRedirection() }
                redirectedUrl("/admin/user")
                flash { attribute("message", "The User \"12\" was deleted successfully.") }
            }

        mockMvc.get("/admin/user/12")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `delete handles already missing objects gracefully`() {
        mockMvc.post("/admin/user/999/delete")
            .andExpect {
                status { is3xxRedirection() }
                redirectedUrl("/admin/user")
                flash { attribute("error", "The User \"999\" no longer exists.") }
            }
    }

    @Test
    fun `bulk action redirects to delete confirmation with selected ids`() {
        mockMvc.post("/admin/user/actions") {
            param("action", "delete")
            param("selectedIds", "1", "2")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/admin/user/delete")
            flash { attribute("selectedIds", listOf("1", "2")) }
        }
    }

    @Test
    fun `bulk delete confirmation shows selected objects`() {
        mockMvc.get("/admin/user/delete") {
            flashAttrs = linkedMapOf("selectedIds" to listOf("1", "2"))
        }.andExpect {
            status { isOk() }
            content { string(Matchers.containsString("Delete")) }
            content { string(Matchers.containsString("User(id=1, username=john_doe, email=john@example.com, age=28)")) }
            content { string(Matchers.containsString("User(id=2, username=alice_smith, email=alice@example.com, age=32)")) }
        }
    }

    @Test
    fun `bulk delete removes selected objects and reports result`() {
        mockMvc.post("/admin/user/delete") {
            params = LinkedMultiValueMap<String, String>().apply {
                add("selectedIds", "10")
                add("selectedIds", "11")
            }
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/admin/user")
            flash { attribute("message", "Successfully deleted 2 user records.") }
        }

        mockMvc.get("/admin/user/10")
            .andExpect {
                status { isNotFound() }
            }

        mockMvc.get("/admin/user/11")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `bulk action requires selection`() {
        mockMvc.post("/admin/user/actions") {
            param("action", "delete")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/admin/user")
            flash { attribute("error", "Items must be selected in order to perform actions on them. No items have been changed.") }
        }
    }

    @Test
    fun `bulk action can target all matching records across pages`() {
        mockMvc.post("/admin/user/actions") {
            param("action", "delete")
            param("selectAllMatching", "true")
            param("q", "example.com")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/admin/user/delete")
            flash { attribute("selectedIds", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")) }
        }
    }

    companion object {
        private val tempDirectory = Files.createTempDirectory("spring-admin-tests")
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
