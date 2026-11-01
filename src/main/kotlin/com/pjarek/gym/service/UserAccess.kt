package com.pjarek.gym.service

import com.pjarek.gym.repository.AccountRepository
import com.pjarek.gym.repository.RoutineRepository
import com.pjarek.gym.repository.WorkoutRepository
import java.util.UUID
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service

@Service
class UserAccess(
    private val accountRepository: AccountRepository,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository
) {
    fun userId(user: UserDetails): UUID = accountRepository.findByUsername(user.username)?.id
        ?: throw AccessDeniedException("User is not available")

    fun ownedRoutine(id: UUID, owner: UUID) {
        if (!routineRepository.owned(id, owner)) throw AccessDeniedException("Routine is not available")
    }

    fun ownedDay(day: UUID, routine: UUID) {
        if (!routineRepository.dayBelongs(day, routine)) throw AccessDeniedException("Routine day is not available")
    }

    fun ownedWorkout(id: UUID, owner: UUID) {
        if (!workoutRepository.owned(id, owner)) throw AccessDeniedException("Workout is not available")
    }

    fun requireActive(id: UUID) {
        require(workoutRepository.status(id) == "IN_PROGRESS")
    }
}
