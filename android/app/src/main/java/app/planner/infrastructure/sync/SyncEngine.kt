package app.planner.infrastructure.sync

import android.content.Context
import android.net.ConnectivityManager
import androidx.room.withTransaction
import app.planner.BuildConfig
import app.planner.domain.model.InterpretationHistory
import app.planner.domain.model.Meta
import app.planner.domain.model.OutboxOperation
import app.planner.domain.model.Record
import app.planner.domain.model.TypeDefinition
import app.planner.domain.model.UserRule
import app.planner.domain.policy.retryDelayMillis
import app.planner.infrastructure.preferences.Preferences
import app.planner.infrastructure.remote.PlannerApi
import app.planner.infrastructure.repository.PlannerRepository
import app.planner.infrastructure.security.TokenStore
import com.google.gson.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Singleton
class SyncEngine @Inject constructor(@ApplicationContext private val context: Context, private val repository: PlannerRepository, private val preferences: Preferences, val tokens: TokenStore) {
    private val dao = repository.dao
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
    private val apis = java.util.concurrent.ConcurrentHashMap<String, PlannerApi>()
    private fun api(url: String): PlannerApi = apis.computeIfAbsent(url) { Retrofit.Builder().baseUrl(it.trimEnd('/') + "/api/v1/").client(client).addConverterFactory(GsonConverterFactory.create(repository.gson)).build().create(PlannerApi::class.java) }
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
            acceptSession(url, body)
        }
        run()
    }

    private suspend fun acceptSession(url: String, body: JsonObject) {
        val userId = body.get("userId").asString
        val owner = dao.metadata("owner")?.value
        require(owner == null || owner == userId) { "This is another account. Export and clear this device before switching." }
        tokens.write(body.get("token").asString)
        preferences.set("serverUrl", url.trimEnd('/'))
        dao.meta(Meta("owner", userId)); dao.meta(Meta("network", "connected"))
    }
    private fun authResult(response: retrofit2.Response<JsonObject>): JsonObject {
        if (!response.isSuccessful) {
            val detail = runCatching { JsonParser.parseString(response.errorBody()?.string()).asJsonObject.get("detail") }.getOrNull()
            error(if (detail?.isJsonPrimitive == true) detail.asString else "Account request failed. Check the fields and connection.")
        }
        return response.body() ?: error("Account response was empty")
    }
    suspend fun emailAccount(url: String, action: String, email: String, password: String): String = withContext(Dispatchers.IO) {
        require(action in setOf("login", "register", "forgot-password"))
        validateUrl(url)
        repository.syncMutex.withLock {
            val body = JsonObject().apply { addProperty("email", email.trim()); if (action != "forgot-password") addProperty("password", password); if (action == "login") addProperty("deviceId", UUID.randomUUID().toString()) }
            val value = authResult(api(url).post("auth/$action", "", UUID.randomUUID().toString(), body))
            if (action == "login") { acceptSession(url, value); "Signed in" } else value.get("message").asString
        }
    }
    suspend fun beginGoogle(url: String, linkPassword: String? = null): String = withContext(Dispatchers.IO) {
        validateUrl(url)
        val verifier = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })
        val challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val body = JsonObject().apply { addProperty("challenge", challenge); linkPassword?.let { addProperty("password", it) } }
        val authorization = if (linkPassword != null) "Bearer ${tokens.read() ?: error("Sign in with your password first")}" else ""
        val value = authResult(api(url).post("auth/google/start", authorization, UUID.randomUUID().toString(), body))
        preferences.googleSession(url, value.get("state").asString, verifier)
        value.get("authorizationUrl").asString.also { require(URI(it).scheme == "https" && URI(it).host == "accounts.google.com") }
    }
    suspend fun finishGoogle(uri: android.net.Uri) = withContext(Dispatchers.IO) {
        require(uri.scheme == "app.planner" && uri.host == "auth")
        val values = preferences.values.first()
        val state = values["googleState"].orEmpty()
        require(state.isNotBlank() && state == uri.getQueryParameter("state") && System.currentTimeMillis() - (values["googleStartedAt"]?.toLongOrNull() ?: 0) in 0L..600_000L) { "Google sign-in expired. Start again." }
        if (uri.getQueryParameter("error") != null) { preferences.googleSession(); error("Google sign-in wasn't completed. For an existing email account, sign in with your password and explicitly link Google.") }
        val body = JsonObject().apply { addProperty("state", state); addProperty("code", uri.getQueryParameter("code")); addProperty("verifier", values["googleVerifier"]); addProperty("deviceId", UUID.randomUUID().toString()) }
        repository.syncMutex.withLock {
            val url = values["googleUrl"].orEmpty(); validateUrl(url)
            val response = authResult(api(url).post("auth/google/exchange", "", UUID.randomUUID().toString(), body))
            acceptSession(url, response); preferences.googleSession()
        }
        run()
    }
    suspend fun deleteAccount(password: String) = withContext(Dispatchers.IO) {
        repository.syncMutex.withLock {
            val owner = dao.metadata("owner")?.value ?: error("Sign in first")
            val url = preferences.values.first()["serverUrl"].orEmpty()
            val body = JsonObject().apply { addProperty("ownerId", owner); addProperty("confirmation", "DELETE"); addProperty("password", password) }
            authResult(api(url).delete("auth/account", "Bearer ${tokens.read() ?: error("Sign in first")}", body))
            tokens.clear()
            preferences.capturePrivacy(true); preferences.set("cloudConsent", ""); preferences.googleSession(); dao.meta(Meta("network", "unconfigured"))
        }
    }
    suspend fun signOut() = withContext(Dispatchers.IO) {
        repository.syncMutex.withLock {
            val url = preferences.values.first()["serverUrl"].orEmpty()
            tokens.read()?.let { token -> if (url.isNotBlank()) runCatching { api(url).post("auth/revoke", "Bearer $token", UUID.randomUUID().toString(), JsonObject()) } }
            tokens.clear(); preferences.capturePrivacy(true); preferences.set("cloudConsent", ""); preferences.googleSession(); dao.meta(Meta("network", "unconfigured"))
        }
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
                                        operation.path == "notes" -> applyNote(result, fromAcknowledgment = true)
                                        operation.path.startsWith("records/") -> applyRecord(result)
                                        operation.path.endsWith("/develop") -> applyJob(result)
                                    }
                                }
                            }
                        } else if (response.code() == 409) {
                            dao.operation(operation.copy(status = "conflict", lastError = "Revision conflict. Review before sending again."))
                        } else if (response.code() in setOf(400, 403, 404, 413, 422)) {
                            dao.operation(operation.copy(status = "failed", lastError = "Server rejected this change (${response.code()})."))
                        } else { retry(operation) }
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { retry(operation); dao.meta(Meta("network", "offline")); break }
                }
                repository.reconcileIncoming()
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
                repository.reconcileIncoming()
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

    private suspend fun applyNote(value: JsonObject, fromAcknowledgment: Boolean = false) {
        repository.mergeNote(repository.noteFromJson(value), fromAcknowledgment)
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

    suspend fun previewAi(body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val values = preferences.values.first()
        require(!app.planner.domain.policy.CapturePrivacy.isPrivate(values)) { "Enable Cloud AI before sending a draft for preview." }
        val url = values["serverUrl"].orEmpty()
        require(url.isNotBlank()) { "Connect a backend in Settings first." }
        val token = tokens.read() ?: error("Connect a backend in Settings first.")
        val response = api(url).post("preview/ai", "Bearer $token", UUID.randomUUID().toString(), body)
        require(response.isSuccessful) { when (response.code()) { 429 -> "Daily AI request limit reached."; 503 -> "The backend AI provider is not configured."; 401 -> "Reconnect your backend account."; else -> "AI preview is unavailable. Your draft is still here." } }
        response.body() ?: error("AI preview was empty.")
    }

    suspend fun learningSettings(enabled: Boolean? = null): JsonObject = withContext(Dispatchers.IO) {
        repository.syncMutex.withLock {
            val url = preferences.values.first()["serverUrl"].orEmpty()
            require(url.isNotBlank()) { "Connect a backend first." }
            validateUrl(url)
            val authorization = "Bearer ${tokens.read() ?: error("Sign in first.")}"
            if (enabled == null) authResult(api(url).get("dataset/summary", authorization))
            else authResult(api(url).post("dataset/consent", authorization, UUID.randomUUID().toString(), JsonObject().apply {
                addProperty("enabled", enabled); addProperty("policyVersion", "learning-v1")
            }))
        }
    }

    // Compatibility entry point for existing integrations; policy is application-owned.
    suspend fun approve(record: Record, jobId: String, steps: List<String>) =
        app.planner.application.usecase.ApproveDevelopment(app.planner.infrastructure.repository.RoomDevelopmentApproval(repository)).approve(record.id, jobId, steps)

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
