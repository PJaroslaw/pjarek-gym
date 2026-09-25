package com.pjarek.gym

import java.math.BigDecimal
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DashboardController(private val jdbc: JdbcTemplate, private val access: UserAccess) {
    @GetMapping("/")
    fun home(@AuthenticationPrincipal user: UserDetails, model: Model): String {
        val id = access.userId(user)
        model.addAttribute("username", user.username)
        model.addAttribute("routines", jdbc.queryForList("SELECT id,name FROM routine WHERE owner_id=? ORDER BY name", id))
        model.addAttribute("active", jdbc.queryForList("SELECT id,title,started_at,status FROM workout WHERE owner_id=? AND status IN ('IN_PROGRESS','PAUSED') ORDER BY started_at DESC", id))
        val recent = jdbc.queryForList("SELECT id,title,started_at,completed_at,elapsed_seconds FROM workout WHERE owner_id=? AND status='COMPLETED' ORDER BY completed_at DESC LIMIT 8", id)
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
    fun history(@AuthenticationPrincipal user: UserDetails, model: Model): String {
        val id = access.userId(user)
        val history = jdbc.queryForList("SELECT w.id,w.title,w.started_at,w.completed_at,w.elapsed_seconds,count(s.id) sets,coalesce(sum(s.reps*s.weight),0) volume FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id LEFT JOIN workout_set s ON s.workout_exercise_id=we.id WHERE w.owner_id=? AND w.status='COMPLETED' GROUP BY w.id ORDER BY w.completed_at DESC", id)
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
}
