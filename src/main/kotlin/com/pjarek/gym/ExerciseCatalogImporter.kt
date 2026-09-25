package com.pjarek.gym

import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Component
class ExerciseCatalogImporter(private val jdbc: JdbcTemplate, private val mapper: JsonMapper) {
    @Transactional
    fun seedWhenEmpty() {
        val count = jdbc.queryForObject("SELECT count(*) FROM exercise", Int::class.java)!!
        if (count == 0) {
            val catalog = mapper.readTree(ClassPathResource("exercises.json").inputStream)
            validate(catalog)
            importCatalog(catalog)
        }
    }

    @Transactional
    fun import(data: JsonNode) {
        validate(data)
        importCatalog(data)
    }

    private fun importCatalog(data: JsonNode) {
        jdbc.update("UPDATE exercise SET active=FALSE WHERE source_id IS NOT NULL")
        data.forEach { item -> insertExercise(item) }
    }

    fun validate(data: JsonNode): Set<String> {
        require(data.isArray && data.size() >= 100) { "The upstream exercise catalog is missing or incomplete." }
        val ids = mutableSetOf<String>()
        val imagePaths = mutableSetOf<String>()
        data.forEach { item ->
            val id = item.path("id").asText()
            require(id.matches(Regex("[A-Za-z0-9_-]{1,160}")) && ids.add(id)) {
                "The exercise catalog contains an invalid or duplicate exercise id."
            }
            item.path("images").forEach { image ->
                val path = image.asText()
                require(path.matches(Regex("[A-Za-z0-9_-]+/[0-3]\\.jpg"))) {
                    "The exercise catalog contains an invalid image path."
                }
                imagePaths.add(path)
            }
        }
        return imagePaths
    }

    private fun insertExercise(item: JsonNode) {
        jdbc.update(
            """INSERT INTO exercise(source_id,name,category,equipment,difficulty,force_type,mechanic,primary_muscles,secondary_muscles,instructions,image_paths,active)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,TRUE) ON CONFLICT(source_id) DO UPDATE SET name=excluded.name,category=excluded.category,equipment=excluded.equipment,
                difficulty=excluded.difficulty,force_type=excluded.force_type,mechanic=excluded.mechanic,primary_muscles=excluded.primary_muscles,
               secondary_muscles=excluded.secondary_muscles,instructions=excluded.instructions,image_paths=excluded.image_paths,active=TRUE""".trimIndent(),
            item.path("id").asText(), item.path("name").asText(), item.path("category").asText(""), item.path("equipment").asText(""),
            item.path("level").asText(""), item.path("force").asText(""), item.path("mechanic").asText(""),
            item.path("primaryMuscles").joinToString(", ") { it.asText() },
            item.path("secondaryMuscles").joinToString(", ") { it.asText() },
            item.path("instructions").joinToString("\n") { it.asText() },
            item.path("images").joinToString(",") { it.asText() }
        )
    }
}
