package app.planner.presentation.today

import app.planner.domain.model.ProfileSummary
import app.planner.domain.model.Record
import java.time.LocalDate

data class TodayUiState(
    val records: List<Record> = emptyList(),
    val tomorrow: List<Record> = emptyList(),
    val completed: List<Record> = emptyList(),
    val idea: Record? = null,
    val review: List<Record> = emptyList(),
    val profile: ProfileSummary = ProfileSummary(),
    val date: LocalDate = LocalDate.now(),
    val reads: Map<String, String> = emptyMap(),
)
