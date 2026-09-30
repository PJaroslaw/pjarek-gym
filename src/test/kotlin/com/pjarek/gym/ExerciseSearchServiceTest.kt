package com.pjarek.gym

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

class ExerciseSearchServiceTest {
    private val service = ExerciseSearchService(JdbcTemplate())

    @Test
    fun `short search terms return no results without querying the database`() {
        assertEquals(emptyList<Map<String, Any?>>(), service.search(" I "))
    }

    @Test
    fun `search terms longer than 100 characters are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.search("x".repeat(101))
        }
    }

    @Test
    fun `library search terms longer than 100 characters are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.searchLibrary("x".repeat(101))
        }
    }

    @Test
    fun `searches without alphanumeric terms return no results without querying the database`() {
        assertEquals(emptyList<Map<String, Any?>>(), service.search("!!"))
    }
}
