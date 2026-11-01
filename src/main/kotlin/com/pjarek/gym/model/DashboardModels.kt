package com.pjarek.gym.model

import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

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

data class OverviewPage(
    val calendar: WorkoutCalendarView,
    val routines: List<RoutineSummary>,
    val routineDays: List<RoutineDaySummary>,
    val active: List<ActiveWorkout>,
    val recent: List<CompletedWorkout>,
    val stats: WorkoutStats,
    val volumeTrend: List<VolumeTrend>
)

data class HistoryPage(val calendar: WorkoutCalendarView, val history: List<CompletedWorkout>, val records: List<ExerciseRecord>)

data class WorkoutCalendarDay(val date: LocalDate?, val sessions: Int, val selected: Boolean)

data class WorkoutCalendarView(
    val month: YearMonth,
    val monthLabel: String,
    val previousMonth: YearMonth?,
    val nextMonth: YearMonth?,
    val selectedDate: LocalDate?,
    val selectedLabel: String?,
    val days: List<WorkoutCalendarDay>
)
