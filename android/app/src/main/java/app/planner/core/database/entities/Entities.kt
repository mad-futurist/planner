package app.planner.core.database.entities

import androidx.room.*
import java.time.Instant
import java.util.UUID
import app.planner.core.isoInstant

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

@Entity(tableName = "outbox", indices = [Index(value = ["status", "nextAttemptAt"]), Index("entityId"), Index("createdAt"), Index(value = ["entityId", "createdAt"]), Index(value = ["noteId", "path"])])
data class OutboxOperation(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entityId: String,
    val noteId: String,
    val path: String,
    val method: String = "POST",
    val payload: String,
    val retryCount: Int = 0,
    val nextAttemptAt: Long = 0,
    val lastError: String? = null,
    val status: String = "queued",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "interpretations", indices = [Index("noteId")])
data class InterpretationHistory(@PrimaryKey val id: String, val noteId: String, val inputVersion: Int, val schemaVersion: Int, val modelVersion: String, val proposedData: String, val validationResult: String, val correctedByUser: Boolean = false, val createdAt: String = isoInstant())

@Entity(tableName = "corrections", indices = [Index("sourceNoteId")])
data class UserCorrection(@PrimaryKey val id: String = UUID.randomUUID().toString(), val sourceNoteId: String, val previousInterpretation: String, val correctedInterpretation: String, val createdAt: String = isoInstant())

@Entity(tableName = "types", primaryKeys = ["id", "schemaVersion"])
data class TypeDefinition(val id: String, val schemaVersion: Int = 1, val name: String, val baseKind: String, val fieldDefinitions: String = "{}", val displayConfiguration: String = "{}", val allowedCapabilities: String = "[]", val enabled: Boolean = true)

@Entity(tableName = "rules")
data class UserRule(@PrimaryKey val id: String, val description: String, val conditions: String, val action: String, val enabled: Boolean, val explicitlyApproved: Boolean)

@Entity(tableName = "idea_slots")
data class IdeaSlot(@PrimaryKey val day: String, val recordId: String?)

@Entity(tableName = "meta")
data class Meta(@PrimaryKey val key: String, val value: String)

data class StatusCount(val status: String, val count: Int)
data class ProfileSummary(val notes: Int = 0, val completed: Int = 0, val ideas: Int = 0, val tasks: Int = 0)
data class CollectionCount(val name: String, val count: Int)
