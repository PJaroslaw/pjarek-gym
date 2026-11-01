package com.pjarek.gym.service

import com.pjarek.gym.model.RoutineDetail
import com.pjarek.gym.model.RoutineDraft
import com.pjarek.gym.repository.ExerciseRepository
import com.pjarek.gym.repository.RoutineRepository
import com.pjarek.gym.repository.WorkoutRepository
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RoutineService(
    private val routineRepository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val userAccess: UserAccess
) {
    fun list(owner: UUID): List<Map<String, Any?>> = routineRepository.list(owner)

    fun create(owner: UUID, name: String?): UUID {
        val routineName = name?.trim().orEmpty()
        require(routineName.isNotBlank() && routineName.length <= 160) { "Enter a name for your routine." }
        return routineRepository.create(owner, routineName)
    }

    @Transactional
    fun saveDraft(owner: UUID, draft: RoutineDraft): UUID {
        val routineName = draft.name.trim()
        require(routineName.isNotBlank() && routineName.length <= 160) { "Enter a valid routine name." }
        require(draft.days.isNotEmpty()) { "Add at least one day to the routine." }
        val routineId = routineRepository.create(owner, routineName)
        draft.days.forEach { day ->
            val dayId = routineRepository.addDay(routineId, day.name.trim())
            day.exercises.forEach { exercise ->
                require(exerciseRepository.active(exercise.exerciseId)) { "Choose each exercise from the active exercise library." }
                routineRepository.addItem(
                    dayId, exercise.exerciseId, exercise.sets, exercise.minReps, exercise.maxReps, exercise.restSeconds
                )
            }
        }
        return routineId
    }

    fun detail(id: UUID, owner: UUID): RoutineDetail {
        userAccess.ownedRoutine(id, owner)
        return RoutineDetail(routineRepository.find(id), routineRepository.days(id), routineRepository.items(id))
    }

    fun addDay(id: UUID, owner: UUID, name: String) {
        userAccess.ownedRoutine(id, owner)
        validateDayName(name)
        routineRepository.addDay(id, name.trim())
    }

    fun renameDay(id: UUID, dayId: UUID, owner: UUID, name: String) {
        userAccess.ownedRoutine(id, owner); userAccess.ownedDay(dayId, id)
        validateDayName(name)
        routineRepository.renameDay(id, dayId, name.trim())
    }

    @Transactional
    fun deleteDay(id: UUID, dayId: UUID, owner: UUID) {
        userAccess.ownedRoutine(id, owner); userAccess.ownedDay(dayId, id)
        routineRepository.deleteDay(id, dayId)
    }

    fun rename(id: UUID, owner: UUID, name: String) {
        userAccess.ownedRoutine(id, owner)
        require(name.isNotBlank() && name.length <= 160)
        routineRepository.rename(id, name.trim())
    }

    fun delete(id: UUID, owner: UUID) {
        userAccess.ownedRoutine(id, owner)
        routineRepository.delete(id)
    }

    fun deleteItem(id: UUID, itemId: UUID, owner: UUID) {
        userAccess.ownedRoutine(id, owner)
        routineRepository.deleteItem(id, itemId)
    }

    fun editItem(id: UUID, itemId: UUID, owner: UUID, exerciseId: Long, sets: Int, minReps: Int, maxReps: Int, rest: Int) {
        userAccess.ownedRoutine(id, owner)
        validateExercisePlan(sets, minReps, maxReps, rest)
        require(exerciseRepository.active(exerciseId)) { "Choose an exercise from the search results." }
        require(routineRepository.editItem(id, itemId, exerciseId, sets, minReps, maxReps, rest) == 1) { "Exercise not found in this routine." }
    }

    fun addItem(id: UUID, dayId: UUID, owner: UUID, exerciseId: Long, sets: Int, minReps: Int, maxReps: Int, rest: Int) {
        userAccess.ownedRoutine(id, owner); userAccess.ownedDay(dayId, id)
        validateExercisePlan(sets, minReps, maxReps, rest)
        require(exerciseRepository.active(exerciseId)) { "Choose an exercise from the search results." }
        routineRepository.addItem(dayId, exerciseId, sets, minReps, maxReps, rest)
    }

    @Transactional
    fun start(id: UUID, dayId: UUID, owner: UUID): UUID {
        userAccess.ownedRoutine(id, owner); userAccess.ownedDay(dayId, id)
        return workoutRepository.createFromRoutine(owner, dayId, routineRepository.dayName(dayId))
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
