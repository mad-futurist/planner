package app.planner.presentation.review

import app.planner.domain.model.RawNote
import app.planner.domain.model.Record
import java.time.LocalDate

data class ReviewUiState(val notes: List<RawNote> = emptyList(), val tasks: List<Record> = emptyList(), val date: LocalDate = LocalDate.now(), val metadata: Map<String, String> = emptyMap(), val reads: Map<String, String> = emptyMap(), val split: Record? = null, val saving: Boolean = false)
