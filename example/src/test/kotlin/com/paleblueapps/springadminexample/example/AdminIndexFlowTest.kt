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
import java.nio.file.Files

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = ["/data.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminIndexFlowTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `index renders configured fragment before entities`() {
        mockMvc
            .get("/admin")
            .andExpect {
                status { isOk() }
                content { string(Matchers.containsString("Admin Home Extension")) }
                content { string(Matchers.containsString("Custom index content rendered before entities")) }
                content { string(Matchers.containsString("href=\"/admin/user\"")) }
            }
    }

    companion object {
        private val tempDirectory = Files.createTempDirectory("spring-admin-index-tests")
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
