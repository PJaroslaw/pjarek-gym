package com.pjarek.gym

import java.math.BigDecimal
import java.util.UUID
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class WorkoutSetIntegrationTest : IntegrationTestSupport() {
    @Test
    fun `workout sets can be edited and deleted while preserving set order`() {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Incline Dumbbell Press'", Long::class.java)!!
        val workoutId = jdbc.queryForObject("INSERT INTO workout(owner_id,title) VALUES (?,'Set editing test') RETURNING id", UUID::class.java, ownerId)!!
        val workoutExerciseId = jdbc.queryForObject(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position,instructions_snapshot,planned_sets) SELECT ?,id,1,instructions,3 FROM exercise WHERE id=? RETURNING id",
            Long::class.java, workoutId, exerciseId
        )!!
        val firstSetId = jdbc.queryForObject(
            "INSERT INTO workout_set(workout_exercise_id,set_number,reps,weight) VALUES (?,1,8,20) RETURNING id", Long::class.java, workoutExerciseId
        )!!
        val secondSetId = jdbc.queryForObject(
            "INSERT INTO workout_set(workout_exercise_id,set_number,reps,weight) VALUES (?,2,9,22) RETURNING id", Long::class.java, workoutExerciseId
        )!!

        mockMvc.perform(get("/workouts/$workoutId").with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Save set")))
            .andExpect(content().string(containsString("class=\"field-label\"")))
            .andExpect(content().string(containsString("Delete set 1")))

        mockMvc.perform(post("/workouts/$workoutId/sets/$firstSetId/edit")
            .param("reps", "10").param("weight", "25.5")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        val edited = jdbc.queryForMap("SELECT reps,weight FROM workout_set WHERE id=?", firstSetId)
        assertEquals(10, edited["reps"])
        assertEquals(BigDecimal("25.50"), edited["weight"])

        mockMvc.perform(post("/workouts/$workoutId/sets/$secondSetId/delete")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_set WHERE id=?", Int::class.java, secondSetId))
        assertEquals(1, jdbc.queryForObject("SELECT set_number FROM workout_set WHERE id=?", Int::class.java, firstSetId))

        val adminId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='integration-admin'", Long::class.java)!!
        val otherWorkoutId = jdbc.queryForObject("INSERT INTO workout(owner_id,title) VALUES (?,'Private workout') RETURNING id", UUID::class.java, adminId)!!
        val otherItemId = jdbc.queryForObject(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position,instructions_snapshot) SELECT ?,id,1,instructions FROM exercise WHERE id=? RETURNING id",
            Long::class.java, otherWorkoutId, exerciseId
        )!!
        val otherSetId = jdbc.queryForObject("INSERT INTO workout_set(workout_exercise_id,set_number,reps,weight) VALUES (?,1,8,20) RETURNING id", Long::class.java, otherItemId)
        mockMvc.perform(post("/workouts/$otherWorkoutId/sets/$otherSetId/delete")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().isForbidden)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM workout_set WHERE id=?", Int::class.java, otherSetId))
        jdbc.update("DELETE FROM workout WHERE id=?", otherWorkoutId)
    }

    @Test
    fun `set logging rejects invalid values and unknown set edits without changing saved sets`() {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val workoutId = jdbc.queryForObject("INSERT INTO workout(owner_id,title) VALUES (?,'Validation workout') RETURNING id", UUID::class.java, ownerId)!!
        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Incline Dumbbell Press'", Long::class.java)!!
        val itemId = jdbc.queryForObject(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position) VALUES (?,?,1) RETURNING id",
            Long::class.java, workoutId, exerciseId
        )!!

        mockMvc.perform(post("/workouts/$workoutId/sets").param("itemId", itemId.toString()).param("reps", "1001").param("weight", "20")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/workouts/$workoutId/sets").param("itemId", itemId.toString()).param("reps", "8").param("weight", "2001")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_set WHERE workout_exercise_id=?", Int::class.java, itemId))

        mockMvc.perform(post("/workouts/$workoutId/sets").param("itemId", itemId.toString()).param("reps", "8").param("weight", "20")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        val setId = jdbc.queryForObject("SELECT id FROM workout_set WHERE workout_exercise_id=?", Long::class.java, itemId)!!
        mockMvc.perform(post("/workouts/$workoutId/sets/$setId/edit").param("reps", "-1").param("weight", "30")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        val saved = jdbc.queryForMap("SELECT reps,weight FROM workout_set WHERE id=?", setId)
        assertEquals(8, saved["reps"])
        assertEquals(BigDecimal("20.00"), saved["weight"])
        mockMvc.perform(post("/workouts/$workoutId/sets/999999/edit").param("reps", "9").param("weight", "30")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM workout_set WHERE id=?", Int::class.java, setId))
    }
}
