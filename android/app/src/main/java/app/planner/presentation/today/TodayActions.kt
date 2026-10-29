package app.planner.presentation.today

import app.planner.domain.model.Record
import java.time.LocalDate

data class TodayActions(val complete: (Record) -> Unit, val later: (Record) -> Unit, val selectDate: (LocalDate) -> Unit, val cloud: () -> Unit, val retry: () -> Unit)
