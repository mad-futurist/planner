package app.planner.domain.model

import app.planner.domain.time.isoInstant
import java.util.UUID

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
