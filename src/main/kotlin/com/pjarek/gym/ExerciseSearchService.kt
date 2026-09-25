package com.pjarek.gym

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class ExerciseSearchService(private val jdbc: JdbcTemplate) {
    fun search(query: String): List<Map<String, Any?>> {
        val term = query.trim()
        if (term.length < 2) return emptyList()
        require(term.length <= 100) { "Search terms can be up to 100 characters." }
        return jdbc.queryForList(
            "SELECT id,name FROM exercise WHERE active=TRUE AND name ILIKE ? ORDER BY name LIMIT 100",
            "%$term%"
        )
    }
}
