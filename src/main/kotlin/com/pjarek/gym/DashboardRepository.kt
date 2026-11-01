package com.pjarek.gym

import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

data class RoutineSummary(val id: UUID, val name: String)
data class RoutineDaySummary(val id: UUID, val routineId: UUID, val name: String, val position: Int, val exerciseCount: Int)
data class ActiveWorkout(val id: UUID, val title: String, val startedAt: Timestamp, val status: String)
data class CompletedWorkout(
    val id: UUID,
    val title: String,
    val startedAt: Timestamp,
    val completedAt: Timestamp,
    val elapsedSeconds: Int,
    val sets: Int = 0,
    val volume: BigDecimal = BigDecimal.ZERO,
    val durationLabel: String = ""
)
data class WorkoutStats(val sessions: Long, val volume: BigDecimal)
data class VolumeTrend(val title: String, val completedAt: Timestamp, val volume: BigDecimal, val barHeight: Double = 0.0)
data class ExerciseRecord(val name: String, val maxReps: Int, val maxWeight: BigDecimal, val displayWeight: BigDecimal, val weightUnit: String)

@Repository
class DashboardRepository(private val jdbc: NamedParameterJdbcTemplate) {
    fun routines(owner: UUID): List<RoutineSummary> = jdbc.query(
        "SELECT id,name FROM routine WHERE owner_id=:owner ORDER BY name",
        mapOf("owner" to owner), RowMapper { rs, _ -> RoutineSummary(rs.uuid("id"), rs.getString("name")) }
    )

    fun routineDays(owner: UUID): List<RoutineDaySummary> = jdbc.query(
        """SELECT d.id,d.routine_id,d.name,d.position,count(ri.id) exercise_count
            FROM routine_day d JOIN routine r ON r.id=d.routine_id
            LEFT JOIN routine_item ri ON ri.day_id=d.id
            WHERE r.owner_id=:owner GROUP BY d.id,r.name ORDER BY r.name,d.position""",
        mapOf("owner" to owner), RowMapper { rs, _ -> RoutineDaySummary(rs.uuid("id"), rs.uuid("routine_id"), rs.getString("name"), rs.getInt("position"), rs.getInt("exercise_count")) }
    )

    fun activeWorkouts(owner: UUID): List<ActiveWorkout> = jdbc.query(
        "SELECT id,title,started_at,status FROM workout WHERE owner_id=:owner AND status IN ('IN_PROGRESS','PAUSED') ORDER BY started_at DESC",
        mapOf("owner" to owner), RowMapper { rs, _ -> ActiveWorkout(rs.uuid("id"), rs.getString("title"), rs.getTimestamp("started_at"), rs.getString("status")) }
    )

    fun completedWorkouts(owner: UUID, start: Timestamp, end: Timestamp): List<CompletedWorkout> = jdbc.query(
        "SELECT id,title,started_at,completed_at,elapsed_seconds FROM workout WHERE owner_id=:owner AND status='COMPLETED' AND completed_at>=:start AND completed_at<:end ORDER BY completed_at DESC",
        mapOf("owner" to owner, "start" to start, "end" to end), completedWorkoutMapper
    )

    fun history(owner: UUID, start: Timestamp, end: Timestamp): List<CompletedWorkout> = jdbc.query(
        """SELECT w.id,w.title,w.started_at,w.completed_at,w.elapsed_seconds,count(s.id) sets,coalesce(sum(s.reps*s.weight),0) volume
            FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id LEFT JOIN workout_set s ON s.workout_exercise_id=we.id
            WHERE w.owner_id=:owner AND w.status='COMPLETED' AND w.completed_at>=:start AND w.completed_at<:end
            GROUP BY w.id ORDER BY w.completed_at DESC""",
        mapOf("owner" to owner, "start" to start, "end" to end), RowMapper { rs, _ -> completedWorkoutMapper.mapRow(rs, 0).copy(sets = rs.getInt("sets"), volume = rs.getBigDecimal("volume")) }
    )

    fun stats(owner: UUID): WorkoutStats = jdbc.queryForObject(
        "SELECT count(DISTINCT w.id) sessions, coalesce(sum(s.reps*s.weight),0) volume FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id LEFT JOIN workout_set s ON s.workout_exercise_id=we.id WHERE w.owner_id=:owner AND w.status='COMPLETED'",
        mapOf("owner" to owner), RowMapper { rs, _ -> WorkoutStats(rs.getLong("sessions"), rs.getBigDecimal("volume")) }
    )

    fun volumeTrend(owner: UUID): List<VolumeTrend> = jdbc.query(
        "SELECT w.title,w.completed_at,coalesce(sum(s.reps*s.weight),0) volume FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id LEFT JOIN workout_set s ON s.workout_exercise_id=we.id WHERE w.owner_id=:owner AND w.status='COMPLETED' GROUP BY w.id ORDER BY w.completed_at DESC LIMIT 8",
        mapOf("owner" to owner), RowMapper { rs, _ -> VolumeTrend(rs.getString("title"), rs.getTimestamp("completed_at"), rs.getBigDecimal("volume")) }
    )

    fun records(owner: UUID): List<ExerciseRecord> = jdbc.query(
        "SELECT e.name,max(s.weight) max_weight,max(s.reps) max_reps FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id JOIN exercise e ON e.id=we.exercise_id JOIN workout w ON w.id=we.workout_id WHERE w.owner_id=:owner AND w.status='COMPLETED' GROUP BY e.name ORDER BY max_weight DESC LIMIT 12",
        mapOf("owner" to owner), RowMapper { rs, _ -> ExerciseRecord(rs.getString("name"), rs.getInt("max_reps"), rs.getBigDecimal("max_weight"), BigDecimal.ZERO, "") }
    )

    fun completedDates(owner: UUID, start: Timestamp, end: Timestamp): List<Timestamp> = jdbc.query(
        "SELECT completed_at FROM workout WHERE owner_id=:owner AND status='COMPLETED' AND completed_at>=:start AND completed_at<:end",
        mapOf("owner" to owner, "start" to start, "end" to end), RowMapper { rs, _ -> rs.getTimestamp("completed_at") }
    )

    private val completedWorkoutMapper = RowMapper<CompletedWorkout> { rs, _ ->
        CompletedWorkout(rs.uuid("id"), rs.getString("title"), rs.getTimestamp("started_at"), rs.getTimestamp("completed_at"), rs.getInt("elapsed_seconds"))
    }
}

private fun ResultSet.uuid(column: String): UUID = getObject(column, UUID::class.java)
