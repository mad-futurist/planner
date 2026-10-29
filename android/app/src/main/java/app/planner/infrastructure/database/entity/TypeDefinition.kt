package app.planner.infrastructure.database.entity

import androidx.room.*

@Entity(tableName = "types", primaryKeys = ["id", "schemaVersion"])
data class TypeDefinition(val id: String, val schemaVersion: Int = 1, val name: String, val baseKind: String, val fieldDefinitions: String = "{}", val displayConfiguration: String = "{}", val allowedCapabilities: String = "[]", val enabled: Boolean = true)
