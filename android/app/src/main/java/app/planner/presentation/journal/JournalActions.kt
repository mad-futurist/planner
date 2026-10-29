package app.planner.presentation.journal

import app.planner.domain.model.RawNote

data class JournalActions(val reprocess: (RawNote) -> Unit, val page: (Int) -> Unit, val retry: () -> Unit)
