package com.pjarek.gym

import jakarta.servlet.http.HttpServletResponse
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
class RoutineController(private val jdbc: JdbcTemplate, private val access: UserAccess) {
    @GetMapping("/routines")
    fun routines(@AuthenticationPrincipal user: UserDetails, model: Model): String {
        model.addAttribute("routines", jdbc.queryForList("SELECT id,name FROM routine WHERE owner_id=? ORDER BY name", access.userId(user)))
        return "routines"
    }

    @PostMapping("/routines")
    fun createRoutine(@AuthenticationPrincipal user: UserDetails, @RequestParam(required = false) name: String?, model: Model, response: HttpServletResponse): String {
        val id = access.userId(user)
        val routineName = name?.trim().orEmpty()
        if (routineName.isBlank() || routineName.length > 160) {
            model.addAttribute("routines", jdbc.queryForList("SELECT id,name FROM routine WHERE owner_id=? ORDER BY name", id))
            model.addAttribute("formError", when {
                routineName.isBlank() -> "Enter a name for your routine."
                else -> "Routine names can be up to 160 characters."
            })
            model.addAttribute("routineName", name.orEmpty())
            response.status = HttpServletResponse.SC_BAD_REQUEST
            return "routines"
        }
        val routineId = jdbc.queryForObject("INSERT INTO routine(owner_id,name) VALUES (?,?) RETURNING id", UUID::class.java, id, routineName)!!
        return "redirect:/routines/$routineId"
    }

    @GetMapping("/routines/{id}")
    fun routine(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, model: Model): String {
        val owner = access.ownerScope(user)
        access.ownedRoutine(id, owner)
        model.addAttribute("routine", jdbc.queryForMap("SELECT * FROM routine WHERE id=?", id))
        model.addAttribute("days", jdbc.queryForList("SELECT * FROM routine_day WHERE routine_id=? ORDER BY position", id))
        model.addAttribute("items", jdbc.queryForList("SELECT ri.*,e.name exercise_name FROM routine_item ri JOIN routine_day d ON d.id=ri.day_id JOIN exercise e ON e.id=ri.exercise_id WHERE d.routine_id=? ORDER BY d.position,ri.position", id))
        return "routine-detail"
    }

