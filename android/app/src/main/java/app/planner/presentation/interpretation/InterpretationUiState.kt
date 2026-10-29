package app.planner.presentation.interpretation

import app.planner.domain.model.InterpretationHistory
import app.planner.domain.model.RawNote

data class InterpretationUiState(val note: RawNote? = null, val history: InterpretationHistory? = null, val saving: Boolean = false, val closeVersion: Int = 0, val sessionId: Int = 0, val error: String? = null)
