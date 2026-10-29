package app.planner.infrastructure.database.entity

import androidx.room.*

@Entity(tableName = "idea_slots")
data class IdeaSlot(@PrimaryKey val day: String, val recordId: String?)
