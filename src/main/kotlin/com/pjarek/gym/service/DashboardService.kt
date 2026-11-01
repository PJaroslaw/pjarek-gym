package com.pjarek.gym.service

import com.pjarek.gym.model.HistoryPage
import com.pjarek.gym.model.OverviewPage
import com.pjarek.gym.model.WeightUnits
import com.pjarek.gym.repository.DashboardRepository
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class DashboardService(private val repository: DashboardRepository, private val calendar: WorkoutCalendar, private val accounts: AccountService) {
    fun overview(owner: UUID, month: String?, day: LocalDate?): OverviewPage {
        val (visibleMonth, selectedDate) = calendarSelection(month, day)
        val recent = selectedDate?.let { date ->
            repository.completedWorkouts(owner, calendar.boundary(date), calendar.boundary(date.plusDays(1)))
                .map { it.copy(durationLabel = formatDuration(it.elapsedSeconds)) }
        }.orEmpty()
        val trend = repository.volumeTrend(owner).reversed()
        val volumeMax = trend.maxOfOrNull { it.volume.toDouble() }?.coerceAtLeast(1.0) ?: 1.0
        return OverviewPage(
            calendar.view(owner, visibleMonth, selectedDate), repository.routines(owner), repository.routineDays(owner),
            repository.activeWorkouts(owner), recent, repository.stats(owner),
            trend.map { it.copy(barHeight = maxOf(6.0, 85 * it.volume.toDouble() / volumeMax)) }
        )
    }

    fun history(owner: UUID, month: String?, day: LocalDate?): HistoryPage {
        val (visibleMonth, selectedDate) = calendarSelection(month, day)
        val history = selectedDate?.let { date ->
            repository.history(owner, calendar.boundary(date), calendar.boundary(date.plusDays(1)))
                .map { it.copy(durationLabel = formatDuration(it.elapsedSeconds)) }
        }.orEmpty()
        val unit = accounts.account(owner).weightUnit
        val records = repository.records(owner).map {
            it.copy(displayWeight = WeightUnits.fromKilograms(it.maxWeight, unit), weightUnit = unit)
        }
        return HistoryPage(calendar.view(owner, visibleMonth, selectedDate), history, records)
    }

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun calendarSelection(month: String?, day: LocalDate?): Pair<YearMonth, LocalDate?> {
        require(month == null || day == null) { "Choose a calendar month or day" }
        val today = LocalDate.now()
        val visibleMonth = if (month == null) day?.let(YearMonth::from) ?: YearMonth.from(today) else
            runCatching { YearMonth.parse(month) }.getOrElse { throw IllegalArgumentException("Invalid calendar month") }
        require(visibleMonth.year in 1..9999) { "Calendar month is out of range" }
        require(day == null || YearMonth.from(day) == visibleMonth) { "Selected day must be in the visible month" }
        return visibleMonth to (day ?: if (month == null) today else null)
    }
}
