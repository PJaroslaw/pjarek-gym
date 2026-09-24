package com.pjarek.gym

import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
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
}
