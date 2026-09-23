package dev.pjarek.gym

import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.AccessDeniedException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Controller
class WebController(
    private val jdbc: JdbcTemplate,
    private val exerciseSearch: ExerciseSearchService,
    private val encoder: PasswordEncoder,
    @Value("\${app.exercise-image-dir:/var/lib/pjarek-gym/exercise-images}") private val imageDir: String
) {
    @ModelAttribute
    fun common(model: Model, @AuthenticationPrincipal user: UserDetails?) {
        if (user != null) {
            model.addAttribute("username", user.username)
            model.addAttribute("isAdmin", user.authorities.any { it.authority == "ROLE_ADMIN" })
            model.addAttribute("weightUnit", jdbc.queryForObject("SELECT weight_unit FROM app_user WHERE username=?", String::class.java, user.username) ?: "KG")
            model.addAttribute("themeMode", jdbc.queryForObject("SELECT theme_mode FROM app_user WHERE username=?", String::class.java, user.username) ?: "SYSTEM")
        }
    }
    private fun userId(user: UserDetails) = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long::class.java, user.username)!!

    @GetMapping("/login") fun login() = "login"

    @RequestMapping("/forbidden")
    @ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
    fun forbidden() = "forbidden"

    @GetMapping("/")
    fun home(@AuthenticationPrincipal user: UserDetails, model: Model): String {
        val id = userId(user)
        model.addAttribute("username", user.username)
        model.addAttribute("routines", jdbc.queryForList("SELECT id,name FROM routine WHERE owner_id=? ORDER BY name", id))
        model.addAttribute("active", jdbc.queryForList("SELECT id,title,started_at,status FROM workout WHERE owner_id=? AND status IN ('IN_PROGRESS','PAUSED') ORDER BY started_at DESC", id))
        model.addAttribute("recent", jdbc.queryForList("SELECT id,title,started_at,completed_at FROM workout WHERE owner_id=? AND status='COMPLETED' ORDER BY completed_at DESC LIMIT 8", id))
        model.addAttribute("stats", jdbc.queryForMap("SELECT count(DISTINCT w.id) sessions, coalesce(sum(s.reps*CASE WHEN s.weight_unit='LB' THEN s.weight*0.45359237 ELSE s.weight END),0) volume FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id LEFT JOIN workout_set s ON s.workout_exercise_id=we.id WHERE w.owner_id=? AND w.status='COMPLETED'", id))
        val trend = jdbc.queryForList("SELECT w.title,w.completed_at,coalesce(sum(s.reps*CASE WHEN s.weight_unit='LB' THEN s.weight*0.45359237 ELSE s.weight END),0) volume FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id LEFT JOIN workout_set s ON s.workout_exercise_id=we.id WHERE w.owner_id=? AND w.status='COMPLETED' GROUP BY w.id ORDER BY w.completed_at DESC LIMIT 8", id).reversed()
        model.addAttribute("volumeTrend", trend)
        model.addAttribute("volumeMax", trend.maxOfOrNull { (it["volume"] as Number).toDouble() }?.takeIf { it > 0 } ?: 1.0)
        return "home"
    }

    @GetMapping("/exercises")
    fun exercises(@RequestParam(defaultValue="") q: String, @RequestHeader(name="HX-Request", required=false) hx: String?, model: Model): String {
        require(q.length <= 100) { "Search terms can be up to 100 characters." }
        val term = "%${q.trim()}%"
        model.addAttribute("q", q)
        model.addAttribute("exercises", jdbc.queryForList("SELECT id,name,category,equipment,primary_muscles,source_id FROM exercise WHERE name ILIKE ? OR primary_muscles ILIKE ? OR equipment ILIKE ? ORDER BY name LIMIT 200", term, term, term))
        return if (hx != null) "fragments/exercises :: rows" else "exercises"
    }

    @GetMapping("/api/exercises/search")
    @ResponseBody
    fun searchExercises(@RequestParam(defaultValue = "") q: String) = exerciseSearch.search(q)

    @GetMapping("/exercise/{id}")
    fun exercise(@PathVariable id: Long, model: Model): String {
        model.addAttribute("exercise", jdbc.queryForMap("SELECT * FROM exercise WHERE id=?", id))
        return "exercise-detail"
    }

    @GetMapping("/exercise-images/{sourceId}/{index}")
    fun image(@PathVariable sourceId: String, @PathVariable index: Int, response: HttpServletResponse) {
        require(sourceId.matches(Regex("[A-Za-z0-9_-]+")) && index in 0..3)
        val base = Path.of(imageDir).toAbsolutePath().normalize()
        Files.createDirectories(base)
        val path = jdbc.query("SELECT image_paths FROM exercise WHERE source_id=?", { rs, _ -> rs.getString(1) }, sourceId).firstOrNull()?.split(',')?.getOrNull(index)
        if (path == null || !path.matches(Regex("[A-Za-z0-9_-]+/[0-3]\\.jpg"))) { response.sendError(404); return }
        val image = base.resolve("exercises/$path").normalize()
        if (!image.startsWith(base)) { response.sendError(404); return }
        if (!Files.exists(image)) { response.sendError(404); return }
        response.contentType = "image/jpeg"
        Files.newInputStream(image).use { it.copyTo(response.outputStream) }
    }

    @GetMapping("/routines")
    fun routines(@AuthenticationPrincipal user: UserDetails, model: Model): String {
        model.addAttribute("routines", jdbc.queryForList("SELECT id,name FROM routine WHERE owner_id=? ORDER BY name", userId(user)))
        return "routines"
    }

    @PostMapping("/routines")
    fun createRoutine(@AuthenticationPrincipal user: UserDetails, @RequestParam(required = false) name: String?, model: Model, response: HttpServletResponse): String {
        val id = userId(user)
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
        val owner = ownerScope(user)
        ownedRoutine(id, owner)
        model.addAttribute("routine", jdbc.queryForMap("SELECT * FROM routine WHERE id=?", id))
        model.addAttribute("days", jdbc.queryForList("SELECT * FROM routine_day WHERE routine_id=? ORDER BY position", id))
        model.addAttribute("items", jdbc.queryForList("SELECT ri.*,e.name exercise_name FROM routine_item ri JOIN routine_day d ON d.id=ri.day_id JOIN exercise e ON e.id=ri.exercise_id WHERE d.routine_id=? ORDER BY d.position,ri.position", id))
        return "routine-detail"
    }

    @PostMapping("/routines/{id}/days")
    fun addDay(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam(defaultValue="") name: String): String {
        ownedRoutine(id, ownerScope(user))
        require(name.isNotBlank() && name.trim().length <= 120) { "Enter a day name up to 120 characters." }
        val pos = jdbc.queryForObject("SELECT coalesce(max(position),0)+1 FROM routine_day WHERE routine_id=?", Int::class.java, id)!!
        jdbc.update("INSERT INTO routine_day(routine_id,name,position) VALUES (?,?,?)", id, name.trim(), pos)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/days/{dayId}/rename")
    fun renameDay(@PathVariable id: UUID, @PathVariable dayId: Long, @AuthenticationPrincipal user: UserDetails,
                  @RequestParam name: String): String {
        ownedRoutine(id, ownerScope(user)); ownedDay(dayId, id)
        require(name.isNotBlank() && name.trim().length <= 120) { "Enter a day name up to 120 characters." }
        jdbc.update("UPDATE routine_day SET name=? WHERE id=? AND routine_id=?", name.trim(), dayId, id)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/days/{dayId}/delete")
    fun deleteDay(@PathVariable id: UUID, @PathVariable dayId: Long, @AuthenticationPrincipal user: UserDetails): String {
        ownedRoutine(id, ownerScope(user)); ownedDay(dayId, id)
        val position = jdbc.queryForObject("SELECT position FROM routine_day WHERE id=? AND routine_id=?", Int::class.java, dayId, id)!!
        jdbc.update("DELETE FROM routine_day WHERE id=? AND routine_id=?", dayId, id)
        jdbc.update("UPDATE routine_day SET position=position-1 WHERE routine_id=? AND position>?", id, position)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/rename")
    fun renameRoutine(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam name: String): String {
        ownedRoutine(id, ownerScope(user)); require(name.isNotBlank() && name.length <= 160)
        jdbc.update("UPDATE routine SET name=? WHERE id=?", name.trim(), id)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/delete")
    fun deleteRoutine(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): String {
        ownedRoutine(id, ownerScope(user)); jdbc.update("DELETE FROM routine WHERE id=?", id)
        return "redirect:/routines"
    }

    @PostMapping("/routines/{id}/items/{itemId}/delete")
    fun deleteRoutineItem(@PathVariable id: UUID, @PathVariable itemId: Long, @AuthenticationPrincipal user: UserDetails): String {
        ownedRoutine(id, ownerScope(user))
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
        @RequestParam reps: String,
        @RequestParam rest: Int
    ): String {
        ownedRoutine(id, ownerScope(user))
        require(sets in 1..20 && rest in 0..900 && reps.isNotBlank() && reps.trim().length <= 40) {
            "Use 1–20 sets, 0–900 seconds rest, and a rep target up to 40 characters."
        }
        require(jdbc.queryForObject("SELECT count(*) FROM exercise WHERE id=?", Int::class.java, exerciseId) == 1) { "Choose an exercise from the search results." }
        val updated = jdbc.update(
            "UPDATE routine_item SET exercise_id=?,planned_sets=?,rep_target=?,rest_seconds=? WHERE id=? AND day_id IN (SELECT id FROM routine_day WHERE routine_id=?)",
            exerciseId, sets, reps.trim(), rest, itemId, id
        )
        require(updated == 1) { "Exercise not found in this routine." }
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/items")
    fun addRoutineItem(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails,
                       @RequestParam dayId: Long, @RequestParam exerciseId: Long,
                       @RequestParam(defaultValue="3") sets: Int, @RequestParam(defaultValue="8-12") reps: String,
                       @RequestParam(defaultValue="90") rest: Int): String {
        ownedRoutine(id, ownerScope(user)); ownedDay(dayId, id)
        require(sets in 1..20 && rest in 0..900 && reps.isNotBlank() && reps.length <= 40) { "Use 1–20 sets, 0–900 seconds rest, and a rep target up to 40 characters." }
        val position = jdbc.queryForObject("SELECT coalesce(max(position),0)+1 FROM routine_item WHERE day_id=?", Int::class.java, dayId)!!
        jdbc.update("INSERT INTO routine_item(day_id,exercise_id,position,planned_sets,rep_target,rest_seconds) VALUES (?,?,?,?,?,?)", dayId, exerciseId, position, sets, reps.trim(), rest)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{routineId}/days/{dayId}/start")
    fun startRoutine(@PathVariable routineId: UUID, @PathVariable dayId: Long, @AuthenticationPrincipal user: UserDetails): String {
        val owner = ownerScope(user); ownedRoutine(routineId, owner); ownedDay(dayId, routineId)
        val routineOwner = jdbc.queryForObject("SELECT owner_id FROM routine WHERE id=?", Long::class.java, routineId)!!
        val title = jdbc.queryForObject("SELECT name FROM routine_day WHERE id=?", String::class.java, dayId)!!
        val workout = jdbc.queryForObject("INSERT INTO workout(owner_id,routine_day_id,title) VALUES (?,?,?) RETURNING id", UUID::class.java, routineOwner, dayId, title)!!
        jdbc.update("""INSERT INTO workout_exercise(workout_id,exercise_id,position,instructions_snapshot,planned_sets,rep_target,rest_seconds)
            SELECT ?,ri.exercise_id,ri.position,e.instructions,ri.planned_sets,ri.rep_target,ri.rest_seconds FROM routine_item ri JOIN exercise e ON e.id=ri.exercise_id WHERE ri.day_id=? ORDER BY ri.position""", workout, dayId)
        return "redirect:/workouts/$workout"
    }

    @PostMapping("/workouts/ad-hoc")
    fun startAdHoc(@AuthenticationPrincipal user: UserDetails): String {
        val owner = userId(user)
        val workout = jdbc.queryForObject("INSERT INTO workout(owner_id,title) VALUES (?,'Ad hoc workout') RETURNING id", UUID::class.java, owner)!!
        return "redirect:/workouts/$workout"
    }

    @GetMapping("/workouts/{id}")
    fun workout(@PathVariable id: UUID, @RequestParam(required = false) exercise: Long?, @AuthenticationPrincipal user: UserDetails, model: Model): String {
        val owner = ownerScope(user)
        ownedWorkout(id, owner)
        val w = jdbc.queryForMap("SELECT * FROM workout WHERE id=?", id)
        model.addAttribute("workout", w)
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
        model.addAttribute("exercises", jdbc.queryForList("SELECT id,name FROM exercise ORDER BY name LIMIT 300"))
        model.addAttribute("sets", if (current == null) emptyList<Any>() else jdbc.queryForList("SELECT s.*,we.id AS item_id FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id WHERE we.id=? ORDER BY s.set_number", current["id"]))
        model.addAttribute("previousSets", if (current == null) emptyList<Any>() else jdbc.queryForList(
            """SELECT s.set_number,s.reps,s.weight,s.weight_unit,w.title,w.completed_at
                FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id
                JOIN workout w ON w.id=we.workout_id
                WHERE w.owner_id=? AND w.status='COMPLETED' AND we.exercise_id=?
                  AND w.id=(SELECT w2.id FROM workout w2 JOIN workout_exercise we2 ON we2.workout_id=w2.id
                    JOIN workout_set s2 ON s2.workout_exercise_id=we2.id
                    WHERE w2.owner_id=? AND w2.status='COMPLETED' AND we2.exercise_id=?
                    ORDER BY w2.completed_at DESC,w2.started_at DESC LIMIT 1)
                ORDER BY s.set_number""",
            userId(user), current["exercise_id"], userId(user), current["exercise_id"]
        ))
        return "workout"
    }

    @PostMapping("/workouts/{id}/exercises")
    fun addWorkoutExercise(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam exerciseId: Long): String {
        ownedWorkout(id, ownerScope(user)); requireActive(id)
        val pos = jdbc.queryForObject("SELECT coalesce(max(position),0)+1 FROM workout_exercise WHERE workout_id=?", Int::class.java, id)!!
        val inserted = jdbc.update("INSERT INTO workout_exercise(workout_id,exercise_id,position,instructions_snapshot) SELECT ?,id,?,instructions FROM exercise WHERE id=?", id, pos, exerciseId)
        require(inserted == 1) { "Choose an exercise from the list." }
        return "redirect:/workouts/$id"
    }

    @PostMapping("/workouts/{id}/exercises/{itemId}/move")
    fun moveWorkoutExercise(@PathVariable id: UUID, @PathVariable itemId: Long, @AuthenticationPrincipal user: UserDetails, @RequestParam direction: String): String {
        ownedWorkout(id, ownerScope(user)); requireActive(id); require(direction in listOf("up", "down"))
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
               @RequestParam reps: Int, @RequestParam weight: BigDecimal, @RequestHeader(name="HX-Request", required=false) hx: String?): String {
        ownedWorkout(id, ownerScope(user)); requireActive(id)
        require(reps in 0..1000 && weight >= BigDecimal.ZERO && weight <= BigDecimal("2000") && weight.remainder(BigDecimal("0.25")).compareTo(BigDecimal.ZERO) == 0) {
            "Enter 0–1,000 reps and a weight from 0–2,000 in 0.25 increments."
        }
        require(jdbc.queryForObject("SELECT count(*) FROM workout_exercise WHERE id=? AND workout_id=?", Int::class.java, itemId, id) == 1)
        val setNumber = jdbc.queryForObject("SELECT coalesce(max(set_number),0)+1 FROM workout_set WHERE workout_exercise_id=?", Int::class.java, itemId)!!
        val unit = jdbc.queryForObject("SELECT weight_unit FROM app_user WHERE id=?", String::class.java, userId(user)) ?: "KG"
        jdbc.update("INSERT INTO workout_set(workout_exercise_id,set_number,reps,weight,weight_unit) VALUES (?,?,?,?,?)", itemId, setNumber, reps, weight, unit)
        return if (hx != null) "redirect:/workouts/$id?exercise=$itemId" else "redirect:/workouts/$id?exercise=$itemId"
    }

    @PostMapping("/workouts/{id}/sets/{setId}/edit")
    fun editWorkoutSet(
        @PathVariable id: UUID,
        @PathVariable setId: Long,
        @AuthenticationPrincipal user: UserDetails,
        @RequestParam reps: Int,
        @RequestParam weight: BigDecimal
    ): String {
        ownedWorkout(id, ownerScope(user)); requireActive(id)
        require(reps in 0..1000 && weight >= BigDecimal.ZERO && weight <= BigDecimal("2000") && weight.remainder(BigDecimal("0.25")).compareTo(BigDecimal.ZERO) == 0) {
            "Enter 0–1,000 reps and a weight from 0–2,000 in 0.25 increments."
        }
        val itemId = jdbc.query(
            "SELECT we.id FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id WHERE s.id=? AND we.workout_id=?",
            { rs, _ -> rs.getLong(1) }, setId, id
        ).firstOrNull() ?: throw IllegalArgumentException("Set not found in this workout.")
        jdbc.update("UPDATE workout_set SET reps=?,weight=? WHERE id=?", reps, weight, setId)
        return "redirect:/workouts/$id?exercise=$itemId"
    }

    @PostMapping("/workouts/{id}/sets/{setId}/delete")
    fun deleteWorkoutSet(
        @PathVariable id: UUID,
        @PathVariable setId: Long,
        @AuthenticationPrincipal user: UserDetails
    ): String {
        ownedWorkout(id, ownerScope(user)); requireActive(id)
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
        ownedWorkout(id, ownerScope(user)); require(jdbc.queryForObject("SELECT status FROM workout WHERE id=?", String::class.java, id) in listOf("IN_PROGRESS", "PAUSED"))
        jdbc.update("UPDATE workout SET status='COMPLETED',completed_at=now() WHERE id=?", id)
        return "redirect:/"
    }

    @PostMapping("/workouts/{id}/pause")
    fun pause(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): String {
        ownedWorkout(id, ownerScope(user)); requireActive(id)
        jdbc.update("UPDATE workout SET status='PAUSED' WHERE id=?", id)
        jdbc.update("INSERT INTO workout_event(workout_id,kind) VALUES (?,'PAUSED')", id)
        return "redirect:/workouts/$id"
    }

    @PostMapping("/workouts/{id}/resume")
    fun resume(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): String {
        ownedWorkout(id, ownerScope(user)); require(jdbc.queryForObject("SELECT status FROM workout WHERE id=?", String::class.java, id) == "PAUSED")
        jdbc.update("UPDATE workout SET status='IN_PROGRESS' WHERE id=?", id)
        jdbc.update("INSERT INTO workout_event(workout_id,kind) VALUES (?,'RESUMED')", id)
        return "redirect:/workouts/$id"
    }

    @PostMapping("/workouts/{id}/delete")
    fun deleteWorkout(
        @PathVariable id: UUID,
        @RequestParam(required = false, defaultValue = "overview") returnTo: String,
        @AuthenticationPrincipal user: UserDetails
    ): String {
        ownedWorkout(id, userId(user))
        jdbc.update("DELETE FROM workout WHERE id=?", id)
        return if (returnTo == "history") "redirect:/history" else "redirect:/"
    }

    @GetMapping("/history")
    fun history(@AuthenticationPrincipal user: UserDetails, model: Model): String {
        val id = userId(user)
        model.addAttribute("history", jdbc.queryForList("SELECT w.id,w.title,w.started_at,w.completed_at,count(s.id) sets,coalesce(sum(s.reps*CASE WHEN s.weight_unit='LB' THEN s.weight*0.45359237 ELSE s.weight END),0) volume FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id LEFT JOIN workout_set s ON s.workout_exercise_id=we.id WHERE w.owner_id=? AND w.status='COMPLETED' GROUP BY w.id ORDER BY w.completed_at DESC", id))
        model.addAttribute("records", jdbc.queryForList("SELECT e.name,s.weight_unit,max(s.weight) max_weight,max(s.reps) max_reps FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id JOIN exercise e ON e.id=we.exercise_id JOIN workout w ON w.id=we.workout_id WHERE w.owner_id=? AND w.status='COMPLETED' GROUP BY e.name,s.weight_unit ORDER BY max_weight DESC LIMIT 12", id))
        return "history"
    }

    @GetMapping("/admin/users")
    fun adminUsers(@AuthenticationPrincipal user: UserDetails, model: Model): String {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
        model.addAttribute("users", jdbc.queryForList("SELECT id,username,role,enabled,created_at FROM app_user ORDER BY created_at"))
        return "admin-users"
    }

    @GetMapping("/admin/users/{id}")
    fun adminUserData(@PathVariable id: Long, @AuthenticationPrincipal user: UserDetails, model: Model): String {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
        model.addAttribute("account", jdbc.queryForMap("SELECT id,username,role,enabled FROM app_user WHERE id=?", id))
        model.addAttribute("routines", jdbc.queryForList("SELECT id,name FROM routine WHERE owner_id=? ORDER BY name", id))
        model.addAttribute("workouts", jdbc.queryForList("SELECT id,title,status,started_at FROM workout WHERE owner_id=? ORDER BY started_at DESC LIMIT 100", id))
        return "admin-user-data"
    }

    @PostMapping("/admin/workouts/{id}/delete")
    fun adminDeleteWorkout(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): String {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
        val ownerId = jdbc.queryForObject("SELECT owner_id FROM workout WHERE id=?", Long::class.java, id) ?: throw IllegalArgumentException("Workout not found")
        jdbc.update("DELETE FROM workout WHERE id=?", id)
        return "redirect:/admin/users/$ownerId"
    }

    @PostMapping("/admin/routines/{id}/delete")
    fun adminDeleteRoutine(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): String {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
        val ownerId = jdbc.queryForObject("SELECT owner_id FROM routine WHERE id=?", Long::class.java, id) ?: throw IllegalArgumentException("Routine not found")
        jdbc.update("DELETE FROM routine WHERE id=?", id)
        return "redirect:/admin/users/$ownerId"
    }

    @PostMapping("/admin/users")
    fun createUser(@AuthenticationPrincipal user: UserDetails, @RequestParam username: String, @RequestParam password: String, @RequestParam(defaultValue="USER") role: String): String {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
        require(username.matches(Regex("[A-Za-z0-9_.-]{3,80}")) && password.length in 12..200 && role in listOf("USER","ADMIN")) { "Enter a valid username, a password of 12–200 characters, and a valid role." }
        jdbc.update("INSERT INTO app_user(username,password_hash,role) VALUES (?,?,?)", username, encoder.encode(password), role)
        return "redirect:/admin/users"
    }

    @PostMapping("/admin/users/{id}/enabled")
    fun toggleUser(@PathVariable id: Long, @AuthenticationPrincipal user: UserDetails, @RequestParam enabled: Boolean): String {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
        require(jdbc.queryForObject("SELECT username FROM app_user WHERE id=?", String::class.java, id) != user.username)
        jdbc.update("UPDATE app_user SET enabled=? WHERE id=?", enabled, id)
        return "redirect:/admin/users"
    }

    @PostMapping("/settings/weight-unit")
    fun updateWeightUnit(@AuthenticationPrincipal user: UserDetails, @RequestParam unit: String): String {
        require(unit in listOf("KG", "LB"))
        jdbc.update("UPDATE app_user SET weight_unit=? WHERE id=?", unit, userId(user))
        return "redirect:/"
    }

    @PostMapping("/settings/theme-mode")
    fun updateThemeMode(@AuthenticationPrincipal user: UserDetails, @RequestParam mode: String): String {
        require(mode in listOf("LIGHT", "DARK", "SYSTEM"))
        jdbc.update("UPDATE app_user SET theme_mode=? WHERE id=?", mode, userId(user))
        return "redirect:/"
    }

    @PostMapping("/settings/password")
    fun changePassword(@AuthenticationPrincipal user: UserDetails, @RequestParam currentPassword: String, @RequestParam newPassword: String): String {
        val hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String::class.java, userId(user))!!
        require(encoder.matches(currentPassword, hash)) { "Current password is not correct" }
        require(newPassword.length in 12..200) { "Password must contain 12–200 characters." }
        jdbc.update("UPDATE app_user SET password_hash=? WHERE username=?", encoder.encode(newPassword), user.username)
        return "redirect:/?passwordChanged"
    }

    @PostMapping("/admin/users/{id}/password")
    fun adminResetPassword(@PathVariable id: Long, @AuthenticationPrincipal user: UserDetails, @RequestParam password: String): String {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
        require(password.length in 12..200) { "Password must contain 12–200 characters." }
        require(jdbc.update("UPDATE app_user SET password_hash=? WHERE id=?", encoder.encode(password), id) == 1) { "User not found" }
        return "redirect:/admin/users/$id"
    }

    private fun ownerScope(user: UserDetails) = if (user.authorities.any { it.authority == "ROLE_ADMIN" }) -1L else userId(user)
    private fun ownedRoutine(id: UUID, owner: Long) {
        val count = if (owner < 0) jdbc.queryForObject("SELECT count(*) FROM routine WHERE id=?", Int::class.java, id)
                    else jdbc.queryForObject("SELECT count(*) FROM routine WHERE id=? AND owner_id=?", Int::class.java, id, owner)
        if (count != 1) throw AccessDeniedException("Routine is not available")
    }
    private fun ownedDay(day: Long, routine: UUID) { if (jdbc.queryForObject("SELECT count(*) FROM routine_day WHERE id=? AND routine_id=?", Int::class.java, day, routine) != 1) throw AccessDeniedException("Routine day is not available") }
    private fun ownedWorkout(id: UUID, owner: Long) {
        val count = if (owner < 0) jdbc.queryForObject("SELECT count(*) FROM workout WHERE id=?", Int::class.java, id)
                    else jdbc.queryForObject("SELECT count(*) FROM workout WHERE id=? AND owner_id=?", Int::class.java, id, owner)
        if (count != 1) throw AccessDeniedException("Workout is not available")
    }
    private fun requireActive(id: UUID) { require(jdbc.queryForObject("SELECT status FROM workout WHERE id=?", String::class.java, id) == "IN_PROGRESS") }
}
