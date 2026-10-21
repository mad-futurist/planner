package app.planner.sync

import android.content.Context
import android.net.ConnectivityManager
import androidx.room.withTransaction
import app.planner.BuildConfig
import app.planner.core.Preferences
import app.planner.core.database.*
import app.planner.core.model.*
import app.planner.core.network.*
import app.planner.data.PlannerRepository
import app.planner.domain.retryDelayMillis
import com.google.gson.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

@Singleton
class SyncEngine @Inject constructor(@ApplicationContext private val context: Context, private val repository: PlannerRepository, private val preferences: Preferences, val tokens: TokenStore) {
    private val dao = repository.dao
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
    private var cachedUrl = ""
    private var cachedApi: PlannerApi? = null
    private fun api(url: String): PlannerApi {
        if (url != cachedUrl) {
            cachedApi = Retrofit.Builder().baseUrl(url.trimEnd('/') + "/api/v1/").client(client).addConverterFactory(GsonConverterFactory.create(repository.gson)).build().create(PlannerApi::class.java)
            cachedUrl = url
        }
        return cachedApi!!
    }
    private fun validateUrl(url: String) {
        val parsed = URI(url)
        require(parsed.host != null && parsed.userInfo == null && parsed.query == null && parsed.fragment == null) { "Enter a server URL without credentials or query parameters." }
        require(parsed.scheme == "https" || (BuildConfig.DEBUG && parsed.scheme == "http" && parsed.host in setOf("10.0.2.2", "127.0.0.1", "localhost"))) { "Use HTTPS, or a local debug address." }
    }

    suspend fun connect(url: String, invitation: String) = withContext(Dispatchers.IO) {
        validateUrl(url)
        repository.syncMutex.withLock {
            val request = JsonObject().apply { addProperty("code", invitation.trim()); addProperty("deviceId", UUID.randomUUID().toString()) }
            val response = api(url).enroll(request)
            require(response.isSuccessful) { "Invitation was rejected or expired." }
            val body = response.body()!!
            val userId = body.get("userId").asString
            val owner = dao.metadata("owner")?.value
            require(owner == null || owner == userId) { "This is another account. Export and clear this device before switching." }
            tokens.write(body.get("token").asString)
            preferences.set("serverUrl", url.trimEnd('/'))
            dao.meta(Meta("owner", userId))
            dao.meta(Meta("network", "connected"))
        }
        run()
    }

