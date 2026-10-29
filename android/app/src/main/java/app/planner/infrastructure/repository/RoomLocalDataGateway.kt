package app.planner.infrastructure.repository

import android.content.Context
import app.planner.application.port.LocalDataGateway
import app.planner.domain.model.OutboxOperation
import app.planner.infrastructure.preferences.Preferences
import app.planner.infrastructure.sync.SyncEngine
import com.google.gson.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
        app.planner.infrastructure.overlay.OverlayManager.stop(context)
        app.planner.infrastructure.overlay.CaptureNotification.update(context, false)
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