    @PostMapping("/routines/{id}/days")
    fun addDay(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam(defaultValue="") name: String): String {
        access.ownedRoutine(id, access.ownerScope(user))
        require(name.isNotBlank() && name.trim().length <= 120) { "Enter a day name up to 120 characters." }
        val pos = jdbc.queryForObject("SELECT coalesce(max(position),0)+1 FROM routine_day WHERE routine_id=?", Int::class.java, id)!!
        jdbc.update("INSERT INTO routine_day(routine_id,name,position) VALUES (?,?,?)", id, name.trim(), pos)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/days/{dayId}/rename")
    fun renameDay(@PathVariable id: UUID, @PathVariable dayId: Long, @AuthenticationPrincipal user: UserDetails,
                  @RequestParam name: String): String {
        access.ownedRoutine(id, access.ownerScope(user)); access.ownedDay(dayId, id)
        require(name.isNotBlank() && name.trim().length <= 120) { "Enter a day name up to 120 characters." }
        jdbc.update("UPDATE routine_day SET name=? WHERE id=? AND routine_id=?", name.trim(), dayId, id)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/days/{dayId}/delete")
    fun deleteDay(@PathVariable id: UUID, @PathVariable dayId: Long, @AuthenticationPrincipal user: UserDetails): String {
        access.ownedRoutine(id, access.ownerScope(user)); access.ownedDay(dayId, id)
        val position = jdbc.queryForObject("SELECT position FROM routine_day WHERE id=? AND routine_id=?", Int::class.java, dayId, id)!!
        jdbc.update("DELETE FROM routine_day WHERE id=? AND routine_id=?", dayId, id)
        jdbc.update("UPDATE routine_day SET position=position-1 WHERE routine_id=? AND position>?", id, position)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/rename")
    fun renameRoutine(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam name: String): String {
        access.ownedRoutine(id, access.ownerScope(user)); require(name.isNotBlank() && name.length <= 160)
        jdbc.update("UPDATE routine SET name=? WHERE id=?", name.trim(), id)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/delete")
    fun deleteRoutine(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): String {
        access.ownedRoutine(id, access.ownerScope(user)); jdbc.update("DELETE FROM routine WHERE id=?", id)
        return "redirect:/routines"
    }

    @PostMapping("/routines/{id}/items/{itemId}/delete")
    fun deleteRoutineItem(@PathVariable id: UUID, @PathVariable itemId: Long, @AuthenticationPrincipal user: UserDetails): String {
        access.ownedRoutine(id, access.ownerScope(user))
        jdbc.update("DELETE FROM routine_item WHERE id=? AND day_id IN (SELECT id FROM routine_day WHERE routine_id=?)", itemId, id)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/items/{itemId}/edit")
    fun editRoutineItem(
        @PathVariable id: UUID,
        @PathVariable itemId: Long,
        @AuthenticationPrincipal user: UserDetails,
        @RequestParam exerciseId: Long,
        @RequestParam sets: Int,
        @RequestParam minReps: Int,
        @RequestParam maxReps: Int,
        @RequestParam rest: Int
    ): String {
        access.ownedRoutine(id, access.ownerScope(user))
        validateExercisePlan(sets, minReps, maxReps, rest)
        require(jdbc.queryForObject("SELECT count(*) FROM exercise WHERE id=? AND active=TRUE", Int::class.java, exerciseId) == 1) { "Choose an exercise from the search results." }
        val updated = jdbc.update(
            "UPDATE routine_item SET exercise_id=?,planned_sets=?,min_reps=?,max_reps=?,rest_seconds=? WHERE id=? AND day_id IN (SELECT id FROM routine_day WHERE routine_id=?)",
            exerciseId, sets, minReps, maxReps, rest, itemId, id
        )
        require(updated == 1) { "Exercise not found in this routine." }
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/items")
    fun addRoutineItem(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails,
                       @RequestParam dayId: Long, @RequestParam exerciseId: Long,
                       @RequestParam(defaultValue="3") sets: Int,
                       @RequestParam(defaultValue="8") minReps: Int,
                       @RequestParam(defaultValue="12") maxReps: Int,
                       @RequestParam(defaultValue="90") rest: Int): String {
        access.ownedRoutine(id, access.ownerScope(user)); access.ownedDay(dayId, id)
        validateExercisePlan(sets, minReps, maxReps, rest)
        require(jdbc.queryForObject("SELECT count(*) FROM exercise WHERE id=? AND active=TRUE", Int::class.java, exerciseId) == 1) { "Choose an exercise from the search results." }
        val position = jdbc.queryForObject("SELECT coalesce(max(position),0)+1 FROM routine_item WHERE day_id=?", Int::class.java, dayId)!!
        jdbc.update("INSERT INTO routine_item(day_id,exercise_id,position,planned_sets,min_reps,max_reps,rest_seconds) VALUES (?,?,?,?,?,?,?)", dayId, exerciseId, position, sets, minReps, maxReps, rest)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{routineId}/days/{dayId}/start")
    fun startRoutine(@PathVariable routineId: UUID, @PathVariable dayId: Long, @AuthenticationPrincipal user: UserDetails): String {
        val owner = access.ownerScope(user); access.ownedRoutine(routineId, owner); access.ownedDay(dayId, routineId)
        val routineOwner = jdbc.queryForObject("SELECT owner_id FROM routine WHERE id=?", Long::class.java, routineId)!!
        val title = jdbc.queryForObject("SELECT name FROM routine_day WHERE id=?", String::class.java, dayId)!!
        val workout = jdbc.queryForObject("INSERT INTO workout(owner_id,routine_day_id,title) VALUES (?,?,?) RETURNING id", UUID::class.java, routineOwner, dayId, title)!!
        jdbc.update("""INSERT INTO workout_exercise(workout_id,exercise_id,position,instructions_snapshot,planned_sets,min_reps,max_reps,rest_seconds)
            SELECT ?,ri.exercise_id,ri.position,e.instructions,ri.planned_sets,ri.min_reps,ri.max_reps,ri.rest_seconds FROM routine_item ri JOIN exercise e ON e.id=ri.exercise_id WHERE ri.day_id=? ORDER BY ri.position""", workout, dayId)
        return "redirect:/workouts/$workout"
    }

    private fun validateExercisePlan(sets: Int, minReps: Int, maxReps: Int, rest: Int) {
        require(sets in 1..20 && minReps in 1..1000 && maxReps in minReps..1000 && rest in 0..900) {
            "Use 1–20 sets, a rep range from 1–1,000 with the minimum no greater than the maximum, and 0–900 seconds rest."
        }
    }
}
