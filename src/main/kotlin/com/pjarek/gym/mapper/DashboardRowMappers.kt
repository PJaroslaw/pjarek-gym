package com.pjarek.gym.mapper

import com.pjarek.gym.model.ActiveWorkout
import com.pjarek.gym.model.CompletedWorkout
import com.pjarek.gym.model.ExerciseRecord
import com.pjarek.gym.model.RoutineDaySummary
import com.pjarek.gym.model.RoutineSummary
import com.pjarek.gym.model.VolumeTrend
import com.pjarek.gym.model.WorkoutStats
import java.math.BigDecimal
import java.sql.ResultSet
import java.util.UUID
import org.springframework.jdbc.core.RowMapper

object DashboardRowMappers {
    val routine = RowMapper { rs: ResultSet, _ ->
        RoutineSummary(rs.uuid("id"), rs.getString("name"))
    }

    val routineDay = RowMapper { rs: ResultSet, _ ->
        RoutineDaySummary(rs.uuid("id"), rs.uuid("routine_id"), rs.getString("name"), rs.getInt("position"), rs.getInt("exercise_count"))
    }

    val activeWorkout = RowMapper { rs: ResultSet, _ ->
        ActiveWorkout(rs.uuid("id"), rs.getString("title"), rs.getTimestamp("started_at"), rs.getString("status"))
    }

    val completedWorkout = RowMapper { rs: ResultSet, _ ->
        CompletedWorkout(rs.uuid("id"), rs.getString("title"), rs.getTimestamp("started_at"), rs.getTimestamp("completed_at"), rs.getInt("elapsed_seconds"))
    }

    val historyWorkout = RowMapper { rs: ResultSet, rowNum ->
        completedWorkout.mapRow(rs, rowNum).copy(sets = rs.getInt("sets"), volume = rs.getBigDecimal("volume"))
    }

    val workoutStats = RowMapper { rs: ResultSet, _ ->
        WorkoutStats(rs.getLong("sessions"), rs.getBigDecimal("volume"))
    }

    val volumeTrend = RowMapper { rs: ResultSet, _ ->
        VolumeTrend(rs.getString("title"), rs.getTimestamp("completed_at"), rs.getBigDecimal("volume"))
    }

    val exerciseRecord = RowMapper { rs: ResultSet, _ ->
        ExerciseRecord(rs.getString("name"), rs.getInt("max_reps"), rs.getBigDecimal("max_weight"), BigDecimal.ZERO, "")
    }

    val completedDate = RowMapper { rs: ResultSet, _ -> rs.getTimestamp("completed_at") }
}

private fun ResultSet.uuid(column: String): UUID = getObject(column, UUID::class.java)
