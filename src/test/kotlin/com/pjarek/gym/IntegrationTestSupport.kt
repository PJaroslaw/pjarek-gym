package com.pjarek.gym

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest(properties = ["app.admin.username=integration-admin", "app.admin.password=IntegrationTestPass123!", "app.exercise-sync.on-startup=false", "app.exercise-sync.cron=-"])
@AutoConfigureMockMvc
abstract class IntegrationTestSupport {
    @Autowired protected lateinit var mockMvc: MockMvc
    @Autowired protected lateinit var jdbc: JdbcTemplate
    @Autowired protected lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun createTestUser() {
        jdbc.update("INSERT INTO app_user(username,password_hash,role) VALUES ('exercise-search-test','unused','USER')")
    }

    @AfterEach
    fun removeTestUser() {
        jdbc.update("DELETE FROM app_user WHERE username IN ('exercise-search-test','temporary-password-test','user-delete-test','created-admin-test')")
    }

    companion object {
        @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:18-alpine").apply {
            start()
            Runtime.getRuntime().addShutdownHook(Thread { stop() })
        }

        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
