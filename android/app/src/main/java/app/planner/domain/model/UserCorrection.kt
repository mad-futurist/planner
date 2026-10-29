package app.planner.domain.model

import app.planner.domain.time.isoInstant
import java.util.UUID

data class UserCorrection(val id: String = UUID.randomUUID().toString(), val sourceNoteId: String, val previousInterpretation: String, val correctedInterpretation: String, val createdAt: String = isoInstant())
