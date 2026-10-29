package app.planner.infrastructure.database.entity

import androidx.room.*
import app.planner.domain.time.isoInstant
import java.util.UUID

@Entity(tableName = "notes", indices = [Index("createdAt"), Index("processingStatus")])
data class RawNote(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val content: String,
    val originalContent: String = content,
    val createdAt: String = isoInstant(),
    val updatedAt: String = createdAt,
    val source: String = "APP",
    val privacyMode: String = "CLOUD_ALLOWED",
    val processingStatus: String = "pending",
    val syncStatus: String = "pending",
    val version: Int = 1,
    val locale: String = "en",
    val timezone: String = "UTC",
    val manual: Boolean = false,
    @ColumnInfo(defaultValue = "'{}'") val interpretationHintsJson: String = "{}",
)
