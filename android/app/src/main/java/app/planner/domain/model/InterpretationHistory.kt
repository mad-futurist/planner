package app.planner.domain.model

import app.planner.domain.time.isoInstant

data class InterpretationHistory(val id: String, val noteId: String, val inputVersion: Int, val schemaVersion: Int, val modelVersion: String, val proposedData: String, val validationResult: String, val correctedByUser: Boolean = false, val createdAt: String = isoInstant())
