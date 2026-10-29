package app.planner.presentation.journal

import app.planner.domain.model.RawNote
import app.planner.domain.model.Record

data class JournalEntry(val note: RawNote, val records: List<Record>)

data class JournalUiState(val entries: List<JournalEntry> = emptyList(), val page: Int = 0, val reads: Map<String, String> = emptyMap())
