package com.pjarek.gym.config

import com.pjarek.gym.repository.AccountRepository
import com.pjarek.gym.service.ExerciseCatalogImporter
import com.pjarek.gym.service.ExerciseDatasetSyncService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class Bootstrap(
    private val accounts: AccountRepository,
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
        accounts.createAdminIfMissing(adminName, encoder.encode(adminPassword)!!)
        catalogImporter.seedWhenEmpty()
        datasetSync.syncOnStartup()
    }
}
