package com.pjarek.gym.service

import com.pjarek.gym.model.InspectedRoutine
import com.pjarek.gym.model.InspectedWorkout
import com.pjarek.gym.model.InspectionPage
import com.pjarek.gym.repository.AdminInspectionRepository
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AdminInspectionService(private val adminInspectionRepository: AdminInspectionRepository, private val accountService: AccountService) {
    fun inspect(id: UUID, routinesPage: Int, sessionsPage: Int): InspectionPage {
        val account = accountService.adminAccount(id)
        val routineCount = adminInspectionRepository.routineCount(id)
        val routinePageCount = ((routineCount + PAGE_SIZE - 1) / PAGE_SIZE).toInt()
        val currentRoutinePage = routinesPage.coerceIn(0, (routinePageCount - 1).coerceAtLeast(0))
        val workoutCount = adminInspectionRepository.workoutCount(id)
        val sessionPageCount = ((workoutCount + PAGE_SIZE - 1) / PAGE_SIZE).toInt()
        val currentSessionPage = sessionsPage.coerceIn(0, (sessionPageCount - 1).coerceAtLeast(0))
        return InspectionPage(
            account, adminInspectionRepository.routines(id, PAGE_SIZE, currentRoutinePage * PAGE_SIZE), routineCount,
            currentRoutinePage, currentRoutinePage > 0, currentRoutinePage + 1 < routinePageCount,
            adminInspectionRepository.workouts(id, PAGE_SIZE, currentSessionPage * PAGE_SIZE), currentSessionPage,
            currentSessionPage > 0, currentSessionPage + 1 < sessionPageCount
        )
    }

    fun routine(id: UUID, routineId: UUID): InspectedRoutine = InspectedRoutine(
        accountService.adminAccount(id), adminInspectionRepository.routine(id, routineId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND),
        adminInspectionRepository.routineDays(routineId), adminInspectionRepository.routineItems(routineId)
    )

    fun workout(id: UUID, workoutId: UUID): InspectedWorkout = InspectedWorkout(
        accountService.adminAccount(id), adminInspectionRepository.workout(id, workoutId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND),
        adminInspectionRepository.workoutExercises(workoutId), adminInspectionRepository.sets(workoutId)
    )

    companion object { private const val PAGE_SIZE = 20 }
}
