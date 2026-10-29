package app.planner.presentation.tasks

import app.planner.domain.model.Record

data class TasksUiState(
    val tasks: List<Record> = emptyList(),
    val filter: String = "today",
    val counts: Map<String, Int> = emptyMap(),
    val page: Int = 0,
    val reads: Map<String, String> = emptyMap(),
)
