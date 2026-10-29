package app.planner.infrastructure.database.entity

import androidx.room.*
import app.planner.domain.time.isoInstant
import java.util.UUID

@Entity(tableName = "corrections", indices = [Index("sourceNoteId")])
data class UserCorrection(@PrimaryKey val id: String = UUID.randomUUID().toString(), val sourceNoteId: String, val previousInterpretation: String, val correctedInterpretation: String, val createdAt: String = isoInstant())
