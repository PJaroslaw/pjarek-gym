package com.pjarek.gym

import java.util.UUID
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class WorkoutIntegrationTest : IntegrationTestSupport() {
    @Test
    fun `starting a routine day creates a uuid workout and advances one exercise at a time`() {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val routineId = jdbc.queryForObject("INSERT INTO routine(owner_id,name) VALUES (?, 'Push') RETURNING id", UUID::class.java, ownerId)!!
        val dayId = jdbc.queryForObject("INSERT INTO routine_day(routine_id,name,position) VALUES (?, 'Day 1',1) RETURNING id", Long::class.java, routineId)!!
        val firstExercise = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Incline Dumbbell Press'", Long::class.java)!!
        val secondExercise = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Dumbbell Bench Press'", Long::class.java)!!
        jdbc.update("INSERT INTO routine_item(day_id,exercise_id,position,planned_sets,rep_target) VALUES (?,?,1,1,'8')", dayId, firstExercise)
        jdbc.update("INSERT INTO routine_item(day_id,exercise_id,position,planned_sets,rep_target) VALUES (?,?,2,1,'8')", dayId, secondExercise)

        val start = mockMvc.perform(post("/routines/$routineId/days/$dayId/start").with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andReturn().response.redirectedUrl!!
        val workoutId = UUID.fromString(start.substringAfterLast('/'))
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM workout_exercise WHERE workout_id=?", Int::class.java, workoutId))

        val firstStep = mockMvc.perform(get("/workouts/$workoutId").with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("EXERCISE 1 OF 2")))
            .andExpect(content().string(containsString("Incline Dumbbell Press")))
            .andReturn().response.contentAsString
        assertTrue(firstStep.contains("FORM NOTES"))
        val firstItemId = jdbc.queryForObject("SELECT id FROM workout_exercise WHERE workout_id=? AND position=1", Long::class.java, workoutId)!!
        mockMvc.perform(post("/workouts/$workoutId/sets").param("itemId", firstItemId.toString()).param("reps", "8").param("weight", "20")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)

        mockMvc.perform(get("/workouts/$workoutId").with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("EXERCISE 2 OF 2")))
            .andExpect(content().string(containsString("Dumbbell Bench Press")))
        mockMvc.perform(post("/workouts/$workoutId/finish").with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM workout WHERE id=?", String::class.java, workoutId))
    }

    @Test
    fun `workout page shows the previous completed session for that exercise`() {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Incline Dumbbell Press'", Long::class.java)!!
        val previousId = jdbc.queryForObject("INSERT INTO workout(owner_id,title,status,completed_at) VALUES (?,'Previous','COMPLETED',now()) RETURNING id", UUID::class.java, ownerId)!!
        val previousItem = jdbc.queryForObject("INSERT INTO workout_exercise(workout_id,exercise_id,position) VALUES (?,?,1) RETURNING id", Long::class.java, previousId, exerciseId)!!
        jdbc.update("INSERT INTO workout_set(workout_exercise_id,set_number,reps,weight) VALUES (?,1,10,32.5)", previousItem)
        val currentId = jdbc.queryForObject("INSERT INTO workout(owner_id,title) VALUES (?,'Current') RETURNING id", UUID::class.java, ownerId)!!
        jdbc.update("INSERT INTO workout_exercise(workout_id,exercise_id,position,planned_sets) VALUES (?,?,1,3)", currentId, exerciseId)

        mockMvc.perform(get("/workouts/$currentId").with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("PREVIOUS SESSION")))
            .andExpect(content().string(containsString("32.50")))
    }

    @Test
    fun `last exercise page exposes the finish action in its bottom controls`() {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val workoutId = jdbc.queryForObject("INSERT INTO workout(owner_id,title) VALUES (?,'Final set') RETURNING id", UUID::class.java, ownerId)!!
        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Incline Dumbbell Press'", Long::class.java)!!
        jdbc.update("INSERT INTO workout_exercise(workout_id,exercise_id,position,planned_sets) VALUES (?,?,1,1)", workoutId, exerciseId)
        val page = mockMvc.perform(get("/workouts/$workoutId").with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Finish workout ✓")))
            .andReturn().response.contentAsString
        val navigation = Regex("<nav class=\"workout-step-nav\".*?</nav>", RegexOption.DOT_MATCHES_ALL)
            .find(page)?.value.orEmpty()
        assertTrue(navigation.contains("Finish workout ✓"))
        assertFalse(navigation.contains("Next exercise →"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["IN_PROGRESS", "COMPLETED"])
    fun `owner can delete active and completed workouts and dependent records`(workoutStatus: String) {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val workoutId = jdbc.queryForObject(
            "INSERT INTO workout(owner_id,title,status,completed_at) VALUES (?,?,?,CASE WHEN ?='COMPLETED' THEN now() ELSE NULL END) RETURNING id",
            UUID::class.java, ownerId, "Delete $workoutStatus", workoutStatus, workoutStatus
        )!!
        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise LIMIT 1", Long::class.java)!!
        val itemId = jdbc.queryForObject("INSERT INTO workout_exercise(workout_id,exercise_id,position) VALUES (?,?,1) RETURNING id", Long::class.java, workoutId, exerciseId)!!
        jdbc.update("INSERT INTO workout_set(workout_exercise_id,set_number,reps,weight) VALUES (?,1,8,10)", itemId)
        jdbc.update("INSERT INTO workout_event(workout_id,kind) VALUES (?,'PAUSED')", workoutId)
        val destination = if (workoutStatus == "COMPLETED") "history" else "overview"
        val redirect = if (destination == "history") "/history" else "/"

        mockMvc.perform(post("/workouts/$workoutId/delete").param("returnTo", destination)
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl(redirect))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout WHERE id=?", Int::class.java, workoutId))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_exercise WHERE workout_id=?", Int::class.java, workoutId))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_set WHERE workout_exercise_id=?", Int::class.java, itemId))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_event WHERE workout_id=?", Int::class.java, workoutId))
    }

    @Test
    fun `workout delete rejects another users workout and unknown destinations`() {
        val adminId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='integration-admin'", Long::class.java)!!
        val foreignWorkout = jdbc.queryForObject("INSERT INTO workout(owner_id,title) VALUES (?,'Private') RETURNING id", UUID::class.java, adminId)!!
        mockMvc.perform(post("/workouts/$foreignWorkout/delete").param("returnTo", "overview")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().isForbidden)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM workout WHERE id=?", Int::class.java, foreignWorkout))

        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val ownedWorkout = jdbc.queryForObject("INSERT INTO workout(owner_id,title) VALUES (?,'Keep') RETURNING id", UUID::class.java, ownerId)!!
        mockMvc.perform(post("/workouts/$ownedWorkout/delete").param("returnTo", "unknown")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM workout WHERE id=?", Int::class.java, ownedWorkout))
    }

    @Test
    fun `ad hoc workouts add and reorder exercises and enforce pause state transitions`() {
        val redirect = mockMvc.perform(post("/workouts/ad-hoc").with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andReturn().response.redirectedUrl!!
        val workoutId = UUID.fromString(redirect.substringAfterLast('/'))
        val exerciseIds = jdbc.query("SELECT id FROM exercise WHERE name IN ('Incline Dumbbell Press','Dumbbell Bench Press') ORDER BY name", { rs, _ -> rs.getLong(1) })
        exerciseIds.forEach { exerciseId ->
            mockMvc.perform(post("/workouts/$workoutId/exercises").param("exerciseId", exerciseId.toString())
                .with(user("exercise-search-test")).with(csrf())).andExpect(status().is3xxRedirection)
        }
        val itemIds = jdbc.query("SELECT id FROM workout_exercise WHERE workout_id=? ORDER BY position", { rs, _ -> rs.getLong(1) }, workoutId)
        mockMvc.perform(post("/workouts/$workoutId/exercises/${itemIds.first()}/move").param("direction", "down")
            .with(user("exercise-search-test")).with(csrf())).andExpect(status().is3xxRedirection)
        assertEquals(itemIds.last(), jdbc.queryForObject("SELECT id FROM workout_exercise WHERE workout_id=? AND position=1", Long::class.java, workoutId))
        mockMvc.perform(post("/workouts/$workoutId/exercises/${itemIds.first()}/move").param("direction", "sideways")
            .with(user("exercise-search-test")).with(csrf())).andExpect(status().is3xxRedirection)

        mockMvc.perform(post("/workouts/$workoutId/pause").with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals("PAUSED", jdbc.queryForObject("SELECT status FROM workout WHERE id=?", String::class.java, workoutId))
        mockMvc.perform(post("/workouts/$workoutId/pause").with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM workout_event WHERE workout_id=? AND kind='PAUSED'", Int::class.java, workoutId))
        mockMvc.perform(post("/workouts/$workoutId/resume").with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals("IN_PROGRESS", jdbc.queryForObject("SELECT status FROM workout WHERE id=?", String::class.java, workoutId))
        mockMvc.perform(post("/workouts/$workoutId/resume").with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM workout_event WHERE workout_id=? AND kind='RESUMED'", Int::class.java, workoutId))
    }
}
