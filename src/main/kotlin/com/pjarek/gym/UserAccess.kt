package com.pjarek.gym

import java.util.UUID
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service

@Service
class UserAccess(
    private val accounts: AccountRepository,
    private val routines: RoutineRepository,
    private val workouts: WorkoutRepository
) {
    fun userId(user: UserDetails): UUID = accounts.findByUsername(user.username)?.id
        ?: throw AccessDeniedException("User is not available")

    fun ownedRoutine(id: UUID, owner: UUID) {
        if (!routines.owned(id, owner)) throw AccessDeniedException("Routine is not available")
    }

    fun ownedDay(day: UUID, routine: UUID) {
        if (!routines.dayBelongs(day, routine)) throw AccessDeniedException("Routine day is not available")
    }

    fun ownedWorkout(id: UUID, owner: UUID) {
        if (!workouts.owned(id, owner)) throw AccessDeniedException("Workout is not available")
    }

    fun requireActive(id: UUID) {
        require(workouts.status(id) == "IN_PROGRESS")
    }
}
