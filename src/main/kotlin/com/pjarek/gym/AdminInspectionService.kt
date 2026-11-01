package com.pjarek.gym

import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

data class InspectionPage(
    val account: AdminAccount,
    val routines: List<Map<String, Any?>>,
    val routineCount: Long,
    val routinesPage: Int,
    val hasPreviousRoutinePage: Boolean,
    val hasNextRoutinePage: Boolean,
    val workouts: List<Map<String, Any?>>,
    val sessionsPage: Int,
    val hasPreviousSessionPage: Boolean,
    val hasNextSessionPage: Boolean
)

data class InspectedRoutine(val account: AdminAccount, val routine: Map<String, Any?>,
                            val days: List<Map<String, Any?>>, val items: List<Map<String, Any?>>)

data class InspectedWorkout(val account: AdminAccount, val workout: Map<String, Any?>,
                            val exercises: List<Map<String, Any?>>, val sets: List<Map<String, Any?>>)

@Service
class AdminInspectionService(private val repository: AdminInspectionRepository, private val accounts: AccountService) {
    fun inspect(id: UUID, routinesPage: Int, sessionsPage: Int): InspectionPage {
        val account = accounts.adminAccount(id)
        val routineCount = repository.routineCount(id)
        val routinePageCount = ((routineCount + PAGE_SIZE - 1) / PAGE_SIZE).toInt()
        val currentRoutinePage = routinesPage.coerceIn(0, (routinePageCount - 1).coerceAtLeast(0))
        val workoutCount = repository.workoutCount(id)
        val sessionPageCount = ((workoutCount + PAGE_SIZE - 1) / PAGE_SIZE).toInt()
        val currentSessionPage = sessionsPage.coerceIn(0, (sessionPageCount - 1).coerceAtLeast(0))
        return InspectionPage(
            account, repository.routines(id, PAGE_SIZE, currentRoutinePage * PAGE_SIZE), routineCount,
            currentRoutinePage, currentRoutinePage > 0, currentRoutinePage + 1 < routinePageCount,
            repository.workouts(id, PAGE_SIZE, currentSessionPage * PAGE_SIZE), currentSessionPage,
            currentSessionPage > 0, currentSessionPage + 1 < sessionPageCount
        )
    }

    fun routine(id: UUID, routineId: UUID): InspectedRoutine = InspectedRoutine(
        accounts.adminAccount(id), repository.routine(id, routineId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND),
        repository.routineDays(routineId), repository.routineItems(routineId)
    )

    fun workout(id: UUID, workoutId: UUID): InspectedWorkout = InspectedWorkout(
        accounts.adminAccount(id), repository.workout(id, workoutId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND),
        repository.workoutExercises(workoutId), repository.sets(workoutId)
    )

    companion object { private const val PAGE_SIZE = 20 }
}
