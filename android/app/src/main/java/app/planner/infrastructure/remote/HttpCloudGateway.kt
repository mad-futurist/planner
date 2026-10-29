package app.planner.infrastructure.remote

import app.planner.application.model.AiPreview
import app.planner.application.model.AiPreviewRecord
import app.planner.application.port.CloudGateway
import app.planner.domain.interpretation.InterpretationHints
import app.planner.domain.model.OutboxOperation
import app.planner.domain.model.Record
import app.planner.domain.policy.CaptureContext
import app.planner.infrastructure.sync.SyncEngine
import com.google.gson.*
import javax.inject.Inject

class HttpCloudGateway @Inject constructor(private val engine: SyncEngine, private val gson: Gson) : CloudGateway {
    override suspend fun connect(url: String, invitation: String) { engine.connect(url, invitation) }
    override suspend fun sync() = engine.run()
    override suspend fun emailAccount(url: String, action: String, email: String, password: String) = engine.emailAccount(url, action, email, password)
    override suspend fun beginGoogle(url: String, linkPassword: String?) = engine.beginGoogle(url, linkPassword)
    override suspend fun finishGoogle(callback: String) { engine.finishGoogle(android.net.Uri.parse(callback)) }
    override suspend fun signOut() { engine.signOut() }
    override suspend fun deleteAccount(password: String) { engine.deleteAccount(password) }
    override suspend fun preview(text: String, hints: InterpretationHints, reference: CaptureContext): AiPreview {
        val value = engine.previewAi(JsonObject().apply {
        addProperty("content", text); addProperty("createdAt", reference.instant.toString()); addProperty("timezone", reference.timezone.id); addProperty("locale", reference.locale); add("interpretationHints", gson.toJsonTree(hints))
    })
        return AiPreview(value.getAsJsonArray("records").map { it.asJsonObject.let { row -> AiPreviewRecord(row["baseKind"].asString, row["title"].asString) } }, value["needsReview"].asBoolean)
    }
    override suspend fun contribution(enabled: Boolean?) = engine.learningSettings(enabled).get("contributionEnabled").asBoolean
    override suspend fun develop(record: Record) { engine.develop(record) }
    override suspend fun keepLocal(operation: OutboxOperation) { engine.keepLocal(operation) }
}
