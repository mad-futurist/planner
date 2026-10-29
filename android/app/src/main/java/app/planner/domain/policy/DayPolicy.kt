package app.planner.domain.policy

import app.planner.domain.model.Record
import app.planner.domain.time.isoInstant
import java.time.*

data class DayBounds(val day: String, val start: String, val end: String)

fun taskBucket(record: Record, date: LocalDate, zone: ZoneId): String {
    require(record.baseKind == "task")
    val day = record.plannedFor ?: record.dueDate
    if (day != null) return if (LocalDate.parse(day) <= date) "today" else "upcoming"
    val instant = record.startAt ?: record.dueAt
    return if (instant == null) "someday" else if (Instant.parse(instant) < date.plusDays(1).atStartOfDay(zone).toInstant()) "today" else "upcoming"
}

fun dayBounds(date: LocalDate, zone: ZoneId): DayBounds = DayBounds(date.toString(), isoInstant(date.atStartOfDay(zone).toInstant()), isoInstant(date.plusDays(1).atStartOfDay(zone).toInstant()))

fun isVisibleToday(record: Record, date: LocalDate, zone: ZoneId): Boolean {
    if (record.state != "pending") return false
    val bounds = dayBounds(date, zone)
    val start = record.startAt?.let(Instant::parse)
    val dayStart = Instant.parse(bounds.start)
    val dayEnd = Instant.parse(bounds.end)
    val timed = start != null && start < dayEnd && (start >= dayStart || (record.endAt?.let(Instant::parse)?.isAfter(dayStart) == true))
    val due = record.dueAt?.let(Instant::parse)?.let { it >= dayStart && it < dayEnd } == true
    return timed || (record.baseKind == "task" && (record.plannedFor == bounds.day || record.dueDate == bounds.day || due))
}

fun retryDelayMillis(attempt: Int, jitter: Double): Long = (30_000L * (1L shl attempt.coerceIn(0, 8)) * (0.8 + jitter.coerceIn(0.0, 1.0) * 0.4)).toLong().coerceAtMost(6 * 60 * 60 * 1000)

fun isScheduledOnDay(record: Record, date: LocalDate, zone: ZoneId): Boolean {
    if (record.state != "pending" || record.baseKind !in setOf("task", "event") || record.startAt == null) return false
    val bounds = dayBounds(date, zone)
    val start = Instant.parse(record.startAt)
    return start < Instant.parse(bounds.end) && (start >= Instant.parse(bounds.start) || record.endAt?.let { Instant.parse(it) > Instant.parse(bounds.start) } == true)
}
