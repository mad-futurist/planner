package app.planner.presentation.calendar

import app.planner.domain.model.Record
import java.time.LocalDate

data class CalendarActions(val complete: (Record) -> Unit, val selectDate: (LocalDate) -> Unit, val retry: () -> Unit)
