package app.planner.infrastructure.database.entity

import androidx.room.*

@Entity(tableName = "rules")
data class UserRule(@PrimaryKey val id: String, val description: String, val conditions: String, val action: String, val enabled: Boolean, val explicitlyApproved: Boolean)
