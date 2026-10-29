package app.planner.application

import app.planner.domain.*
import app.planner.domain.model.*

/** Remote capabilities. Android, credentials and HTTP JSON stay in the adapter. */
data class AiPreviewRecord(val kind: String, val title: String)
data class AiPreview(val records: List<AiPreviewRecord>, val needsReview: Boolean)

interface CloudGateway {
    suspend fun connect(url: String, invitation: String)
    suspend fun sync(): Boolean
    suspend fun emailAccount(url: String, action: String, email: String, password: String): String
    suspend fun beginGoogle(url: String, linkPassword: String? = null): String
    suspend fun finishGoogle(callback: String)
    suspend fun signOut()
    suspend fun deleteAccount(password: String)
    suspend fun preview(text: String, hints: InterpretationHints, reference: CaptureContext): AiPreview
    suspend fun contribution(enabled: Boolean? = null): Boolean
    suspend fun develop(record: Record)
    suspend fun keepLocal(operation: OutboxOperation)
}

interface LocalDataGateway {
    suspend fun export(append: (String) -> Unit)
    suspend fun clear()
    suspend fun retry(operation: OutboxOperation)
}
