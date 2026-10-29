package app.planner.infrastructure.database.entity

import androidx.room.*
import java.util.UUID

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
