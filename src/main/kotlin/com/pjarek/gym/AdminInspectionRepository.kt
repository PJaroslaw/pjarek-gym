package com.pjarek.gym

import java.util.UUID
import org.springframework.jdbc.core.ColumnMapRowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AdminInspectionRepository(private val jdbc: NamedParameterJdbcTemplate) {
    private val rows = ColumnMapRowMapper()

    fun routineCount(owner: UUID): Long = jdbc.queryForObject(
        "SELECT count(*) FROM routine WHERE owner_id=:owner", mapOf("owner" to owner), Long::class.java
    ) ?: 0L

    fun routines(owner: UUID, limit: Int, offset: Int): List<Map<String, Any?>> = jdbc.query(
        """SELECT r.id,r.name,count(DISTINCT d.id) day_count,count(ri.id) exercise_count
            FROM routine r LEFT JOIN routine_day d ON d.routine_id=r.id
            LEFT JOIN routine_item ri ON ri.day_id=d.id WHERE r.owner_id=:owner
            GROUP BY r.id ORDER BY r.name,r.id LIMIT :limit OFFSET :offset""",
        mapOf("owner" to owner, "limit" to limit, "offset" to offset), rows
    )

    fun workoutCount(owner: UUID): Long = jdbc.queryForObject(
        "SELECT count(*) FROM workout WHERE owner_id=:owner", mapOf("owner" to owner), Long::class.java
    ) ?: 0L

    fun workouts(owner: UUID, limit: Int, offset: Int): List<Map<String, Any?>> = jdbc.query(
        """SELECT w.id,w.title,w.status,w.started_at,w.completed_at,w.elapsed_seconds,
            count(DISTINCT we.id) exercise_count,count(s.id) set_count
            FROM workout w LEFT JOIN workout_exercise we ON we.workout_id=w.id
            LEFT JOIN workout_set s ON s.workout_exercise_id=we.id WHERE w.owner_id=:owner
            GROUP BY w.id ORDER BY w.started_at DESC,w.id DESC LIMIT :limit OFFSET :offset""",
        mapOf("owner" to owner, "limit" to limit, "offset" to offset), rows
    )

    fun routine(owner: UUID, routineId: UUID): Map<String, Any?>? = jdbc.query(
        "SELECT id,name FROM routine WHERE id=:routineId AND owner_id=:owner",
        mapOf("routineId" to routineId, "owner" to owner), rows
    ).firstOrNull()

    fun routineDays(routineId: UUID): List<Map<String, Any?>> = jdbc.query(
        "SELECT id,name,position FROM routine_day WHERE routine_id=:routineId ORDER BY position",
        mapOf("routineId" to routineId), rows
    )

    fun routineItems(routineId: UUID): List<Map<String, Any?>> = jdbc.query(
        """SELECT ri.id,ri.day_id,e.name exercise_name,ri.planned_sets,ri.min_reps,ri.max_reps,ri.rest_seconds
            FROM routine_item ri JOIN routine_day d ON d.id=ri.day_id
            JOIN exercise e ON e.id=ri.exercise_id WHERE d.routine_id=:routineId ORDER BY d.position,ri.position""",
        mapOf("routineId" to routineId), rows
    )

    fun workout(owner: UUID, workoutId: UUID): Map<String, Any?>? = jdbc.query(
        "SELECT id,title,status,started_at,completed_at,elapsed_seconds FROM workout WHERE id=:workoutId AND owner_id=:owner",
        mapOf("workoutId" to workoutId, "owner" to owner), rows
    ).firstOrNull()

    fun workoutExercises(workoutId: UUID): List<Map<String, Any?>> = jdbc.query(
        """SELECT we.id,we.position,e.name exercise_name,we.planned_sets,we.min_reps,we.max_reps
            FROM workout_exercise we JOIN exercise e ON e.id=we.exercise_id
            WHERE we.workout_id=:workoutId ORDER BY we.position""",
        mapOf("workoutId" to workoutId), rows
    )

    fun sets(workoutId: UUID): List<Map<String, Any?>> = jdbc.query(
        """SELECT s.id,s.workout_exercise_id,s.set_number,s.reps,s.weight FROM workout_set s
            JOIN workout_exercise we ON we.id=s.workout_exercise_id
            WHERE we.workout_id=:workoutId ORDER BY we.position,s.set_number""",
        mapOf("workoutId" to workoutId), rows
    )
}
