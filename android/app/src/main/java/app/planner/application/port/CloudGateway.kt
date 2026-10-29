package app.planner.application.port

import app.planner.application.model.AiPreview
import app.planner.domain.interpretation.InterpretationHints
import app.planner.domain.model.OutboxOperation
import app.planner.domain.model.Record
import app.planner.domain.policy.CaptureContext

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
