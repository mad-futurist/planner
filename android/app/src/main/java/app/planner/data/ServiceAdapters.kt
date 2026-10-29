package app.planner.data

import app.planner.application.*
import app.planner.domain.*
import app.planner.domain.model.*
import app.planner.core.Preferences
import app.planner.sync.SyncEngine
import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

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

class RoomLocalDataGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlannerRepository,
    private val preferences: Preferences,
    private val engine: SyncEngine,
) : LocalDataGateway {
    override suspend fun export(append: (String) -> Unit) = withContext(Dispatchers.IO) {
        append("{\"schemaVersion\":1,\"notes\":[")
        var before: String? = null; var beforeId: String? = null; var first = true
        while (true) {
            val page = repository.dao.exportPage(50, before, beforeId)
            if (page.isEmpty()) break
            for (note in page) {
                if (!first) append(",")
                first = false
                val value = repository.noteJson(note)
                value.add("records", JsonArray().apply { repository.dao.recordsForNote(note.id).forEach { add(repository.recordJson(it)) } })
                append(value.toString())
            }
            before = page.last().createdAt; beforeId = page.last().id
        }
        append("]}")
    }
    override suspend fun clear() {
        androidx.work.WorkManager.getInstance(context).cancelAllWork()
        app.planner.features.overlay.OverlayManager.stop(context)
        app.planner.features.overlay.CaptureNotification.update(context, false)
        repository.syncMutex.withLock {
            engine.tokens.clear(); repository.clear()
            for (key in listOf("draft", "draftHints", "overlayDraft", "serverUrl", "cloudConsent")) preferences.set(key, "")
            preferences.set("overlay", "false"); preferences.set("notification", "false"); preferences.capturePrivacy(true)
        }
        preferences.googleSession()
    }
    override suspend fun retry(operation: OutboxOperation) {
        repository.dao.operation(operation.copy(status = "queued", retryCount = 0, nextAttemptAt = 0)); repository.requestSync()
    }
}
