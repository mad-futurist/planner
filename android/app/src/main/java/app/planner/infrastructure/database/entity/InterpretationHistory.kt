package app.planner.infrastructure.database.entity

import androidx.room.*
import app.planner.domain.time.isoInstant

@Entity(tableName = "interpretations", indices = [Index("noteId")])
data class InterpretationHistory(@PrimaryKey val id: String, val noteId: String, val inputVersion: Int, val schemaVersion: Int, val modelVersion: String, val proposedData: String, val validationResult: String, val correctedByUser: Boolean = false, val createdAt: String = isoInstant())
