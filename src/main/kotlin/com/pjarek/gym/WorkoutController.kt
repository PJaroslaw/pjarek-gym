package com.pjarek.gym

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class WorkoutController(private val jdbc: JdbcTemplate, private val access: UserAccess) {
    @PostMapping("/workouts/ad-hoc")
    fun startAdHoc(@AuthenticationPrincipal user: UserDetails, @RequestParam(required = false) exerciseId: Long?): String {
        val owner = access.userId(user)
        if (exerciseId != null) {
            require(jdbc.queryForObject("SELECT count(*) FROM exercise WHERE id=? AND active=TRUE", Int::class.java, exerciseId) == 1) {
                "Choose an exercise from the library."
            }
        }
        val workout = jdbc.queryForObject("INSERT INTO workout(owner_id,title) VALUES (?,'Ad hoc workout') RETURNING id", UUID::class.java, owner)!!
        if (exerciseId == null) return "redirect:/workouts/$workout"
        val itemId = jdbc.query(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position,instructions_snapshot) SELECT ?,id,1,instructions FROM exercise WHERE id=? AND active=TRUE RETURNING id",
            { rs, _ -> rs.getLong(1) },
            workout,
            exerciseId
        ).first()
        return "redirect:/workouts/$workout?exercise=$itemId"
    }

    @GetMapping("/workouts/{id}")
    fun workout(
        @PathVariable id: UUID,
        @RequestParam(required = false) exercise: Long?,
        @RequestParam(required = false) restSeconds: Int?,
        @RequestParam(required = false) restStartedAt: Long?,
        @AuthenticationPrincipal user: UserDetails,
        model: Model
    ): String {
        val owner = access.ownerScope(user)
        access.ownedWorkout(id, owner)
        model.addAttribute("restRemainingMillis", restRemainingMillis(restSeconds, restStartedAt))
        val w = jdbc.queryForMap(
            """SELECT *,CASE WHEN status='IN_PROGRESS' THEN elapsed_seconds + floor(extract(epoch FROM (now()-active_since)))::INTEGER
                ELSE elapsed_seconds END AS displayed_elapsed_seconds FROM workout WHERE id=?""",
            id
        )
        model.addAttribute("workout", w)
        model.addAttribute("workoutElapsedSeconds", w["displayed_elapsed_seconds"])
        val items = jdbc.queryForList("SELECT we.*,e.name,e.primary_muscles,e.equipment,e.source_id,(SELECT count(*) FROM workout_set s WHERE s.workout_exercise_id=we.id) completed_sets FROM workout_exercise we JOIN exercise e ON e.id=we.exercise_id WHERE we.workout_id=? ORDER BY we.position", id)
        val requestedCurrent = exercise?.let { requested -> items.firstOrNull { (it["id"] as Number).toLong() == requested } }
        require(exercise == null || requestedCurrent != null) { "Exercise not found in this workout." }
        val current = requestedCurrent
            ?: items.firstOrNull {
                (it["planned_sets"] as Number).toInt() == 0 ||
                    (it["completed_sets"] as Number).toInt() < (it["planned_sets"] as Number).toInt()
            }
            ?: items.lastOrNull()
        val currentIndex = current?.let { items.indexOf(it) } ?: -1
        model.addAttribute("currentItem", current)
        model.addAttribute("exerciseNumber", currentIndex + 1)
        model.addAttribute("exerciseCount", items.size)
        model.addAttribute("previousExerciseId", items.getOrNull(currentIndex - 1)?.get("id"))
        model.addAttribute("nextExerciseId", items.getOrNull(currentIndex + 1)?.get("id"))
        model.addAttribute("exercises", jdbc.queryForList("SELECT id,name FROM exercise WHERE active=TRUE ORDER BY name"))
        val storedSets = if (current == null) emptyList() else jdbc.queryForList("SELECT s.*,we.id AS item_id FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id WHERE we.id=? ORDER BY s.set_number", current["id"])
        val previousSets = if (current == null) emptyList() else jdbc.queryForList(
            """SELECT s.set_number,s.reps,s.weight,w.title,w.completed_at
                FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id
                JOIN workout w ON w.id=we.workout_id
                WHERE w.owner_id=? AND w.status='COMPLETED' AND we.exercise_id=?
                  AND w.id=(SELECT w2.id FROM workout w2 JOIN workout_exercise we2 ON we2.workout_id=w2.id
                    JOIN workout_set s2 ON s2.workout_exercise_id=we2.id
                    WHERE w2.owner_id=? AND w2.status='COMPLETED' AND we2.exercise_id=?
                    ORDER BY w2.completed_at DESC,w2.started_at DESC LIMIT 1)
                ORDER BY s.set_number""",
            access.userId(user), current["exercise_id"], access.userId(user), current["exercise_id"]
        )
        val weightUnit = jdbc.queryForObject("SELECT weight_unit FROM app_user WHERE id=?", String::class.java, access.userId(user))!!
        val sets = storedSets.map { set ->
            set + ("displayWeight" to WeightUnits.fromKilograms(set["weight"] as BigDecimal, weightUnit))
        }
        val latestCurrentSet = sets.lastOrNull()
        val latestCurrentWeight = latestCurrentSet?.get("displayWeight") as? BigDecimal
        val latestPreviousSet = previousSets.lastOrNull()
        val previousWeight = (latestPreviousSet?.get("weight") as? BigDecimal)?.let { weight -> WeightUnits.fromKilograms(weight, weightUnit) }
        model.addAttribute("sets", sets)
        model.addAttribute("previousSets", previousSets.map { set ->
            set + ("displayWeight" to WeightUnits.fromKilograms(set["weight"] as BigDecimal, weightUnit))
        })
        model.addAttribute("weightStep", if (weightUnit == "LB") "0.25" else "0.01")
        val defaultReps = current?.let { item ->
            if ((item["planned_sets"] as Number).toInt() > 0) item["max_reps"] else ""
        } ?: ""
        model.addAttribute("defaultReps", defaultReps)
        model.addAttribute("defaultWeight", latestCurrentWeight ?: previousWeight ?: BigDecimal.ZERO)
        return "workout"
    }

    @PostMapping("/workouts/{id}/exercises")
    fun addWorkoutExercise(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam exerciseId: Long): String {
        access.ownedWorkout(id, access.ownerScope(user)); access.requireActive(id)
        val pos = jdbc.queryForObject("SELECT coalesce(max(position),0)+1 FROM workout_exercise WHERE workout_id=?", Int::class.java, id)!!
        val itemId = jdbc.query(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position,instructions_snapshot) SELECT ?,id,?,instructions FROM exercise WHERE id=? AND active=TRUE RETURNING id",
            { rs, _ -> rs.getLong(1) },
            id,
            pos,
            exerciseId
        ).firstOrNull() ?: throw IllegalArgumentException("Choose an exercise from the list.")
        return "redirect:/workouts/$id?exercise=$itemId"
    }

    @PostMapping("/workouts/{id}/exercises/{itemId}/move")
    fun moveWorkoutExercise(@PathVariable id: UUID, @PathVariable itemId: Long, @AuthenticationPrincipal user: UserDetails, @RequestParam direction: String): String {
        access.ownedWorkout(id, access.ownerScope(user)); access.requireActive(id); require(direction in listOf("up", "down"))
        val current = jdbc.queryForObject("SELECT position FROM workout_exercise WHERE id=? AND workout_id=?", Int::class.java, itemId, id) ?: throw IllegalArgumentException("Exercise not found")
        val target = if (direction == "up") current - 1 else current + 1
        val neighbor = jdbc.query("SELECT id FROM workout_exercise WHERE workout_id=? AND position=?", { rs, _ -> rs.getLong(1) }, id, target).firstOrNull()
        if (neighbor != null) {
            jdbc.update("UPDATE workout_exercise SET position=-1 WHERE id=?", itemId)
            jdbc.update("UPDATE workout_exercise SET position=? WHERE id=?", current, neighbor)
            jdbc.update("UPDATE workout_exercise SET position=? WHERE id=?", target, itemId)
        }
        return "redirect:/workouts/$id"
    }

    @PostMapping("/workouts/{id}/sets")
    fun logSet(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam itemId: Long,
               @RequestParam reps: Int, @RequestParam weight: BigDecimal): String {
        access.ownedWorkout(id, access.ownerScope(user)); access.requireActive(id)
        val unit = jdbc.queryForObject("SELECT weight_unit FROM app_user WHERE id=?", String::class.java, access.userId(user))!!
        require(reps in 0..1000 && WeightUnits.isValidInput(weight, unit)) {
            "Enter 0–1,000 reps and a valid weight from 0–2,000 in $unit."
        }
        require(jdbc.queryForObject("SELECT count(*) FROM workout_exercise WHERE id=? AND workout_id=?", Int::class.java, itemId, id) == 1)
        val setNumber = jdbc.queryForObject("SELECT coalesce(max(set_number),0)+1 FROM workout_set WHERE workout_exercise_id=?", Int::class.java, itemId)!!
        val restSeconds = jdbc.queryForObject("SELECT rest_seconds FROM workout_exercise WHERE id=? AND workout_id=?", Int::class.java, itemId, id)!!
        jdbc.queryForObject(
            "INSERT INTO workout_set(workout_exercise_id,set_number,reps,weight) VALUES (?,?,?,?) RETURNING id",
            Long::class.java, itemId, setNumber, reps, WeightUnits.toKilograms(weight, unit)
        )
        val restStartedAt = Instant.now().toEpochMilli()
        return "redirect:/workouts/$id?exercise=$itemId&restSeconds=$restSeconds&restStartedAt=$restStartedAt"
    }

    @PostMapping("/workouts/{id}/sets/{setId}/edit")
    fun editWorkoutSet(
        @PathVariable id: UUID,
        @PathVariable setId: Long,
        @AuthenticationPrincipal user: UserDetails,
        @RequestParam reps: Int,
        @RequestParam weight: BigDecimal
    ): String {
        access.ownedWorkout(id, access.ownerScope(user)); access.requireActive(id)
        val unit = jdbc.queryForObject("SELECT weight_unit FROM app_user WHERE id=?", String::class.java, access.userId(user))!!
        require(reps in 0..1000 && WeightUnits.isValidInput(weight, unit)) {
            "Enter 0–1,000 reps and a valid weight from 0–2,000 in $unit."
        }
        val itemId = jdbc.query(
            "SELECT we.id FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id WHERE s.id=? AND we.workout_id=?",
            { rs, _ -> rs.getLong(1) }, setId, id
        ).firstOrNull() ?: throw IllegalArgumentException("Set not found in this workout.")
        jdbc.update("UPDATE workout_set SET reps=?,weight=? WHERE id=?", reps, WeightUnits.toKilograms(weight, unit), setId)
        return "redirect:/workouts/$id?exercise=$itemId"
    }

    @PostMapping("/workouts/{id}/sets/{setId}/delete")
    fun deleteWorkoutSet(
        @PathVariable id: UUID,
        @PathVariable setId: Long,
        @AuthenticationPrincipal user: UserDetails
    ): String {
        access.ownedWorkout(id, access.ownerScope(user)); access.requireActive(id)
        val itemId = jdbc.query(
            "SELECT we.id FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id WHERE s.id=? AND we.workout_id=?",
            { rs, _ -> rs.getLong(1) }, setId, id
        ).firstOrNull() ?: throw IllegalArgumentException("Set not found in this workout.")
        jdbc.update("DELETE FROM workout_set WHERE id=?", setId)
        jdbc.update(
            "WITH ordered AS (SELECT id,row_number() OVER (ORDER BY set_number,completed_at,id) AS new_number FROM workout_set WHERE workout_exercise_id=?) UPDATE workout_set s SET set_number=o.new_number FROM ordered o WHERE s.id=o.id",
            itemId
        )
        return "redirect:/workouts/$id?exercise=$itemId"
    }

    @PostMapping("/workouts/{id}/finish")
    fun finishWorkout(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): String {
        access.ownedWorkout(id, access.ownerScope(user))
        val updated = jdbc.update(
            """UPDATE workout SET elapsed_seconds=elapsed_seconds+CASE WHEN status='IN_PROGRESS'
                THEN floor(extract(epoch FROM (now()-active_since)))::INTEGER ELSE 0 END,
                active_since=NULL,status='COMPLETED',completed_at=now()
                WHERE id=? AND status IN ('IN_PROGRESS','PAUSED')""",
            id
        )
        require(updated == 1) { "This workout is already finished." }
        return "redirect:/"
    }

    @PostMapping("/workouts/{id}/pause")
    fun pause(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): String {
        access.ownedWorkout(id, access.ownerScope(user))
        val updated = jdbc.update(
            """UPDATE workout SET elapsed_seconds=elapsed_seconds+floor(extract(epoch FROM (now()-active_since)))::INTEGER,
                active_since=NULL,status='PAUSED' WHERE id=? AND status='IN_PROGRESS'""",
            id
        )
        require(updated == 1) { "This workout is not running." }
        jdbc.update("INSERT INTO workout_event(workout_id,kind) VALUES (?,'PAUSED')", id)
        return "redirect:/workouts/$id"
    }

    @PostMapping("/workouts/{id}/resume")
    fun resume(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): String {
        access.ownedWorkout(id, access.ownerScope(user))
        val updated = jdbc.update("UPDATE workout SET status='IN_PROGRESS',active_since=now() WHERE id=? AND status='PAUSED'", id)
        require(updated == 1) { "This workout is not paused." }
        jdbc.update("INSERT INTO workout_event(workout_id,kind) VALUES (?,'RESUMED')", id)
        return "redirect:/workouts/$id"
    }

    @PostMapping("/workouts/{id}/delete")
    fun deleteWorkout(
        @PathVariable id: UUID,
        @RequestParam returnTo: String,
        @AuthenticationPrincipal user: UserDetails
    ): String {
        val destination = when (returnTo) {
            "overview" -> "redirect:/"
            "history" -> "redirect:/history"
            else -> throw IllegalArgumentException("Unknown workout deletion destination")
        }
        access.ownedWorkout(id, access.userId(user))
        jdbc.update("DELETE FROM workout WHERE id=?", id)
        return destination
    }

    private fun restRemainingMillis(restSeconds: Int?, restStartedAt: Long?): Long {
        if (restSeconds == null || restSeconds !in 1..900 || restStartedAt == null || restStartedAt <= 0) return 0
        val durationMillis = restSeconds * 1000L
        val now = Instant.now().toEpochMilli()
        val elapsedMillis = if (restStartedAt < now) (now - restStartedAt).coerceAtMost(durationMillis) else 0
        return durationMillis - elapsedMillis
    }
}
