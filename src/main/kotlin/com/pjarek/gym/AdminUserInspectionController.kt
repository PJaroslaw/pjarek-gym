package com.pjarek.gym

import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

@Controller
class AdminUserInspectionController(private val jdbc: JdbcTemplate) {
    @GetMapping("/admin/users/{id}/inspect")
    fun inspectUser(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") routinesPage: Int,
        @RequestParam(defaultValue = "0") sessionsPage: Int,
        @AuthenticationPrincipal user: UserDetails,
        model: Model
    ): String {
        requireAdmin(user)
        model.addAttribute("account", findAccount(id))
        val routineCount = jdbc.queryForObject("SELECT count(*) FROM routine WHERE owner_id=?", Long::class.java, id) ?: 0L
        model.addAttribute("routineCount", routineCount)
        val routinePageCount = ((routineCount + PAGE_SIZE - 1) / PAGE_SIZE).toInt()
        val currentRoutinePage = routinesPage.coerceIn(0, (routinePageCount - 1).coerceAtLeast(0))
        model.addAttribute("routines", jdbc.queryForList(
            """SELECT r.id,r.name,count(DISTINCT d.id) day_count,count(ri.id) exercise_count
                FROM routine r LEFT JOIN routine_day d ON d.routine_id=r.id
                LEFT JOIN routine_item ri ON ri.day_id=d.id WHERE r.owner_id=?
                GROUP BY r.id ORDER BY r.name,r.id LIMIT ? OFFSET ?""",
            id, PAGE_SIZE, currentRoutinePage * PAGE_SIZE
        ))
        model.addAttribute("routinesPage", currentRoutinePage)
        model.addAttribute("hasPreviousRoutinePage", currentRoutinePage > 0)
        model.addAttribute("hasNextRoutinePage", currentRoutinePage + 1 < routinePageCount)

        val workoutCount = jdbc.queryForObject("SELECT count(*) FROM workout WHERE owner_id=?", Long::class.java, id) ?: 0L
        val pageCount = ((workoutCount + PAGE_SIZE - 1) / PAGE_SIZE).toInt()
        val currentPage = sessionsPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val offset = currentPage * PAGE_SIZE
        model.addAttribute("workouts", jdbc.queryForList(
            """SELECT w.id,w.title,w.status,w.started_at,w.completed_at,w.elapsed_seconds,
                count(DISTINCT we.id) exercise_count,count(s.id) set_count
                FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id
                LEFT JOIN workout_set s ON s.workout_exercise_id=we.id WHERE w.owner_id=?
                GROUP BY w.id ORDER BY w.started_at DESC,w.id DESC LIMIT ? OFFSET ?""",
            id, PAGE_SIZE, offset
        ))
        model.addAttribute("sessionsPage", currentPage)
        model.addAttribute("hasPreviousSessionPage", currentPage > 0)
        model.addAttribute("hasNextSessionPage", currentPage + 1 < pageCount)
        return "admin-user-inspect"
    }

    @GetMapping("/admin/users/{id}/inspect/routines/{routineId}")
    fun inspectRoutine(
        @PathVariable id: UUID,
        @PathVariable routineId: UUID,
        @AuthenticationPrincipal user: UserDetails,
        model: Model
    ): String {
        requireAdmin(user)
        model.addAttribute("account", findAccount(id))
        model.addAttribute("routine", jdbc.queryForList(
            "SELECT id,name FROM routine WHERE id=? AND owner_id=?", routineId, id
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND))
        model.addAttribute("days", jdbc.queryForList(
            "SELECT id,name,position FROM routine_day WHERE routine_id=? ORDER BY position", routineId
        ))
        model.addAttribute("items", jdbc.queryForList(
            """SELECT ri.id,ri.day_id,e.name exercise_name,ri.planned_sets,ri.min_reps,ri.max_reps,ri.rest_seconds
                FROM routine_item ri JOIN routine_day d ON d.id=ri.day_id
                JOIN exercise e ON e.id=ri.exercise_id WHERE d.routine_id=? ORDER BY d.position,ri.position""",
            routineId
        ))
        return "admin-user-routine-inspect"
    }

    @GetMapping("/admin/users/{id}/inspect/workouts/{workoutId}")
    fun inspectWorkout(
        @PathVariable id: UUID,
        @PathVariable workoutId: UUID,
        @AuthenticationPrincipal user: UserDetails,
        model: Model
    ): String {
        requireAdmin(user)
        model.addAttribute("account", findAccount(id))
        model.addAttribute("workout", jdbc.queryForList(
            "SELECT id,title,status,started_at,completed_at,elapsed_seconds FROM workout WHERE id=? AND owner_id=?",
            workoutId, id
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND))
        model.addAttribute("workoutExercises", jdbc.queryForList(
            """SELECT we.id,we.position,e.name exercise_name,we.planned_sets,we.min_reps,we.max_reps
                FROM workout_exercise we JOIN exercise e ON e.id=we.exercise_id
                WHERE we.workout_id=? ORDER BY we.position""",
            workoutId
        ))
        model.addAttribute("sets", jdbc.queryForList(
            """SELECT s.id,s.workout_exercise_id,s.set_number,s.reps,s.weight FROM workout_set s
                JOIN workout_exercise we ON we.id=s.workout_exercise_id
                WHERE we.workout_id=? ORDER BY we.position,s.set_number""",
            workoutId
        ))
        return "admin-user-workout-inspect"
    }

    private fun requireAdmin(user: UserDetails) {
        if (user.authorities.none { it.authority == "ROLE_ADMIN" }) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
    }

    private fun findAccount(id: UUID): Map<String, Any?> = jdbc.queryForList(
        "SELECT id,username,role FROM app_user WHERE id=?", id
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    companion object {
        private const val PAGE_SIZE = 20
    }
}
