package com.pjarek.gym.service

import com.pjarek.gym.model.RoutineDetail
import com.pjarek.gym.repository.ExerciseRepository
import com.pjarek.gym.repository.RoutineRepository
import com.pjarek.gym.repository.WorkoutRepository
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RoutineService(
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val workouts: WorkoutRepository,
    private val access: UserAccess
) {
    fun list(owner: UUID): List<Map<String, Any?>> = routines.list(owner)

    fun create(owner: UUID, name: String?): UUID {
        val routineName = name?.trim().orEmpty()
        require(routineName.isNotBlank() && routineName.length <= 160) { "Enter a name for your routine." }
        return routines.create(owner, routineName)
    }

    fun detail(id: UUID, owner: UUID): RoutineDetail {
        access.ownedRoutine(id, owner)
        return RoutineDetail(routines.find(id), routines.days(id), routines.items(id))
    }

    fun addDay(id: UUID, owner: UUID, name: String) {
        access.ownedRoutine(id, owner)
        validateDayName(name)
        routines.addDay(id, name.trim())
    }

    fun renameDay(id: UUID, dayId: UUID, owner: UUID, name: String) {
        access.ownedRoutine(id, owner); access.ownedDay(dayId, id)
        validateDayName(name)
        routines.renameDay(id, dayId, name.trim())
    }

    @Transactional
    fun deleteDay(id: UUID, dayId: UUID, owner: UUID) {
        access.ownedRoutine(id, owner); access.ownedDay(dayId, id)
        routines.deleteDay(id, dayId)
    }

    fun rename(id: UUID, owner: UUID, name: String) {
        access.ownedRoutine(id, owner)
        require(name.isNotBlank() && name.length <= 160)
        routines.rename(id, name.trim())
    }

    fun delete(id: UUID, owner: UUID) {
        access.ownedRoutine(id, owner)
        routines.delete(id)
    }

    fun deleteItem(id: UUID, itemId: UUID, owner: UUID) {
        access.ownedRoutine(id, owner)
        routines.deleteItem(id, itemId)
    }

    fun editItem(id: UUID, itemId: UUID, owner: UUID, exerciseId: Long, sets: Int, minReps: Int, maxReps: Int, rest: Int) {
        access.ownedRoutine(id, owner)
        validateExercisePlan(sets, minReps, maxReps, rest)
        require(exercises.active(exerciseId)) { "Choose an exercise from the search results." }
        require(routines.editItem(id, itemId, exerciseId, sets, minReps, maxReps, rest) == 1) { "Exercise not found in this routine." }
    }

    fun addItem(id: UUID, dayId: UUID, owner: UUID, exerciseId: Long, sets: Int, minReps: Int, maxReps: Int, rest: Int) {
        access.ownedRoutine(id, owner); access.ownedDay(dayId, id)
        validateExercisePlan(sets, minReps, maxReps, rest)
        require(exercises.active(exerciseId)) { "Choose an exercise from the search results." }
        routines.addItem(dayId, exerciseId, sets, minReps, maxReps, rest)
    }

    @Transactional
    fun start(id: UUID, dayId: UUID, owner: UUID): UUID {
        access.ownedRoutine(id, owner); access.ownedDay(dayId, id)
        return workouts.createFromRoutine(owner, dayId, routines.dayName(dayId))
    }

    private fun validateDayName(name: String) {
        require(name.isNotBlank() && name.trim().length <= 120) { "Enter a day name up to 120 characters." }
    }

    private fun validateExercisePlan(sets: Int, minReps: Int, maxReps: Int, rest: Int) {
        require(sets in 1..20 && minReps in 1..1000 && maxReps in minReps..1000 && rest in 0..900) {
            "Use 1–20 sets, a rep range from 1–1,000 with the minimum no greater than the maximum, and 0–900 seconds rest."
        }
    }
}
