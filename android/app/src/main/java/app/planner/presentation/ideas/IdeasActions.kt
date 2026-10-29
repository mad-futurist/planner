package app.planner.presentation.ideas

import app.planner.domain.model.Record

data class IdeasActions(val convert: (Record) -> Unit, val search: (String) -> Unit, val page: (Int) -> Unit, val retry: () -> Unit)
