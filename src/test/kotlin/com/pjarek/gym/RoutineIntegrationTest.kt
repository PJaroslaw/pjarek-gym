package com.pjarek.gym

import java.util.UUID
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class RoutineIntegrationTest : IntegrationTestSupport() {
    @Test
    fun `routine exercise plan can be edited`() {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val routineId = jdbc.queryForObject("INSERT INTO routine(owner_id,name) VALUES (?,?) RETURNING id", UUID::class.java, ownerId, "Editable plan")!!
        val dayId = jdbc.queryForObject("INSERT INTO routine_day(routine_id,name,position) VALUES (?, 'Day', 1) RETURNING id", Long::class.java, routineId)
        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Incline Dumbbell Press'", Long::class.java)!!
        val replacementExerciseId = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Dumbbell Bench Press'", Long::class.java)!!
        val itemId = jdbc.queryForObject("INSERT INTO routine_item(day_id,exercise_id,position) VALUES (?,?,1) RETURNING id", Long::class.java, dayId, exerciseId)

        mockMvc.perform(get("/routines/$routineId").with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Edit plan")))
            .andExpect(content().string(containsString("Incline Dumbbell Press")))
            .andExpect(content().string(containsString("name=\"minReps\" min=\"1\" max=\"1000\"")))
            .andExpect(content().string(containsString("name=\"maxReps\" min=\"1\" max=\"1000\"")))

        mockMvc.perform(post("/routines/$routineId/items/$itemId/edit")
            .param("exerciseId", replacementExerciseId.toString()).param("sets", "5").param("minReps", "6").param("maxReps", "8").param("rest", "150")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/routines/$routineId"))

        val item = jdbc.queryForMap("SELECT exercise_id,planned_sets,min_reps,max_reps,rest_seconds FROM routine_item WHERE id=?", itemId)
        assertEquals(replacementExerciseId, (item["exercise_id"] as Number).toLong())
        assertEquals(5, item["planned_sets"])
        assertEquals(6, item["min_reps"])
        assertEquals(8, item["max_reps"])
        assertEquals(150, item["rest_seconds"])
    }

    @Test
    fun `routine lifecycle creates no default day and manages named days and exercises`() {
        mockMvc.perform(post("/routines").param("name", "Training plan")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val routineId = jdbc.queryForObject("SELECT id FROM routine WHERE owner_id=? AND name='Training plan'", UUID::class.java, ownerId)!!
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM routine_day WHERE routine_id=?", Int::class.java, routineId))
        mockMvc.perform(post("/routines/$routineId/rename").param("name", "Strength plan")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals("Strength plan", jdbc.queryForObject("SELECT name FROM routine WHERE id=?", String::class.java, routineId))

        mockMvc.perform(post("/routines/$routineId/days").param("name", "Push")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        val dayId = jdbc.queryForObject("SELECT id FROM routine_day WHERE routine_id=?", Long::class.java, routineId)!!
        mockMvc.perform(post("/routines/$routineId/days/$dayId/rename").param("name", "Upper body")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals("Upper body", jdbc.queryForObject("SELECT name FROM routine_day WHERE id=?", String::class.java, dayId))

        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Incline Dumbbell Press'", Long::class.java)!!
        mockMvc.perform(post("/routines/$routineId/items").param("dayId", dayId.toString()).param("exerciseId", exerciseId.toString())
            .param("sets", "4").param("minReps", "6").param("maxReps", "8").param("rest", "120")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        val itemId = jdbc.queryForObject("SELECT id FROM routine_item WHERE day_id=?", Long::class.java, dayId)!!
        mockMvc.perform(post("/routines/$routineId/items/$itemId/delete")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM routine_item WHERE id=?", Int::class.java, itemId))

        mockMvc.perform(post("/routines/$routineId/days/$dayId/delete")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/routines/$routineId/delete")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM routine WHERE id=?", Int::class.java, routineId))
    }

    @Test
    fun `routine item creation and editing reject invalid rep ranges without changing the plan`() {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val routineId = jdbc.queryForObject("INSERT INTO routine(owner_id,name) VALUES (?, 'Range validation') RETURNING id", UUID::class.java, ownerId)!!
        val dayId = jdbc.queryForObject("INSERT INTO routine_day(routine_id,name,position) VALUES (?, 'Day', 1) RETURNING id", Long::class.java, routineId)!!
        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise LIMIT 1", Long::class.java)!!

        listOf("0" to "8", "9" to "8", "8" to "1001").forEach { (minReps, maxReps) ->
            mockMvc.perform(post("/routines/$routineId/items").param("dayId", dayId.toString())
                .param("exerciseId", exerciseId.toString()).param("minReps", minReps).param("maxReps", maxReps)
                .header("Referer", "http://localhost/routines/$routineId").with(user("exercise-search-test")).with(csrf()))
                .andExpect(status().is3xxRedirection)
        }
        mockMvc.perform(post("/routines/$routineId/items").param("dayId", dayId.toString())
            .param("exerciseId", Long.MAX_VALUE.toString()).param("minReps", "8").param("maxReps", "12")
            .header("Referer", "http://localhost/routines/$routineId").with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM routine_item WHERE day_id=?", Int::class.java, dayId))

        val itemId = jdbc.queryForObject("INSERT INTO routine_item(day_id,exercise_id,position) VALUES (?,?,1) RETURNING id", Long::class.java, dayId, exerciseId)!!
        mockMvc.perform(post("/routines/$routineId/items/$itemId/edit").param("exerciseId", exerciseId.toString())
            .param("sets", "3").param("minReps", "12").param("maxReps", "10").param("rest", "90")
            .header("Referer", "http://localhost/routines/$routineId").with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(8, jdbc.queryForObject("SELECT min_reps FROM routine_item WHERE id=?", Int::class.java, itemId))
        assertEquals(12, jdbc.queryForObject("SELECT max_reps FROM routine_item WHERE id=?", Int::class.java, itemId))
    }

    @Test
    fun `routine forms reject invalid names and block access to another users routine`() {
        mockMvc.perform(post("/routines").param("name", "   ")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().isBadRequest)
            .andExpect(content().string(containsString("Enter a name for your routine")))
        mockMvc.perform(post("/routines").param("name", "x".repeat(161))
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().isBadRequest)

        mockMvc.perform(post("/routines").param("name", "Validation plan")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val ownRoutine = jdbc.queryForObject("SELECT id FROM routine WHERE owner_id=? AND name='Validation plan'", UUID::class.java, ownerId)!!
        mockMvc.perform(post("/routines/$ownRoutine/days").param("name", " ")
            .header("Referer", "http://localhost/routines/$ownRoutine")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/routines/$ownRoutine"))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM routine_day WHERE routine_id=?", Int::class.java, ownRoutine))

        val adminId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='integration-admin'", Long::class.java)!!
        val foreignRoutine = jdbc.queryForObject("INSERT INTO routine(owner_id,name) VALUES (?, 'Private') RETURNING id", UUID::class.java, adminId)!!
        mockMvc.perform(get("/routines/$foreignRoutine").with(user("exercise-search-test")))
            .andExpect(status().isForbidden)
        mockMvc.perform(post("/routines/$foreignRoutine/delete").with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().isForbidden)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM routine WHERE id=?", Int::class.java, foreignRoutine))
    }
}
