package app.planner.domain.model

import java.util.UUID

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
