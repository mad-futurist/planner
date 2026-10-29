package app.planner.domain.model

import java.time.Instant
import java.util.UUID
import app.planner.domain.isoInstant

data class RawNote(
    val id: String = UUID.randomUUID().toString(),
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
    val interpretationHintsJson: String = "{}",
)

data class Record(
    val id: String = UUID.randomUUID().toString(),
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

data class OutboxOperation(
    val id: String = UUID.randomUUID().toString(),
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

data class InterpretationHistory(val id: String, val noteId: String, val inputVersion: Int, val schemaVersion: Int, val modelVersion: String, val proposedData: String, val validationResult: String, val correctedByUser: Boolean = false, val createdAt: String = isoInstant())

data class UserCorrection(val id: String = UUID.randomUUID().toString(), val sourceNoteId: String, val previousInterpretation: String, val correctedInterpretation: String, val createdAt: String = isoInstant())

data class TypeDefinition(val id: String, val schemaVersion: Int = 1, val name: String, val baseKind: String, val fieldDefinitions: String = "{}", val displayConfiguration: String = "{}", val allowedCapabilities: String = "[]", val enabled: Boolean = true)

data class UserRule(val id: String, val description: String, val conditions: String, val action: String, val enabled: Boolean, val explicitlyApproved: Boolean)

data class IdeaSlot(val day: String, val recordId: String?)

data class Meta(val key: String, val value: String)

data class StatusCount(val status: String, val count: Int)
data class ProfileSummary(val notes: Int = 0, val completed: Int = 0, val ideas: Int = 0, val tasks: Int = 0)
data class CollectionCount(val name: String, val count: Int)
