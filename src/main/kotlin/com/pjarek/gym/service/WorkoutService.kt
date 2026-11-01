package com.pjarek.gym.service

import com.pjarek.gym.model.LoggedSet
import com.pjarek.gym.model.WeightUnits
import com.pjarek.gym.model.WorkoutPage
import com.pjarek.gym.repository.ExerciseRepository
import com.pjarek.gym.repository.WorkoutRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkoutService(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val accountService: AccountService,
    private val userAccess: UserAccess
) {
    @Transactional
    fun startAdHoc(owner: UUID, exerciseId: Long?): Pair<UUID, UUID?> {
        if (exerciseId != null) require(exerciseRepository.active(exerciseId)) { "Choose an exercise from the library." }
        val workout = workoutRepository.createAdHoc(owner)
        return workout to exerciseId?.let {
            workoutRepository.addInitialExercise(workout, it) ?: throw IllegalArgumentException("Choose an exercise from the library.")
        }
    }

    fun page(id: UUID, owner: UUID, exercise: UUID?, restSeconds: Int?, restStartedAt: Long?): WorkoutPage {
        userAccess.ownedWorkout(id, owner)
        val workout = workoutRepository.find(id)
        val items = workoutRepository.items(id)
        val unit = accountService.account(owner).weightUnit
        val completed = workout["status"] == "COMPLETED"
        val sessionSets = if (completed) workoutRepository.sessionSets(id).map { it.withDisplayWeight(unit) } else emptyList()
        val requestedCurrent = exercise?.let { requested -> items.firstOrNull { it["id"] == requested } }
        require(exercise == null || requestedCurrent != null) { "Exercise not found in this workout." }
        val current = requestedCurrent
            ?: items.firstOrNull {
                (it["planned_sets"] as Number).toInt() == 0 ||
                    (it["completed_sets"] as Number).toInt() < (it["planned_sets"] as Number).toInt()
            }
            ?: items.lastOrNull()
        val currentIndex = current?.let { items.indexOf(it) } ?: -1
        val storedSets = current?.let { workoutRepository.storedSets(it["id"] as UUID) }.orEmpty()
        val previousSets = current?.let { workoutRepository.previousSets(owner, it["exercise_id"] as Long) }.orEmpty()
        val sets = storedSets.map { it.withDisplayWeight(unit) }
        val previousDisplaySets = previousSets.map { it.withDisplayWeight(unit) }
        val latestCurrentWeight = sets.lastOrNull()?.get("displayWeight") as? BigDecimal
        val previousWeight = previousDisplaySets.lastOrNull()?.get("displayWeight") as? BigDecimal
        val defaultReps = current?.let { if ((it["planned_sets"] as Number).toInt() > 0) it["max_reps"] ?: "" else "" } ?: ""
        return WorkoutPage(
            workout, restRemainingMillis(restSeconds, restStartedAt), items,
            if (completed) workoutRepository.sessionVolume(id) else null, sessionSets,
            current, currentIndex + 1, items.getOrNull(currentIndex - 1)?.get("id") as? UUID,
            items.getOrNull(currentIndex + 1)?.get("id") as? UUID, exerciseRepository.activeNames(),
            sets, previousDisplaySets, if (unit == "LB") "0.25" else "0.01", defaultReps,
            latestCurrentWeight ?: previousWeight ?: BigDecimal.ZERO
        )
    }

    fun addExercise(id: UUID, owner: UUID, exerciseId: Long): UUID {
        userAccess.ownedWorkout(id, owner); userAccess.requireActive(id)
        return workoutRepository.addExercise(id, exerciseId) ?: throw IllegalArgumentException("Choose an exercise from the list.")
    }

    @Transactional
    fun logSet(id: UUID, owner: UUID, itemId: UUID, reps: Int, weight: BigDecimal): LoggedSet {
        userAccess.ownedWorkout(id, owner); userAccess.requireActive(id)
        val unit = accountService.account(owner).weightUnit
        validateSet(reps, weight, unit)
        require(workoutRepository.containsItem(id, itemId))
        val restSeconds = workoutRepository.restSeconds(id, itemId)
        workoutRepository.insertSet(itemId, reps, WeightUnits.toKilograms(weight, unit))
        return LoggedSet(itemId, restSeconds, Instant.now().toEpochMilli())
    }

    fun editSet(id: UUID, owner: UUID, setId: UUID, reps: Int, weight: BigDecimal): UUID {
        userAccess.ownedWorkout(id, owner); userAccess.requireActive(id)
        val unit = accountService.account(owner).weightUnit
        validateSet(reps, weight, unit)
        val itemId = workoutRepository.setItem(id, setId) ?: throw IllegalArgumentException("Set not found in this workout.")
        workoutRepository.editSet(setId, reps, WeightUnits.toKilograms(weight, unit))
        return itemId
    }

    @Transactional
    fun deleteSet(id: UUID, owner: UUID, setId: UUID): UUID {
        userAccess.ownedWorkout(id, owner); userAccess.requireActive(id)
        val itemId = workoutRepository.setItem(id, setId) ?: throw IllegalArgumentException("Set not found in this workout.")
        workoutRepository.deleteSet(setId, itemId)
        return itemId
    }

    fun finish(id: UUID, owner: UUID) {
        userAccess.ownedWorkout(id, owner)
        require(workoutRepository.finish(id) == 1) { "This workout is already finished." }
    }

    @Transactional
    fun pause(id: UUID, owner: UUID) {
        userAccess.ownedWorkout(id, owner)
        require(workoutRepository.pause(id) == 1) { "This workout is not running." }
        workoutRepository.event(id, "PAUSED")
    }

    @Transactional
    fun resume(id: UUID, owner: UUID) {
        userAccess.ownedWorkout(id, owner)
        require(workoutRepository.resume(id) == 1) { "This workout is not paused." }
        workoutRepository.event(id, "RESUMED")
    }

    fun delete(id: UUID, owner: UUID) {
        userAccess.ownedWorkout(id, owner)
        workoutRepository.delete(id)
    }

    private fun validateSet(reps: Int, weight: BigDecimal, unit: String) {
        require(reps in 0..1000 && WeightUnits.isValidInput(weight, unit)) {
            "Enter 0–1,000 reps and a valid weight from 0–2,000 in $unit."
        }
    }

    private fun restRemainingMillis(restSeconds: Int?, restStartedAt: Long?): Long {
        if (restSeconds == null || restSeconds !in 1..900 || restStartedAt == null || restStartedAt <= 0) return 0
        val durationMillis = restSeconds * 1000L
        val now = Instant.now().toEpochMilli()
        val elapsedMillis = if (restStartedAt < now) (now - restStartedAt).coerceAtMost(durationMillis) else 0
        return durationMillis - elapsedMillis
    }

    private fun Map<String, Any?>.withDisplayWeight(unit: String): Map<String, Any?> =
        this + ("displayWeight" to WeightUnits.fromKilograms(this["weight"] as BigDecimal, unit))
}
