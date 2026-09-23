package dev.pjarek.gym

import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.hamcrest.Matchers.containsString
import java.math.BigDecimal
import java.util.UUID
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(properties = ["app.admin.username=integration-admin", "app.admin.password=IntegrationTestPass123!"])
@AutoConfigureMockMvc
class ExerciseSearchIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun createTestUser() {
        jdbc.update(
            "INSERT INTO app_user(username,password_hash,role) VALUES ('exercise-search-test','unused','USER') ON CONFLICT (username) DO NOTHING"
        )
    }

    @AfterEach
    fun removeTestUser() {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)
        if (ownerId != null) {
            jdbc.update("DELETE FROM workout WHERE owner_id=?", ownerId)
            jdbc.update("DELETE FROM routine WHERE owner_id=?", ownerId)
        }
        jdbc.update("DELETE FROM app_user WHERE username='exercise-search-test'")
    }

    @Test
    fun `search finds an exercise outside the first 300 alphabetic entries`() {
        mockMvc.perform(get("/api/exercises/search")
            .param("q", "Incline Dumbbell Press")
            .with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].name", hasItem("Incline Dumbbell Press")))
    }

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

        mockMvc.perform(post("/routines/$routineId/items/$itemId/edit")
            .param("exerciseId", replacementExerciseId.toString()).param("sets", "5").param("reps", "6-8").param("rest", "150")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/routines/$routineId"))

        val item = jdbc.queryForMap("SELECT exercise_id,planned_sets,rep_target,rest_seconds FROM routine_item WHERE id=?", itemId)
        org.junit.jupiter.api.Assertions.assertEquals(replacementExerciseId, (item["exercise_id"] as Number).toLong())
        org.junit.jupiter.api.Assertions.assertEquals(5, item["planned_sets"])
        org.junit.jupiter.api.Assertions.assertEquals("6-8", item["rep_target"])
        org.junit.jupiter.api.Assertions.assertEquals(150, item["rest_seconds"])
    }

    @Test
    fun `workout step shows most recent completed sets and bottom finish action`() {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Incline Dumbbell Press'", Long::class.java)!!
        val previousWorkoutId = jdbc.queryForObject(
            "INSERT INTO workout(owner_id,title,status,completed_at) VALUES (?,'Previous push','COMPLETED',now()) RETURNING id", UUID::class.java, ownerId
        )!!
        val previousItemId = jdbc.queryForObject(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position,instructions_snapshot) SELECT ?,id,1,instructions FROM exercise WHERE id=? RETURNING id",
            Long::class.java, previousWorkoutId, exerciseId
        )!!
        jdbc.update("INSERT INTO workout_set(workout_exercise_id,set_number,reps,weight,weight_unit) VALUES (?,1,8,42.5,'KG')", previousItemId)
        val currentWorkoutId = jdbc.queryForObject("INSERT INTO workout(owner_id,title) VALUES (?,'Current push') RETURNING id", UUID::class.java, ownerId)!!
        jdbc.update(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position,instructions_snapshot,planned_sets,rep_target) SELECT ?,id,1,instructions,3,'8-12' FROM exercise WHERE id=?",
            currentWorkoutId, exerciseId
        )

        val page = mockMvc.perform(get("/workouts/$currentWorkoutId").with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("PREVIOUS SESSION")))
            .andExpect(content().string(containsString("Previous push")))
            .andExpect(content().string(containsString("8 reps")))
            .andExpect(content().string(containsString("42.50 KG")))
            .andExpect(content().string(containsString("Weight (")))
            .andExpect(content().string(containsString("Finish workout ✓")))
            .andReturn().response.contentAsString
        org.junit.jupiter.api.Assertions.assertTrue(page.indexOf("Finish workout ✓") < page.lastIndexOf("Finish workout ✓"))
    }

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
        org.junit.jupiter.api.Assertions.assertEquals(10, edited["reps"])
        org.junit.jupiter.api.Assertions.assertEquals(BigDecimal("25.50"), edited["weight"])

        mockMvc.perform(post("/workouts/$workoutId/sets/$secondSetId/delete")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_set WHERE id=?", Int::class.java, secondSetId))
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT set_number FROM workout_set WHERE id=?", Int::class.java, firstSetId))

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
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM workout_set WHERE id=?", Int::class.java, otherSetId))
        jdbc.update("DELETE FROM workout WHERE id=?", otherWorkoutId)
    }

    @Test
    fun `routine workout uses a uuid and guides sets then exercises before finishing`() {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val routineId = jdbc.queryForObject(
            "INSERT INTO routine(owner_id,name) VALUES (?,?) RETURNING id", UUID::class.java, ownerId, "Start day test"
        )!!
        val dayId = jdbc.queryForObject(
            "INSERT INTO routine_day(routine_id,name,position) VALUES (?,?,1) RETURNING id", Long::class.java, routineId, "Push"
        )!!
        val exerciseIds = listOf("Incline Dumbbell Press", "Dumbbell Bench Press").map { name ->
            jdbc.queryForObject("SELECT id FROM exercise WHERE name=?", Long::class.java, name)!!
        }
        exerciseIds.forEachIndexed { index, exerciseId ->
            jdbc.update(
                "INSERT INTO routine_item(day_id,exercise_id,position,planned_sets,rep_target,rest_seconds) VALUES (?,?,?,4,'8-12',90)",
                dayId, exerciseId, index + 1
            )
        }

        val workoutLocation = mockMvc.perform(post("/routines/$routineId/days/$dayId/start")
            .with(user("exercise-search-test"))
            .with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andReturn().response.redirectedUrl!!
        assertTrue(workoutLocation.matches(Regex("/workouts/[0-9a-fA-F-]{36}")))
        val workoutId = UUID.fromString(workoutLocation.substringAfterLast('/'))
        val itemIds = jdbc.query(
            "SELECT id FROM workout_exercise WHERE workout_id=? ORDER BY position",
            { rs, _ -> rs.getLong(1) }, workoutId
        )

        val firstExercisePage = mockMvc.perform(get(workoutLocation).with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("EXERCISE 1 OF 2")))
            .andExpect(content().string(containsString("Incline Dumbbell Press")))
            .andReturn().response.contentAsString
        val currentStep = Regex("<article class=\"panel workout-step\".*?</article>", RegexOption.DOT_MATCHES_ALL)
            .find(firstExercisePage)?.value.orEmpty()
        assertTrue(currentStep.contains("Incline Dumbbell Press"))
        assertTrue(!currentStep.contains("Dumbbell Bench Press"))

        val setLocation = mockMvc.perform(post("/workouts/$workoutId/sets")
            .param("itemId", itemIds.first().toString())
            .param("reps", "8")
            .param("weight", "10")
            .with(user("exercise-search-test"))
            .with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andReturn().response.redirectedUrl!!
        mockMvc.perform(get(setLocation).with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("SET 2 OF 4")))

        val secondExerciseLocation = "$workoutLocation?exercise=${itemIds.last()}"
        val secondExercisePage = mockMvc.perform(get(secondExerciseLocation).with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("EXERCISE 2 OF 2")))
            .andExpect(content().string(containsString("Dumbbell Bench Press")))
            .andReturn().response.contentAsString
        val secondStep = Regex("<article class=\"panel workout-step\".*?</article>", RegexOption.DOT_MATCHES_ALL)
            .find(secondExercisePage)?.value.orEmpty()
        assertTrue(secondStep.contains("Dumbbell Bench Press"))
        assertTrue(!secondStep.contains("Incline Dumbbell Press"))

        mockMvc.perform(post("/workouts/$workoutId/finish")
            .with(user("exercise-search-test"))
            .with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/"))
        mockMvc.perform(get("/").with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("data-stat-sessions=\"1\"")))
            .andExpect(content().string(containsString("Push")))
            .andExpect(content().string(containsString("Delete this completed workout")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["IN_PROGRESS", "COMPLETED"])
    fun `users can delete their active and completed workouts with all dependent data`(statusValue: String) {
        val ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", Long::class.java)!!
        val workoutId = jdbc.queryForObject(
            "INSERT INTO workout(owner_id,title,status,completed_at) VALUES (?,?,?,CASE WHEN ?='COMPLETED' THEN now() ELSE NULL END) RETURNING id",
            UUID::class.java, ownerId, "Delete $statusValue", statusValue, statusValue
        )!!
        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise WHERE name='Incline Dumbbell Press'", Long::class.java)!!
        val workoutExerciseId = jdbc.queryForObject(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position,instructions_snapshot) SELECT ?,id,1,instructions FROM exercise WHERE id=? RETURNING id",
            Long::class.java, workoutId, exerciseId
        )!!
        jdbc.update("INSERT INTO workout_set(workout_exercise_id,set_number,reps,weight,weight_unit) VALUES (?,1,8,10,'KG')", workoutExerciseId)
        jdbc.update("INSERT INTO workout_event(workout_id,kind) VALUES (?,'PAUSED')", workoutId)

        mockMvc.perform(post("/workouts/$workoutId/delete")
            .param("returnTo", if (statusValue == "COMPLETED") "history" else "overview")
            .with(user("exercise-search-test"))
            .with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(if (statusValue == "COMPLETED") "/history" else "/"))

        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout WHERE id=?", Int::class.java, workoutId))
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_exercise WHERE workout_id=?", Int::class.java, workoutId))
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_set WHERE workout_exercise_id=?", Int::class.java, workoutExerciseId))
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_event WHERE workout_id=?", Int::class.java, workoutId))
    }

    @Test
    fun `users cannot delete another user's workout`() {
        val otherOwnerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='integration-admin'", Long::class.java)!!
        val workoutId = jdbc.queryForObject(
            "INSERT INTO workout(owner_id,title) VALUES (?,'Another user workout') RETURNING id", UUID::class.java, otherOwnerId
        )!!

        mockMvc.perform(post("/workouts/$workoutId/delete")
            .with(user("exercise-search-test"))
            .with(csrf()))
            .andExpect(status().isForbidden)
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM workout WHERE id=?", Int::class.java, workoutId))
        jdbc.update("DELETE FROM workout WHERE id=?", workoutId)
    }

    @Test
    fun `search requires at least two characters`() {
        mockMvc.perform(get("/api/exercises/search")
            .param("q", "I")
            .with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:18-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
