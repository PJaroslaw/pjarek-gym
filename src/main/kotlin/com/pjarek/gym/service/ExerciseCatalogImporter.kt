package com.pjarek.gym.service

import com.pjarek.gym.model.CatalogExercise
import com.pjarek.gym.repository.ExerciseRepository
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Component
class ExerciseCatalogImporter(private val repository: ExerciseRepository, private val mapper: JsonMapper) {
    @Transactional
    fun seedWhenEmpty() {
        val count = repository.count()
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
        repository.deactivateImported()
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
        repository.upsertImported(CatalogExercise(
            item.path("id").asText(), item.path("name").asText(), item.path("category").asText(""),
            item.path("equipment").asText(""), item.path("level").asText(""), item.path("force").asText(""),
            item.path("mechanic").asText(""), item.path("primaryMuscles").joinToString(", ") { it.asText() },
            item.path("secondaryMuscles").joinToString(", ") { it.asText() },
            item.path("instructions").joinToString("\n") { it.asText() }, item.path("images").joinToString(",") { it.asText() }
        ))
    }
}
