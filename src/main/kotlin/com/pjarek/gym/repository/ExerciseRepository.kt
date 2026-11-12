package com.pjarek.gym.repository

import com.pjarek.gym.model.CatalogExercise
import org.springframework.jdbc.core.ColumnMapRowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ExerciseRepository(private val jdbc: NamedParameterJdbcTemplate) {
    private val rows = ColumnMapRowMapper()

    fun count(): Int = jdbc.queryForObject("SELECT count(*) FROM exercise", emptyMap<String, Any>(), Int::class.java)!!

    fun deactivateImported() {
        jdbc.update("UPDATE exercise SET active=FALSE WHERE source_id IS NOT NULL", emptyMap<String, Any>())
    }

    fun upsertImported(exercise: CatalogExercise) {
        jdbc.update(
            """INSERT INTO exercise(source_id,name,category,equipment,difficulty,force_type,mechanic,primary_muscles,secondary_muscles,instructions,image_paths,active)
                VALUES (:sourceId,:name,:category,:equipment,:difficulty,:forceType,:mechanic,:primaryMuscles,:secondaryMuscles,:instructions,:imagePaths,TRUE)
                ON CONFLICT(source_id) DO UPDATE SET name=excluded.name,category=excluded.category,equipment=excluded.equipment,
                difficulty=excluded.difficulty,force_type=excluded.force_type,mechanic=excluded.mechanic,primary_muscles=excluded.primary_muscles,
                secondary_muscles=excluded.secondary_muscles,instructions=excluded.instructions,image_paths=excluded.image_paths,active=TRUE""",
            mapOf(
                "sourceId" to exercise.sourceId, "name" to exercise.name, "category" to exercise.category,
                "equipment" to exercise.equipment, "difficulty" to exercise.difficulty, "forceType" to exercise.forceType,
                "mechanic" to exercise.mechanic, "primaryMuscles" to exercise.primaryMuscles,
                "secondaryMuscles" to exercise.secondaryMuscles, "instructions" to exercise.instructions,
                "imagePaths" to exercise.imagePaths
            )
        )
    }

    fun find(id: Long): Map<String, Any?> = jdbc.queryForMap(
        "SELECT * FROM exercise WHERE id=:id", mapOf("id" to id)
    )

    fun findActive(ids: Collection<Long>): List<Map<String, Any?>> {
        if (ids.isEmpty()) return emptyList()
        return jdbc.query(
            "SELECT id,name,equipment,primary_muscles,instructions,source_id FROM exercise WHERE active=TRUE AND id IN (:ids)",
            mapOf("ids" to ids.distinct()), rows
        )
    }

    fun imagePaths(sourceId: String): String? = jdbc.query(
        "SELECT image_paths FROM exercise WHERE source_id=:sourceId",
        mapOf("sourceId" to sourceId), { rs, _ -> rs.getString("image_paths") }
    ).firstOrNull()

    fun active(id: Long): Boolean = jdbc.queryForObject(
        "SELECT count(*) FROM exercise WHERE id=:id AND active=TRUE", mapOf("id" to id), Int::class.java
    ) == 1

    fun activeNames(): List<Map<String, Any?>> = jdbc.query(
        "SELECT id,name FROM exercise WHERE active=TRUE ORDER BY name", emptyMap<String, Any>(), rows
    )

    fun routineBuilderCatalog(): List<Map<String, Any?>> = jdbc.query(
        """WITH ranked AS (
            SELECT id,name,category,equipment,primary_muscles,mechanic,
                   row_number() OVER (PARTITION BY equipment,primary_muscles ORDER BY name,id) AS choice
            FROM exercise WHERE active=TRUE
        )
        SELECT id,name,category,equipment,primary_muscles,mechanic FROM ranked WHERE choice<=2 ORDER BY md5(id::text)""",
        emptyMap<String, Any>(), rows
    )

    fun library(): List<Map<String, Any?>> = jdbc.query(
        "SELECT e.id,e.name,e.category,e.equipment,e.primary_muscles,e.source_id FROM exercise e WHERE e.active=TRUE ORDER BY e.name LIMIT 200",
        emptyMap<String, Any>(), rows
    )

    fun searchPicker(query: String, terms: List<String>): List<Map<String, Any?>> =
        search(query, terms, listOf("e.name"), "e.id,e.name,e.equipment,e.primary_muscles,e.instructions,e.source_id", 100)

    fun searchLibrary(query: String, terms: List<String>): List<Map<String, Any?>> =
        search(query, terms, listOf("e.name", "e.primary_muscles", "e.equipment"),
            "e.id,e.name,e.category,e.equipment,e.primary_muscles,e.source_id", 200)

    private fun search(query: String, terms: List<String>, fields: List<String>, columns: String, limit: Int): List<Map<String, Any?>> {
        val parameters = mutableMapOf<String, Any>()
        val termConditions = terms.mapIndexed { termIndex, term ->
            fields.mapIndexed { fieldIndex, field ->
                val key = "term${termIndex}Field${fieldIndex}"
                parameters[key] = term
                if (term.length < 4) {
                    "lower($field) LIKE '%' || :$key || '%'"
                } else {
                    "(lower($field) LIKE '%' || :$key || '%' OR word_similarity(:$key, lower($field)) >= 0.45)"
                }
            }.joinToString(" OR ", prefix = "(", postfix = ")")
        }.joinToString(" AND ")
        val scoreExpression = terms.mapIndexed { termIndex, term ->
            fields.mapIndexed { fieldIndex, field ->
                val key = "score${termIndex}Field${fieldIndex}"
                parameters[key] = term
                "word_similarity(:$key, lower($field))"
            }.joinToString(", ", prefix = "GREATEST(", postfix = ")")
        }.joinToString(" + ")
        parameters["query"] = query
        return jdbc.query(
            """SELECT $columns FROM exercise e WHERE e.active=TRUE AND ($termConditions)
                ORDER BY CASE WHEN lower(e.name) LIKE '%' || :query || '%' THEN 1 ELSE 0 END DESC,
                    $scoreExpression DESC,e.name LIMIT $limit""",
            parameters, rows
        )
    }
}
