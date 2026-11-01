package com.pjarek.gym

import java.sql.Timestamp
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

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

@Component
class WorkoutCalendar(private val jdbc: JdbcTemplate) {
    private val zone = ZoneId.systemDefault()

    fun view(ownerId: UUID, month: YearMonth, selectedDate: LocalDate?): WorkoutCalendarView {
        val start = month.atDay(1)
        val counts = jdbc.query(
            "SELECT completed_at FROM workout WHERE owner_id=? AND status='COMPLETED' AND completed_at>=? AND completed_at<?",
            { rs, _ -> rs.getTimestamp(1).toInstant().atZone(zone).toLocalDate() },
            ownerId,
            boundary(start),
            boundary(start.plusMonths(1))
        ).groupingBy { it }.eachCount()
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
