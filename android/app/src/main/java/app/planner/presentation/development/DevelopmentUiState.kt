package app.planner.presentation.development

import app.planner.domain.model.Record

data class DevelopmentUiState(val record: Record? = null, val status: String = "unavailable", val jobId: String? = null, val steps: List<String> = emptyList(), val saving: Boolean = false, val closeVersion: Int = 0, val sessionId: Int = 0, val error: String? = null)
