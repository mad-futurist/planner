package app.planner.presentation.ideas

import app.planner.domain.model.Record

data class IdeasUiState(
    val ideas: List<Record> = emptyList(),
    val query: String = "",
    val page: Int = 0,
    val reads: Map<String, String> = emptyMap(),
)
