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
class Preferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val overlayDraft = MutableStateFlow<String?>(null)
    val values = context.plannerPreferences.data.map { p -> p.asMap().mapKeys { it.key.name }.mapValues { it.value.toString() } }
    @OptIn(FlowPreview::class)
    private fun watchDraft() = scope.launch {
        overlayDraft.filterNotNull().debounce(120).collect { text ->
            try { context.plannerPreferences.edit { if (overlayDraft.value == text) it[stringPreferencesKey("overlayDraft")] = text } } catch (_: java.io.IOException) { /* Explicit close/save still flushes persistence. */ }
        }
    }
    init { watchDraft() }
    fun saveOverlayDraft(text: String) { overlayDraft.value = text }
    fun withLatest(action: (Map<String, String>) -> Unit) { scope.launch { runCatching { action(values.first()) } } }
    suspend fun set(key: String, value: String) {
        if (key == "overlayDraft") overlayDraft.value = value
        context.plannerPreferences.edit { if (key != "overlayDraft" || overlayDraft.value == value) it[stringPreferencesKey(key)] = value }
    }
}
