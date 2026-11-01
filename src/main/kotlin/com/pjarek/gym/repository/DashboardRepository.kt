package com.pjarek.gym.repository

import com.pjarek.gym.mapper.DashboardRowMappers
import com.pjarek.gym.model.ActiveWorkout
import com.pjarek.gym.model.CompletedWorkout
import com.pjarek.gym.model.ExerciseRecord
import com.pjarek.gym.model.RoutineDaySummary
import com.pjarek.gym.model.RoutineSummary
import com.pjarek.gym.model.VolumeTrend
import com.pjarek.gym.model.WorkoutStats
import java.sql.Timestamp
import java.util.UUID
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class DashboardRepository(private val jdbc: NamedParameterJdbcTemplate) {
    fun routines(owner: UUID): List<RoutineSummary> = jdbc.query(
        "SELECT id,name FROM routine WHERE owner_id=:owner ORDER BY name",
        mapOf("owner" to owner), DashboardRowMappers.routine
    )

    fun routineDays(owner: UUID): List<RoutineDaySummary> = jdbc.query(
        """SELECT d.id,d.routine_id,d.name,d.position,count(ri.id) exercise_count
            FROM routine_day d JOIN routine r ON r.id=d.routine_id
            LEFT JOIN routine_item ri ON ri.day_id=d.id
            WHERE r.owner_id=:owner GROUP BY d.id,r.name ORDER BY r.name,d.position""",
        mapOf("owner" to owner), DashboardRowMappers.routineDay
    )

    fun activeWorkouts(owner: UUID): List<ActiveWorkout> = jdbc.query(
        "SELECT id,title,started_at,status FROM workout WHERE owner_id=:owner AND status IN ('IN_PROGRESS','PAUSED') ORDER BY started_at DESC",
        mapOf("owner" to owner), DashboardRowMappers.activeWorkout
    )

    fun completedWorkouts(owner: UUID, start: Timestamp, end: Timestamp): List<CompletedWorkout> = jdbc.query(
        "SELECT id,title,started_at,completed_at,elapsed_seconds FROM workout WHERE owner_id=:owner AND status='COMPLETED' AND completed_at>=:start AND completed_at<:end ORDER BY completed_at DESC",
        mapOf("owner" to owner, "start" to start, "end" to end), DashboardRowMappers.completedWorkout
    )

    fun history(owner: UUID, start: Timestamp, end: Timestamp): List<CompletedWorkout> = jdbc.query(
        """SELECT w.id,w.title,w.started_at,w.completed_at,w.elapsed_seconds,count(s.id) sets,coalesce(sum(s.reps*s.weight),0) volume
            FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id LEFT JOIN workout_set s ON s.workout_exercise_id=we.id
            WHERE w.owner_id=:owner AND w.status='COMPLETED' AND w.completed_at>=:start AND w.completed_at<:end
            GROUP BY w.id ORDER BY w.completed_at DESC""",
        mapOf("owner" to owner, "start" to start, "end" to end), DashboardRowMappers.historyWorkout
    )

    fun stats(owner: UUID): WorkoutStats = jdbc.queryForObject(
        "SELECT count(DISTINCT w.id) sessions, coalesce(sum(s.reps*s.weight),0) volume FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id LEFT JOIN workout_set s ON s.workout_exercise_id=we.id WHERE w.owner_id=:owner AND w.status='COMPLETED'",
        mapOf("owner" to owner), DashboardRowMappers.workoutStats
    )

    fun volumeTrend(owner: UUID): List<VolumeTrend> = jdbc.query(
        "SELECT w.title,w.completed_at,coalesce(sum(s.reps*s.weight),0) volume FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id LEFT JOIN workout_set s ON s.workout_exercise_id=we.id WHERE w.owner_id=:owner AND w.status='COMPLETED' GROUP BY w.id ORDER BY w.completed_at DESC LIMIT 8",
        mapOf("owner" to owner), DashboardRowMappers.volumeTrend
    )

    fun records(owner: UUID): List<ExerciseRecord> = jdbc.query(
        "SELECT e.name,max(s.weight) max_weight,max(s.reps) max_reps FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id JOIN exercise e ON e.id=we.exercise_id JOIN workout w ON w.id=we.workout_id WHERE w.owner_id=:owner AND w.status='COMPLETED' GROUP BY e.name ORDER BY max_weight DESC LIMIT 12",
        mapOf("owner" to owner), DashboardRowMappers.exerciseRecord
    )

    fun completedDates(owner: UUID, start: Timestamp, end: Timestamp): List<Timestamp> = jdbc.query(
        "SELECT completed_at FROM workout WHERE owner_id=:owner AND status='COMPLETED' AND completed_at>=:start AND completed_at<:end",
        mapOf("owner" to owner, "start" to start, "end" to end), DashboardRowMappers.completedDate
    )

}
