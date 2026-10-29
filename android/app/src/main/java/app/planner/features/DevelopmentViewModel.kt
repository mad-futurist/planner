package app.planner.features

import app.planner.application.*
import app.planner.domain.model.*
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DevelopmentUiState(val record: Record? = null, val status: String = "unavailable", val jobId: String? = null, val steps: List<String> = emptyList(), val saving: Boolean = false, val closeVersion: Int = 0, val sessionId: Int = 0, val error: String? = null)
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DevelopmentViewModel @Inject constructor(private val queries: OrganizerQueries, private val cloud: CloudGateway, private val approve: ApproveDevelopment, feedback: UiFeedback) : FeatureViewModel(feedback) {
    private val details = MutableStateFlow(DevelopmentUiState())
    private val selected = MutableStateFlow<String?>(null)
    private val job = observe<String?>("development", null) { selected.flatMapLatest { id -> if (id == null) flowOf(null) else queries.job(id) } }
    val uiState = combine(details, job, busy) { details, value, busy ->
        val parsed = value?.let { com.google.gson.JsonParser.parseString(it).asJsonObject }
        val status = parsed?.get("status")?.asString ?: "unavailable"
        details.copy(status = status, jobId = parsed?.get("id")?.asString, steps = if (status == "complete") parsed?.getAsJsonObject("result")?.getAsJsonArray("steps")?.map { it.asString }.orEmpty() else emptyList(), saving = busy.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DevelopmentUiState())
    private var generation = 0
    fun open(id: String): Int {
        val version = ++generation
        selected.value = id
        details.value = DevelopmentUiState(sessionId = version)
        execute("develop-open:$version") {
            try {
                val record = queries.record(id)
                require(record != null) { "This idea is no longer available." }
                if (generation != version) return@execute
                details.update { it.copy(record = record) }
                cloud.develop(record)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (version == generation) details.update { it.copy(error = operationError(error)) } }
        }
        return version
    }
    fun retry(session: Int = generation) {
        if (session != generation) return
        val id = selected.value ?: return
        val version = generation
        details.update { it.copy(error = null) }
        execute("develop") {
            try {
                val record = queries.record(id)
                require(record != null) { "This idea is no longer available." }
                if (version != generation) return@execute
                details.update { it.copy(record = record) }; cloud.develop(record)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (version == generation) details.update { it.copy(error = operationError(error)) } }
        }
    }
    fun approve(steps: List<String>, session: Int = generation) {
        val state = uiState.value
        if (session != generation || state.sessionId != generation) return
        val record = state.record ?: return
        val job = state.jobId ?: return
        val version = generation
        details.update { it.copy(error = null) }
        execute("approve:${record.id}") {
            try { approve.approve(record.id, job, steps); if (version == generation) details.update { it.copy(closeVersion = it.closeVersion + 1) } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (version == generation) details.update { it.copy(error = operationError(error)) } }
        }
    }
}