    suspend fun run(): Boolean = withContext(Dispatchers.IO) {
        try {
        repository.syncMutex.withLock {
            val url = preferences.values.first()["serverUrl"].orEmpty()
            val token = tokens.read()
            if (url.isBlank() || token == null) { dao.meta(Meta("network", "unconfigured")); return@withLock false }
            val connected = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork != null
            if (!connected) { dao.meta(Meta("network", "offline")); return@withLock true }
            val remote = api(url)
            val authorization = "Bearer $token"
            try {
                val config = remote.config(authorization)
                if (config.code() == 401) { dao.meta(Meta("network", "unauthorized")); return@withLock false }
                check(config.isSuccessful)
                dao.meta(Meta("ai", if (config.body()?.get("aiConfigured")?.asBoolean == true) "configured" else "unconfigured"))
                dao.meta(Meta("network", "connected"))
                var sent = 0
                while (sent++ < 50) {
                    val operation = dao.nextOperation(System.currentTimeMillis()) ?: break
                    val payload = JsonParser.parseString(operation.payload).asJsonObject
                    try {
                        val response = if (operation.method == "PATCH") remote.patch(operation.path, authorization, operation.id, payload) else remote.post(operation.path, authorization, operation.id, payload)
                        if (response.code() == 401) { dao.meta(Meta("network", "unauthorized")); break }
                        if (response.isSuccessful) {
                            repository.database.withTransaction {
                                dao.deleteOperation(operation.id)
                                response.body()?.let { result ->
                                    when {
                                        operation.path == "notes" -> applyNote(result)
                                        operation.path.startsWith("records/") -> applyRecord(result)
                                        operation.path.endsWith("/develop") -> applyJob(result)
                                    }
                                }
                            }
                        } else if (response.code() == 409) {
                            dao.operation(operation.copy(status = "conflict", lastError = "Revision conflict. Review before sending again."))
                        } else if (response.code() in setOf(400, 403, 404, 422)) {
                            dao.operation(operation.copy(status = "failed", lastError = "Server rejected this change (${response.code()})."))
                        } else { retry(operation) }
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { retry(operation); dao.meta(Meta("network", "offline")); break }
                }
                var hasMore = true
                var page = 0
                while (hasMore && page++ < 10) {
                    val cursor = dao.metadata("cursor")?.value ?: "0"
                    val response = remote.changes(authorization, cursor)
                    check(response.isSuccessful)
                    val body = response.body()!!
                    repository.database.withTransaction {
                        for (entry in body.getAsJsonArray("changes")) {
                            val change = entry.asJsonObject; val value = change.getAsJsonObject("payload")
                            when (change.get("kind").asString) {
                                "note" -> applyNote(value)
                                "record" -> applyRecord(value)
                                "interpretation" -> dao.history(InterpretationHistory(id = value.get("id").asString, noteId = value.get("noteId").asString, inputVersion = value.get("inputVersion").asInt, schemaVersion = value.get("schemaVersion").asInt, modelVersion = value.get("modelVersion").asString, proposedData = value.get("proposedData").toString(), validationResult = value.get("validationResult").asString, correctedByUser = value.get("correctedByUser").asBoolean, createdAt = value.get("createdAt").asString))
                                "type" -> dao.type(TypeDefinition(id = value.get("id").asString, schemaVersion = value.get("schemaVersion").asInt, name = value.get("name").asString, baseKind = value.get("baseKind").asString, fieldDefinitions = value.get("fieldDefinitions").toString(), displayConfiguration = value.get("displayConfiguration").toString(), allowedCapabilities = value.get("allowedCapabilities").toString(), enabled = value.get("enabled").asBoolean))
                                "rule" -> dao.rule(UserRule(value.get("id").asString, value.get("description").asString, value.get("conditions").toString(), value.get("action").toString(), value.get("enabled").asBoolean, value.get("explicitlyApproved").asBoolean))
                                "job" -> applyJob(value)
                            }
                        }
                        dao.meta(Meta("cursor", body.get("nextCursor").asString))
                    }
                    hasMore = body.get("hasMore").asBoolean
                }
                dao.meta(Meta("lastSync", java.time.Instant.now().toString()))
                hasMore || dao.nextOperation(Long.MAX_VALUE) != null
            } catch (e: CancellationException) { throw e } catch (_: Exception) { dao.meta(Meta("network", "offline")); true }
        }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { true }
    }

    private suspend fun retry(operation: OutboxOperation) {
        val attempt = operation.retryCount + 1
        dao.operation(operation.copy(retryCount = attempt, nextAttemptAt = System.currentTimeMillis() + retryDelayMillis(attempt - 1, Math.random()), status = if (attempt >= 8) "failed" else "queued", lastError = "Connection or server failure. Saved data is still on this device."))
    }

    private suspend fun applyNote(value: JsonObject) {
        repository.mergeNote(repository.gson.fromJson(value, RawNote::class.java))
    }
    private suspend fun applyRecord(value: JsonObject) {
        repository.mergeRecord(repository.recordFromJson(value))
    }
    private suspend fun applyJob(value: JsonObject) {
        if (value.get("kind").asString == "develop") dao.meta(Meta("job:${value.get("recordId").asString}", value.toString()))
    }

    suspend fun develop(record: Record) {
        val note = dao.noteById(record.sourceNoteId)!!
        require(note.privacyMode != "LOCAL_ONLY") { "Private ideas stay on this device. Add next steps manually." }
        require(tokens.read() != null) { "Connect a backend in Settings first." }
        repository.database.withTransaction {
            dao.meta(Meta("job:${record.id}", "{\"status\":\"queued\"}"))
            repository.queueOperation(OutboxOperation(entityId = record.id, noteId = note.id, path = "ideas/${record.id}/develop", payload = "{}"))
        }
        repository.requestSync()
    }

    suspend fun approve(record: Record, jobId: String, steps: List<String>) = repository.database.withTransaction {
        require(steps.isNotEmpty()) { "Choose at least one step." }
        if (dao.metadata("approved:$jobId") != null) return@withTransaction
        val current = dao.recordById(record.id)!!
        require(current.baseKind == "idea" && current.state == "pending") { "This idea is no longer available for development." }
        require(dao.noteById(current.sourceNoteId)?.privacyMode != "LOCAL_ONLY") { "Private ideas stay on this device. Add next steps manually." }
        val job = JsonParser.parseString(dao.metadata("job:${record.id}")!!.value).asJsonObject
        require(job.get("status").asString == "complete" && job.get("recordVersion").asInt == current.version) { "The idea changed. Develop it again before approving." }
        val allowed = job.getAsJsonObject("result").getAsJsonArray("steps").map { it.asString }
        require(job.get("id").asString == jobId && steps.all { it in allowed }) { "These suggestions changed. Review them again." }
        for (step in steps.distinct()) {
            val taskNote = repository.capture(step, false, manualKind = "task", plannedFor = LocalDate.now().toString())
            val task = dao.recordsForNote(taskNote.id).single()
            repository.edit(task.copy(fieldsJson = JsonObject().apply { addProperty("developedFrom", record.id); addProperty("suggestionJobId", jobId) }.toString()))
        }
        repository.later(current, LocalDate.now())
        dao.meta(Meta("approved:$jobId", "true"))
    }

    suspend fun keepLocal(operation: OutboxOperation) = withContext(Dispatchers.IO) {
        repository.syncMutex.withLock {
            val url = preferences.values.first()["serverUrl"].orEmpty()
            val authorization = "Bearer ${tokens.read() ?: error("Connect first") }"
            val response = api(url).get("notes/${operation.noteId}", authorization)
            check(response.isSuccessful)
            val note = response.body()!!
            repository.database.withTransaction {
                if (operation.path.startsWith("records/")) {
                    val local = dao.recordById(operation.entityId)!!
                    val remote = note.getAsJsonArray("records").firstOrNull { it.asJsonObject.get("id").asString == local.id }?.asJsonObject ?: error("Record no longer exists")
                    val revision = remote.get("version").asInt
                    for (old in dao.operationsForEntity(local.id).filter { it.path.startsWith("records/") && it.method == "PATCH" }) dao.deleteOperation(old.id)
                    dao.record(local.copy(version = revision + 1))
                    // Retain the blocked edit's order ahead of subsequent development requests.
                    dao.operation(operation.copy(id = UUID.randomUUID().toString(), retryCount = 0, nextAttemptAt = 0, status = "queued", lastError = null, payload = repository.recordJson(local.copy(version = revision + 1)).apply { addProperty("expectedVersion", revision) }.toString()))
                } else {
                    val payload = JsonParser.parseString(operation.payload).asJsonObject
                    payload.addProperty("expectedNoteVersion", note.get("version").asInt)
                    dao.deleteOperation(operation.id)
                    repository.queueOperation(operation.copy(id = UUID.randomUUID().toString(), payload = payload.toString(), status = "queued", lastError = null, retryCount = 0, nextAttemptAt = 0))
                }
            }
        }
        repository.requestSync()
    }
}
