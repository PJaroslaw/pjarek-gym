package com.pjarek.gym

import java.math.BigDecimal
import java.util.UUID
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `logging a set starts the planned rest countdown`() {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val workoutId = jdbc.queryForObject("INSERT INTO workout(owner_id,title) VALUES (?,'Rest timer') RETURNING id", UUID::class.java, ownerId)!!
        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Incline Dumbbell Press'", Long::class.java)!!
        val itemId = jdbc.queryForObject(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position,planned_sets,rest_seconds) VALUES (?,?,1,3,75) RETURNING id",
            Long::class.java, workoutId, exerciseId
        )!!

        val response = mockMvc.perform(post("/workouts/$workoutId/sets").param("itemId", itemId.toString()).param("reps", "8").param("weight", "20")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andReturn().response
        assertTrue(response.redirectedUrl.orEmpty().startsWith("/workouts/$workoutId?exercise=$itemId&restSeconds=75&restStartedAt="))

        mockMvc.perform(get("/workouts/$workoutId").param("exercise", itemId.toString()).with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("data-rest-timer")))
            .andExpect(content().string(containsString("Cancel rest timer")))
            .andExpect(content().string(containsString("Pause workout")))

        val serverStartedAt = System.currentTimeMillis() - 10_000
        val page = mockMvc.perform(get("/workouts/$workoutId")
            .param("exercise", itemId.toString())
            .param("restSeconds", "75")
            .param("restStartedAt", serverStartedAt.toString())
            .with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val remainingMillis = Regex("""data-rest-remaining-millis="(\d+)"""")
            .find(page)?.groupValues?.get(1)?.toLong()
        assertTrue(remainingMillis != null && remainingMillis in 64_000L..65_000L)

        mockMvc.perform(get("/workouts/$workoutId")
            .param("exercise", itemId.toString())
            .param("restSeconds", "75")
            .param("restStartedAt", (System.currentTimeMillis() - 90_000).toString())
            .with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("data-rest-remaining-millis=\"0\"")))
    }

    @Test
    fun `pound inputs are stored as kilograms and display converts with the saved preference`() {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Incline Dumbbell Press'", Long::class.java)!!
        val workoutId = jdbc.queryForObject("INSERT INTO workout(owner_id,title) VALUES (?,'Unit conversion') RETURNING id", UUID::class.java, ownerId)!!
        val itemId = jdbc.queryForObject(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position) VALUES (?,?,1) RETURNING id",
            Long::class.java,
            workoutId,
            exerciseId
        )!!
        jdbc.update("UPDATE app_user SET weight_unit='LB' WHERE id=?", ownerId)

        mockMvc.perform(post("/workouts/$workoutId/sets").param("itemId", itemId.toString()).param("reps", "8").param("weight", "100")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        val setId = jdbc.queryForObject("SELECT id FROM workout_set WHERE workout_exercise_id=?", Long::class.java, itemId)!!
        assertEquals(BigDecimal("45.36"), jdbc.queryForObject("SELECT weight FROM workout_set WHERE id=?", BigDecimal::class.java, setId))

        mockMvc.perform(get("/workouts/$workoutId").with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("100.00 LB")))
            .andExpect(content().string(containsString("value=\"100.00\"")))

        jdbc.update("UPDATE app_user SET weight_unit='KG' WHERE id=?", ownerId)
        mockMvc.perform(get("/workouts/$workoutId").with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("45.36 KG")))
            .andExpect(content().string(containsString("value=\"45.36\"")))
        mockMvc.perform(post("/workouts/$workoutId/sets/$setId/edit").param("reps", "8").param("weight", "45.36")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(BigDecimal("45.36"), jdbc.queryForObject("SELECT weight FROM workout_set WHERE id=?", BigDecimal::class.java, setId))
    }

    @Test
    fun `pound input rejects values outside quarter pound increments`() {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val workoutId = jdbc.queryForObject("INSERT INTO workout(owner_id,title) VALUES (?,'Invalid pound value') RETURNING id", UUID::class.java, ownerId)!!
        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Incline Dumbbell Press'", Long::class.java)!!
        val itemId = jdbc.queryForObject(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position) VALUES (?,?,1) RETURNING id",
            Long::class.java,
            workoutId,
            exerciseId
        )!!
        jdbc.update("UPDATE app_user SET weight_unit='LB' WHERE id=?", ownerId)

        mockMvc.perform(post("/workouts/$workoutId/sets").param("itemId", itemId.toString()).param("reps", "8").param("weight", "10.1")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_set WHERE workout_exercise_id=?", Int::class.java, itemId))
    }
}
