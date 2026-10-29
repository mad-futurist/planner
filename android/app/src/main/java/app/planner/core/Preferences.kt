package app.planner.core

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

val Context.plannerPreferences by preferencesDataStore("planner_preferences")

@Singleton
class Preferences @Inject constructor(@ApplicationContext private val context: Context) : app.planner.application.UserPreferences {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val overlayDraft = MutableStateFlow<String?>(null)
    override val values = context.plannerPreferences.data.map { p -> p.asMap().mapKeys { it.key.name }.mapValues { it.value.toString() } }
    @OptIn(FlowPreview::class)
    private fun watchDraft() = scope.launch {
        overlayDraft.filterNotNull().debounce(120).collect { text ->
            try { context.plannerPreferences.edit { if (overlayDraft.value == text) it[stringPreferencesKey("overlayDraft")] = text } } catch (_: java.io.IOException) { /* Explicit close/save still flushes persistence. */ }
        }
    }
    init { watchDraft() }
    fun saveOverlayDraft(text: String) { overlayDraft.value = text }
    fun withLatest(action: (Map<String, String>) -> Unit) { scope.launch { runCatching { action(values.first()) } } }
    override suspend fun capturePrivacy(private: Boolean, informedConsent: Boolean) {
        context.plannerPreferences.edit {
            require(private || informedConsent || it[stringPreferencesKey("cloudConsent")] == CapturePrivacy.CONSENT_VERSION) { "Cloud AI needs your explicit permission first." }
            if (informedConsent) it[stringPreferencesKey("cloudConsent")] = CapturePrivacy.CONSENT_VERSION
            it[stringPreferencesKey("privateCapture")] = private.toString()
        }
    }
    override suspend fun set(key: String, value: String) {
        if (key == "overlayDraft") overlayDraft.value = value
        context.plannerPreferences.edit { if (key != "overlayDraft" || overlayDraft.value == value) it[stringPreferencesKey(key)] = value }
    }
    override suspend fun captureDraft(text: String, hints: String) {
        context.plannerPreferences.edit { it[stringPreferencesKey("draft")] = text; it[stringPreferencesKey("draftHints")] = hints }
    }
    suspend fun googleSession(url: String = "", state: String = "", verifier: String = "") {
        context.plannerPreferences.edit {
            it[stringPreferencesKey("googleUrl")] = url; it[stringPreferencesKey("googleState")] = state; it[stringPreferencesKey("googleVerifier")] = verifier
            it[stringPreferencesKey("googleStartedAt")] = if (state.isEmpty()) "" else System.currentTimeMillis().toString()
        }
    }
}
