package app.planner.presentation.record

import app.planner.domain.model.RawNote
import app.planner.domain.model.Record
import app.planner.domain.model.TypeDefinition

data class RecordUiState(val record: Record? = null, val note: RawNote? = null, val origin: Record? = null, val types: List<TypeDefinition> = emptyList(), val saving: Boolean = false, val closeVersion: Int = 0, val sessionId: Int = 0, val error: String? = null)
