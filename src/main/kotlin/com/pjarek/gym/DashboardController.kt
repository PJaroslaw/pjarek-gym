package com.pjarek.gym

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class DashboardController(private val jdbc: JdbcTemplate, private val access: UserAccess, private val calendar: WorkoutCalendar) {
    @GetMapping("/")
    fun home(@AuthenticationPrincipal user: UserDetails, model: Model,
             @RequestParam(required = false) month: String?,
             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) day: LocalDate?): String {
        val id = access.userId(user)
        val (visibleMonth, selectedDate) = calendarSelection(month, day)
        model.addAttribute("calendar", calendar.view(id, visibleMonth, selectedDate))
        model.addAttribute("username", user.username)
        model.addAttribute("routines", jdbc.queryForList("SELECT id,name FROM routine WHERE owner_id=? ORDER BY name", id))
        model.addAttribute("routineDays", jdbc.queryForList(
            """SELECT d.id,d.routine_id,d.name,d.position,count(ri.id) exercise_count
                FROM routine_day d JOIN routine r ON r.id=d.routine_id
                LEFT JOIN routine_item ri ON ri.day_id=d.id
                WHERE r.owner_id=? GROUP BY d.id,r.name ORDER BY r.name,d.position""",
            id
        ))
        model.addAttribute("active", jdbc.queryForList("SELECT id,title,started_at,status FROM workout WHERE owner_id=? AND status IN ('IN_PROGRESS','PAUSED') ORDER BY started_at DESC", id))
        val recent = if (selectedDate == null) emptyList() else jdbc.queryForList(
            "SELECT id,title,started_at,completed_at,elapsed_seconds FROM workout WHERE owner_id=? AND status='COMPLETED' AND completed_at>=? AND completed_at<? ORDER BY completed_at DESC",
            id, calendar.boundary(selectedDate), calendar.boundary(selectedDate.plusDays(1))
        )
        model.addAttribute("recent", recent.map { it + ("durationLabel" to formatDuration(it["elapsed_seconds"] as Number)) })
        model.addAttribute("stats", jdbc.queryForMap("SELECT count(DISTINCT w.id) sessions, coalesce(sum(s.reps*s.weight),0) volume FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id LEFT JOIN workout_set s ON s.workout_exercise_id=we.id WHERE w.owner_id=? AND w.status='COMPLETED'", id))
        val trend = jdbc.queryForList("SELECT w.title,w.completed_at,coalesce(sum(s.reps*s.weight),0) volume FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id LEFT JOIN workout_set s ON s.workout_exercise_id=we.id WHERE w.owner_id=? AND w.status='COMPLETED' GROUP BY w.id ORDER BY w.completed_at DESC LIMIT 8", id).reversed()
        val volumeTrend = if (trend.isEmpty()) emptyList() else {
            val volumeMax = trend.maxOf { (it["volume"] as Number).toDouble() }.coerceAtLeast(1.0)
            trend.map { row -> row + ("barHeight" to maxOf(6.0, 85 * (row["volume"] as Number).toDouble() / volumeMax)) }
        }
        model.addAttribute("volumeTrend", volumeTrend)
        return "home"
    }

    @GetMapping("/settings")
    fun settings(): String = "settings"

    @GetMapping("/history")
    fun history(@AuthenticationPrincipal user: UserDetails, model: Model,
                @RequestParam(required = false) month: String?,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) day: LocalDate?): String {
        val id = access.userId(user)
        val (visibleMonth, selectedDate) = calendarSelection(month, day)
        model.addAttribute("calendar", calendar.view(id, visibleMonth, selectedDate))
        val history = if (selectedDate == null) emptyList() else jdbc.queryForList(
            "SELECT w.id,w.title,w.started_at,w.completed_at,w.elapsed_seconds,count(s.id) sets,coalesce(sum(s.reps*s.weight),0) volume FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id LEFT JOIN workout_set s ON s.workout_exercise_id=we.id WHERE w.owner_id=? AND w.status='COMPLETED' AND w.completed_at>=? AND w.completed_at<? GROUP BY w.id ORDER BY w.completed_at DESC",
            id, calendar.boundary(selectedDate), calendar.boundary(selectedDate.plusDays(1))
        )
        model.addAttribute("history", history.map { it + ("durationLabel" to formatDuration(it["elapsed_seconds"] as Number)) })
        val weightUnit = jdbc.queryForObject("SELECT weight_unit FROM app_user WHERE id=?", String::class.java, id)!!
        val records = jdbc.queryForList("SELECT e.name,max(s.weight) max_weight,max(s.reps) max_reps FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id JOIN exercise e ON e.id=we.exercise_id JOIN workout w ON w.id=we.workout_id WHERE w.owner_id=? AND w.status='COMPLETED' GROUP BY e.name ORDER BY max_weight DESC LIMIT 12", id)
        model.addAttribute("records", records.map { row ->
            row + ("displayWeight" to WeightUnits.fromKilograms(row["max_weight"] as BigDecimal, weightUnit)) + ("weightUnit" to weightUnit)
        })
        return "history"
    }

    private fun formatDuration(seconds: Number): String {
        val totalSeconds = seconds.toLong()
        val hours = totalSeconds / 3600
        val minutes = totalSeconds % 3600 / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun calendarSelection(month: String?, day: LocalDate?): Pair<YearMonth, LocalDate?> {
        val today = LocalDate.now()
        val visibleMonth = if (month == null) day?.let(YearMonth::from) ?: YearMonth.from(today) else
            runCatching { YearMonth.parse(month) }.getOrElse { throw IllegalArgumentException("Invalid calendar month") }
        require(visibleMonth.year in 1..9999) { "Calendar month is out of range" }
        require(day == null || YearMonth.from(day) == visibleMonth) { "Selected day must be in the visible month" }
        return visibleMonth to (day ?: if (month == null) today else null)
    }
}
