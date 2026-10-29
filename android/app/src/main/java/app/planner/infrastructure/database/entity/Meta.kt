package app.planner.infrastructure.database.entity

import androidx.room.*

@Entity(tableName = "meta")
data class Meta(@PrimaryKey val key: String, val value: String)
