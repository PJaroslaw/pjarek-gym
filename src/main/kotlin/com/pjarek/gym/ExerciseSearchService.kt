package com.pjarek.gym

import java.util.Locale
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class ExerciseSearchService(private val jdbc: JdbcTemplate) {
    fun search(query: String): List<Map<String, Any?>> {
        val term = query.trim()
        if (term.length < MIN_QUERY_LENGTH) return emptyList()
        require(term.length <= MAX_QUERY_LENGTH) { "Search terms can be up to $MAX_QUERY_LENGTH characters." }
        val terms = tokenize(term)
        if (terms.isEmpty()) return emptyList()
        return searchExercises(term, terms, NAME_FIELDS, "e.id,e.name", PICKER_RESULT_LIMIT)
    }

    fun searchLibrary(query: String): List<Map<String, Any?>> {
        require(query.length <= MAX_QUERY_LENGTH) { "Search terms can be up to $MAX_QUERY_LENGTH characters." }
        val term = query.trim()
        val columns = "e.id,e.name,e.category,e.equipment,e.primary_muscles,e.source_id"
        if (term.isEmpty()) {
            return jdbc.queryForList("SELECT $columns FROM exercise e WHERE e.active=TRUE ORDER BY e.name LIMIT $LIBRARY_RESULT_LIMIT")
        }
        val terms = tokenize(term)
        if (terms.isEmpty()) return emptyList()
        return searchExercises(term, terms, LIBRARY_FIELDS, columns, LIBRARY_RESULT_LIMIT)
    }

    private fun searchExercises(
        query: String,
        terms: List<String>,
        fields: List<String>,
        columns: String,
        limit: Int
    ): List<Map<String, Any?>> {
        val whereParameters = mutableListOf<Any>()
        val termConditions = terms.joinToString(" AND ") { term ->
            fields.joinToString(" OR ", prefix = "(", postfix = ")") { field ->
                if (term.length < FUZZY_TERM_MIN_LENGTH) {
                    whereParameters.add(term)
                    "lower($field) LIKE '%' || ? || '%'"
                } else {
                    whereParameters.add(term)
                    whereParameters.add(term)
                    "(lower($field) LIKE '%' || ? || '%' OR word_similarity(?, lower($field)) >= $SIMILARITY_THRESHOLD)"
                }
            }
        }
        val scoreExpression = List(terms.size) {
            fields.joinToString(", ", prefix = "GREATEST(", postfix = ")") { "word_similarity(?, lower($it))" }
        }.joinToString(" + ")
        val parameters = mutableListOf<Any>().apply {
            addAll(whereParameters)
            add(normalize(query))
            terms.forEach { term -> fields.forEach { add(term) } }
        }

        return jdbc.queryForList(
            """SELECT $columns FROM exercise e
                WHERE e.active=TRUE AND ($termConditions)
                ORDER BY CASE WHEN lower(e.name) LIKE '%' || ? || '%' THEN 1 ELSE 0 END DESC,
                    $scoreExpression DESC,e.name
                LIMIT $limit""",
            *parameters.toTypedArray()
        )
    }

    private fun tokenize(query: String): List<String> =
        normalize(query).split(WORD_SEPARATOR).filter(String::isNotEmpty).distinct()

    private fun normalize(query: String): String =
        query.lowercase(Locale.ROOT).replace(NON_WORD_CHARACTERS, " ").trim().replace(MULTIPLE_SPACES, " ")

    companion object {
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_QUERY_LENGTH = 100
        private const val FUZZY_TERM_MIN_LENGTH = 4
        private const val SIMILARITY_THRESHOLD = 0.45
        private const val PICKER_RESULT_LIMIT = 100
        private const val LIBRARY_RESULT_LIMIT = 200
        private val WORD_SEPARATOR = Regex("\\s+")
        private val NON_WORD_CHARACTERS = Regex("[^\\p{L}\\p{N}]+")
        private val MULTIPLE_SPACES = Regex("\\s+")
        private val NAME_FIELDS = listOf("e.name")
        private val LIBRARY_FIELDS = listOf("e.name", "e.primary_muscles", "e.equipment")
    }
}
