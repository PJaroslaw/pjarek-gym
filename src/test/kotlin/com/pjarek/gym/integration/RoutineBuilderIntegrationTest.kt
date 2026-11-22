package com.pjarek.gym.integration

import com.pjarek.gym.model.RoutineDraft
import com.pjarek.gym.model.RoutineDraftDay
import com.pjarek.gym.model.RoutineDraftExercise
import com.pjarek.gym.model.RoutineDraftRequest
import com.pjarek.gym.repository.ExerciseRepository
import com.pjarek.gym.service.RoutineDraftGenerator
import com.pjarek.gym.service.RoutineDraftSession
import com.pjarek.gym.service.RoutineSourceFile
import com.pjarek.gym.service.RoutineService
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@Import(RoutineBuilderTestConfiguration::class)
@TestPropertySource(properties = ["app.ai.api-key=test-api-key"])
class RoutineBuilderIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var routineService: RoutineService

    @Test
    fun `AI generated draft can be refined and saved only after user confirmation`() {
        val owner = testUserId()
        val session = MockHttpSession()
        val exerciseIds = activeExerciseIds()

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/routine-builder/generate")
                .session(session)
                .param("goal", "Build strength")
                .param("daysPerWeek", "3")
                .param("experience", "beginner")
                .param("sessionMinutes", "45")
                .param("equipment", "Dumbbells")
                .param("limitations", "")
                .with(user("exercise-search-test"))
                .with(csrf())
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Generated routine")))
        assertEquals(0, routineCount(owner))

        mockMvc.perform(
            post("/routine-builder/refine")
                .session(session)
                .with(user("exercise-search-test"))
                .with(csrf())
                .param("name", "Generated routine")
                .param("summary", "Generated summary")
                .param("changeRequest", "Make the sessions shorter")
                .param("days[0].name", "Day one")
                .param("days[0].exercises[0].exerciseName", "Catalog exercise")
                .param("days[0].exercises[0].exerciseId", exerciseIds[0].toString())
                .param("days[0].exercises[0].sets", "3")
                .param("days[0].exercises[0].minReps", "8")
                .param("days[0].exercises[0].maxReps", "12")
                .param("days[0].exercises[0].restSeconds", "90")
                .param("days[0].exercises[1].exerciseName", "Catalog exercise two")
                .param("days[0].exercises[1].exerciseId", exerciseIds[1].toString())
                .param("days[0].exercises[1].sets", "3")
                .param("days[0].exercises[1].minReps", "8")
                .param("days[0].exercises[1].maxReps", "12")
                .param("days[0].exercises[1].restSeconds", "90")
                .param("days[0].exercises[2].exerciseName", "Catalog exercise three")
                .param("days[0].exercises[2].exerciseId", exerciseIds[2].toString())
                .param("days[0].exercises[2].sets", "3")
                .param("days[0].exercises[2].minReps", "8")
                .param("days[0].exercises[2].maxReps", "12")
                .param("days[0].exercises[2].restSeconds", "90")
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Updated after refinement")))
        assertEquals(0, routineCount(owner))

        mockMvc.perform(
            post("/routine-builder/save")
                .session(session)
                .with(user("exercise-search-test"))
                .with(csrf())
                .param("name", "Generated routine")
                .param("summary", "Updated after refinement")
                .param("days[0].name", "Day one")
                .param("days[0].exercises[0].exerciseName", "Catalog exercise")
                .param("days[0].exercises[0].exerciseId", activeExerciseId().toString())
                .param("days[0].exercises[0].sets", "3")
                .param("days[0].exercises[0].minReps", "8")
                .param("days[0].exercises[0].maxReps", "12")
                .param("days[0].exercises[0].restSeconds", "90")
        )
            .andExpect(status().is3xxRedirection)
        assertEquals(1, routineCount(owner))
    }

    @Test
    fun `routine draft stays out of the database until the user saves it`() {
        val owner = testUserId()
        val exerciseId = activeExerciseId()
        val session = sessionWithDraft(owner, exerciseId)

        mockMvc.perform(get("/routine-builder").session(session).with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("EDITABLE DRAFT")))
        assertEquals(0, routineCount(owner))

        mockMvc.perform(
            post("/routine-builder/save")
                .session(session)
                .with(user("exercise-search-test"))
                .with(csrf())
                .param("name", "Three day strength")
                .param("summary", "A balanced strength plan")
                .param("days[0].name", "Full body")
                .param("days[0].exercises[0].exerciseName", "Catalog exercise")
                .param("days[0].exercises[0].exerciseId", exerciseId.toString())
                .param("days[0].exercises[0].sets", "3")
                .param("days[0].exercises[0].minReps", "6")
                .param("days[0].exercises[0].maxReps", "8")
                .param("days[0].exercises[0].restSeconds", "90")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrlPattern("/routines/*"))

        assertEquals(1, routineCount(owner))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM routine_day WHERE routine_id IN (SELECT id FROM routine WHERE owner_id=?)", Int::class.java, owner))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM routine_item ri JOIN routine_day rd ON rd.id=ri.day_id JOIN routine r ON r.id=rd.routine_id WHERE r.owner_id=?", Int::class.java, owner))
        assertNull(session.getAttribute("routine-builder-draft"))
    }

    @Test
    fun `draft with an exercise outside the active catalog is rejected without saving`() {
        val owner = testUserId()
        val session = sessionWithDraft(owner, Long.MAX_VALUE)

        mockMvc.perform(
            post("/routine-builder/save")
                .session(session)
                .with(user("exercise-search-test"))
                .with(csrf())
                .param("name", "Invalid plan")
                .param("summary", "")
                .param("days[0].name", "Day one")
                .param("days[0].exercises[0].exerciseName", "Not in catalog")
                .param("days[0].exercises[0].exerciseId", Long.MAX_VALUE.toString())
                .param("days[0].exercises[0].sets", "3")
                .param("days[0].exercises[0].minReps", "8")
                .param("days[0].exercises[0].maxReps", "12")
                .param("days[0].exercises[0].restSeconds", "90")
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("active exercise library")))

        assertEquals(0, routineCount(owner))
    }

    @Test
    fun `routine save rolls back all rows if a later exercise fails validation`() {
        val owner = testUserId()
        val draft = RoutineDraft(
            "Atomic routine",
            "",
            mutableListOf(
                RoutineDraftDay(
                    "Day one",
                    mutableListOf(
                        RoutineDraftExercise(activeExerciseId(), "Valid exercise"),
                        RoutineDraftExercise(Long.MAX_VALUE, "Missing exercise")
                    )
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) { routineService.saveDraft(owner, draft) }
        assertEquals(0, routineCount(owner))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM routine_day", Int::class.java))
    }

    @Test
    fun `routine import rejects files without an accepted PDF or image signature`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/routine-builder/generate")
                .file(MockMultipartFile("sourceFile", "routine.png", "image/png", "not an image".toByteArray()))
                .param("daysPerWeek", "3")
                .param("experience", "beginner")
                .param("sessionMinutes", "45")
                .with(user("exercise-search-test"))
                .with(csrf())
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Choose a PDF, JPEG, PNG, or WebP routine image")))
        assertEquals(0, routineCount(testUserId()))
    }

    private fun sessionWithDraft(owner: UUID, exerciseId: Long): MockHttpSession = MockHttpSession().apply {
        setAttribute(
            "routine-builder-draft",
            RoutineDraftSession(
                owner,
                RoutineDraft(
                    name = "Three day strength",
                    summary = "A balanced strength plan",
                    days = mutableListOf(
                        RoutineDraftDay(
                            "Full body",
                            mutableListOf(RoutineDraftExercise(exerciseId, "Catalog exercise", 3, 6, 8, 90))
                        )
                    )
                )
            )
        )
    }

    private fun testUserId(): UUID = jdbc.queryForObject(
        "SELECT id FROM app_user WHERE username='exercise-search-test'", UUID::class.java
    )!!

    private fun activeExerciseId(): Long = jdbc.queryForObject(
        "SELECT id FROM exercise WHERE active=TRUE ORDER BY id LIMIT 1", Long::class.java
    )!!

    private fun activeExerciseIds(): List<Long> = jdbc.queryForList(
        "SELECT id FROM exercise WHERE active=TRUE ORDER BY id LIMIT 3", Long::class.java
    ).filterNotNull()

    private fun routineCount(owner: UUID): Int = jdbc.queryForObject(
        "SELECT count(*) FROM routine WHERE owner_id=?", Int::class.java, owner
    )!!
}

@TestConfiguration(proxyBeanMethods = false)
class RoutineBuilderTestConfiguration {
    @Bean
    @Primary
    fun routineDraftGenerator(): RoutineDraftGenerator = object : RoutineDraftGenerator {
        override fun create(
            request: RoutineDraftRequest,
            catalog: List<Map<String, Any?>>,
            sourceFile: RoutineSourceFile?
        ): RoutineDraft {
            val exercises = catalog.take(3)
            return RoutineDraft(
                "Generated routine",
                "Generated summary",
                (1..request.daysPerWeek).map { dayNumber ->
                    RoutineDraftDay(
                        "Day $dayNumber",
                        exercises.map { exercise ->
                            RoutineDraftExercise((exercise["id"] as Number).toLong(), exercise["name"].toString())
                        }.toMutableList()
                    )
                }.toMutableList()
            )
        }

        override fun refine(draft: RoutineDraft, instructions: String, catalog: List<Map<String, Any?>>): RoutineDraft =
            draft.copy(summary = "Updated after refinement")
    }
}
