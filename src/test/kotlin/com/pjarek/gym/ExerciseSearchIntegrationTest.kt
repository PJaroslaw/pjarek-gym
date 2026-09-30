package com.pjarek.gym

import java.util.UUID
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ExerciseSearchIntegrationTest : IntegrationTestSupport() {
    @Test
    fun `search finds an exercise outside the first 300 alphabetic entries`() {
        mockMvc.perform(get("/api/exercises/search")
            .param("q", "Incline Dumbbell Press")
            .with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].name", hasItem("Incline Dumbbell Press")))
    }

    @Test
    fun `search requires at least two characters`() {
        mockMvc.perform(get("/api/exercises/search")
            .param("q", "I")
            .with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `exercise picker matches words in any order and tolerates misspellings`() {
        mockMvc.perform(get("/api/exercises/search")
            .param("q", "bench press dumbbel")
            .with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].name", hasItem("Dumbbell Bench Press")))
    }

    @Test
    fun `exercise library fuzzy search matches misspelled muscles and equipment`() {
        val fixtureName = "Fuzzy Metadata Fixture"
        val sourceId = "fuzzy-search-${UUID.randomUUID()}"
        jdbc.update(
            "INSERT INTO exercise(source_id,name,equipment,primary_muscles) VALUES (?,?,?,?)",
            sourceId,
            fixtureName,
            "dumbbell",
            "hamstrings"
        )

        try {
            mockMvc.perform(get("/exercises")
                .param("q", "hamstrng dumbel")
                .with(user("exercise-search-test")))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString(fixtureName)))
        } finally {
            jdbc.update("DELETE FROM exercise WHERE source_id=?", sourceId)
        }
    }

    @Test
    fun `both exercise search routes reject queries longer than 100 characters`() {
        val longQuery = "x".repeat(101)

        mockMvc.perform(get("/api/exercises/search")
            .param("q", longQuery)
            .with(user("exercise-search-test")))
            .andExpect(status().isBadRequest)

        mockMvc.perform(get("/exercises")
            .param("q", longQuery)
            .with(user("exercise-search-test")))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
    }
}
