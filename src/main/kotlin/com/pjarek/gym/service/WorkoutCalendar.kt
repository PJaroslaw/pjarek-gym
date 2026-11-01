package com.pjarek.gym.service

import com.pjarek.gym.model.WorkoutCalendarDay
import com.pjarek.gym.model.WorkoutCalendarView
import com.pjarek.gym.repository.DashboardRepository
import java.sql.Timestamp
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class WorkoutCalendar(private val repository: DashboardRepository) {
    private val zone = ZoneId.systemDefault()

    fun view(ownerId: UUID, month: YearMonth, selectedDate: LocalDate?): WorkoutCalendarView {
        val start = month.atDay(1)
        val counts = repository.completedDates(ownerId, boundary(start), boundary(start.plusMonths(1)))
            .map { it.toInstant().atZone(zone).toLocalDate() }.groupingBy { it }.eachCount()
        val padding = start.dayOfWeek.value - 1
        val days = List(padding) { WorkoutCalendarDay(null, 0, false) } +
            (1..month.lengthOfMonth()).map { number ->
                val date = month.atDay(number)
                WorkoutCalendarDay(date, counts[date] ?: 0, date == selectedDate)
            }
        return WorkoutCalendarView(
            month,
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
            if (month == YearMonth.of(1, 1)) null else month.minusMonths(1),
            if (month == YearMonth.of(9999, 12)) null else month.plusMonths(1),
            selectedDate,
            selectedDate?.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)),
            days
        )
    }

    fun boundary(date: LocalDate): Timestamp = Timestamp.from(date.atStartOfDay(zone).toInstant())
}
