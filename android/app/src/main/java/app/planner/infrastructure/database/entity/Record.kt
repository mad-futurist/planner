package app.planner.infrastructure.database.entity

import androidx.room.*
import app.planner.domain.time.isoInstant
import java.util.UUID

@Entity(tableName = "records", indices = [Index("sourceNoteId"), Index(value = ["baseKind", "state", "plannedFor"]), Index("startAt"), Index("dueAt"), Index("dueDate"), Index("completedAt")])
data class Record(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceNoteId: String,
    val baseKind: String = "task",
    val customTypeId: String? = null,
    val typeSchemaVersion: Int? = null,
    val title: String,
    val fieldsJson: String = "{}",
    val startAt: String? = null,
    val endAt: String? = null,
    val dueAt: String? = null,
    val dueDate: String? = null,
    val plannedFor: String? = null,
    val remindAt: String? = null,
    val timezone: String = "UTC",
    val recurrenceRule: String? = null,
    val state: String = "pending",
    val completedAt: String? = null,
    val pinned: Boolean = false,
    val deferredUntil: String? = null,
    val createdAt: String = isoInstant(),
    val updatedAt: String = createdAt,
    val interpretationVersion: Int = 0,
    val isUserConfirmed: Boolean = false,
    val version: Int = 1,
)
