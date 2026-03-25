package com.paleblueapps.springadminexample.example

import org.hamcrest.Matchers
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
import java.nio.file.Files

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = ["/data.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminRelationListFlowTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `relation list renders bulk action controls`() {
        mockMvc
            .get("/admin/user/1/rel/posts")
            .andExpect {
                status { isOk() }
                content { string(Matchers.containsString("name=\"action\"")) }
                content { string(Matchers.containsString("Delete selected")) }
                content { string(Matchers.containsString("class=\"row-selector")) }
            }
    }

    @Test
    fun `relation list selected items can flow to existing delete confirmation`() {
        mockMvc
            .post("/admin/post/actions") {
                param("action", "delete")
                param("selectedIds", "1", "2")
            }.andExpect {
                status { is3xxRedirection() }
                redirectedUrl("/admin/post/delete")
                flash { attribute("selectedIds", listOf("1", "2")) }
            }
    }

    companion object {
        private val tempDirectory = Files.createTempDirectory("spring-admin-relation-tests")
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
