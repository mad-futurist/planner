package app.planner.domain.model

import app.planner.domain.time.isoInstant
import java.util.UUID

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
