package com.pjarek.gym

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class Bootstrap(
    private val jdbc: JdbcTemplate,
    private val encoder: PasswordEncoder,
    private val catalogImporter: ExerciseCatalogImporter,
    private val datasetSync: ExerciseDatasetSyncService,
    @Value("\${app.admin.username:}") private val adminName: String,
    @Value("\${app.admin.password:}") private val adminPassword: String,
) : CommandLineRunner {
    override fun run(vararg args: String) {
        require(adminName.isNotBlank() && adminPassword.isNotEmpty()) {
            "Set APP_ADMIN_USERNAME and APP_ADMIN_PASSWORD before starting the app"
        }
        jdbc.update(
            "INSERT INTO app_user(username,password_hash,role) VALUES (?,?,'ADMIN') ON CONFLICT(username) DO NOTHING",
            adminName, encoder.encode(adminPassword)
        )
        catalogImporter.seedWhenEmpty()
        datasetSync.syncOnStartup()
    }
}
