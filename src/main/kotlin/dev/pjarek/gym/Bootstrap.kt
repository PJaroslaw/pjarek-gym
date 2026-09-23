package dev.pjarek.gym

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class Bootstrap(
    private val jdbc: JdbcTemplate,
    private val mapper: JsonMapper,
    private val encoder: PasswordEncoder,
    private val imageInstaller: ExerciseImageInstaller,
    @Value("\${app.admin.username:}") private val adminName: String,
    @Value("\${app.admin.password:}") private val adminPassword: String,
) : CommandLineRunner {
    @Transactional
    override fun run(vararg args: String) {
        require(adminName.isNotBlank() && adminPassword.length >= 12) {
            "Set APP_ADMIN_USERNAME and APP_ADMIN_PASSWORD (at least 12 characters) before starting the app"
        }
        jdbc.update(
            "INSERT INTO app_user(username,password_hash,role) VALUES (?,?,'ADMIN') ON CONFLICT(username) DO NOTHING",
            adminName, encoder.encode(adminPassword)
        )
        imageInstaller.install()
        val data = mapper.readTree(ClassPathResource("exercises.json").inputStream)
        data.forEach { item -> insertExercise(item) }
    }

    private fun insertExercise(item: JsonNode) {
        val id = item.path("id").asText(item.path("name").asText().replace(Regex("[^A-Za-z0-9]+"), "_"))
        jdbc.update(
            """INSERT INTO exercise(source_id,name,category,equipment,difficulty,force_type,mechanic,primary_muscles,secondary_muscles,instructions,image_paths)
               VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(source_id) DO UPDATE SET name=excluded.name,category=excluded.category,equipment=excluded.equipment,
               difficulty=excluded.difficulty,force_type=excluded.force_type,mechanic=excluded.mechanic,primary_muscles=excluded.primary_muscles,
               secondary_muscles=excluded.secondary_muscles,instructions=excluded.instructions,image_paths=excluded.image_paths""".trimIndent(),
            id, item.path("name").asText(), item.path("category").asText(""), item.path("equipment").asText(""),
            item.path("level").asText(""), item.path("force").asText(""), item.path("mechanic").asText(""),
            item.path("primaryMuscles").joinToString(", ") { it.asText() },
            item.path("secondaryMuscles").joinToString(", ") { it.asText() },
            item.path("instructions").joinToString("\n") { it.asText() },
            item.path("images").joinToString(",") { it.asText() }
        )
    }
}
