package app.planner.presentation.calendar

import app.planner.domain.model.Record
import java.time.LocalDate

data class CalendarUiState(
    val date: LocalDate = LocalDate.now(),
    val records: List<Record> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val counts: Map<LocalDate, Int> = emptyMap(),
    val reads: Map<String, String> = emptyMap(),
)
