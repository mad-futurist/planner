package app.planner.features

import app.planner.application.*
import app.planner.domain.model.*
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RecordUiState(val record: Record? = null, val note: RawNote? = null, val origin: Record? = null, val types: List<TypeDefinition> = emptyList(), val saving: Boolean = false, val closeVersion: Int = 0, val sessionId: Int = 0, val error: String? = null)
@HiltViewModel
class RecordViewModel @Inject constructor(private val queries: OrganizerQueries, private val commands: OrganizerCommands, feedback: UiFeedback) : FeatureViewModel(feedback) {
    private val details = MutableStateFlow(RecordUiState())
    private val types = observe("types", emptyList<TypeDefinition>()) { queries.types() }
    val uiState = combine(details, types, busy) { details, types, busy -> details.copy(types = types, saving = busy.isNotEmpty()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordUiState())
    private var generation = 0
    fun open(id: String): Int {
        val version = ++generation
        details.value = RecordUiState(sessionId = version)
        execute {
            try {
                val record = queries.record(id)
                require(record != null) { "This item is no longer available." }
                val note = queries.note(record.sourceNoteId)
                val fields = com.google.gson.JsonParser.parseString(record.fieldsJson).asJsonObject
                val originId = (fields["developedFrom"] ?: fields["splitFrom"])?.takeIf { it.isJsonPrimitive }?.asString
                val origin = originId?.let { queries.record(it) }
                if (generation == version) details.update { it.copy(record = record, note = note, origin = origin) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (generation == version) details.update { it.copy(error = operationError(error)) } }
        }
        return version
    }
    fun save(record: Record, session: Int = generation) {
        if (session != generation) return
        val version = generation
        details.update { it.copy(error = null) }
        execute("record-save:${record.id}") {
            try { commands.edit(record); if (version == generation) details.update { it.copy(closeVersion = it.closeVersion + 1) } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (version == generation) details.update { it.copy(error = operationError(error)) } }
        }
    }
    fun complete(record: Record, session: Int = generation) {
        if (session != generation) return
        val version = generation
        details.update { it.copy(error = null) }
        execute("record-save:${record.id}") {
            try { commands.complete(record, record.state == "completed"); if (version == generation) details.update { it.copy(closeVersion = it.closeVersion + 1) } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (version == generation) details.update { it.copy(error = operationError(error)) } }
        }
    }
}
