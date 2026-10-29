package app.planner.presentation.interpretation

import androidx.lifecycle.viewModelScope
import app.planner.application.port.OrganizerCommands
import app.planner.application.port.OrganizerQueries
import app.planner.presentation.common.FeatureViewModel
import app.planner.presentation.common.UiFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class InterpretationViewModel @Inject constructor(private val queries: OrganizerQueries, private val commands: OrganizerCommands, feedback: UiFeedback) : FeatureViewModel(feedback) {
    private val details = MutableStateFlow(InterpretationUiState())
    val uiState = combine(details, busy) { details, busy -> details.copy(saving = busy.isNotEmpty()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InterpretationUiState())
    private var generation = 0
    private var submitted = false
    fun open(id: String): Int {
        val version = ++generation; submitted = false
        details.value = InterpretationUiState(sessionId = version)
        execute {
            try {
                val note = queries.note(id)
                require(note != null) { "This note is no longer available." }
                val history = queries.history(id)
                if (version == generation) details.update { it.copy(note = note, history = history) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (version == generation) details.update { it.copy(error = operationError(error)) } }
        }
        return version
    }
    fun save(kind: String, title: String, planned: String?, session: Int = generation) {
        if (submitted || session != generation) return
        val note = details.value.note ?: return
        val version = generation
        submitted = true
        details.update { it.copy(error = null) }
        execute("interpretation-save:$version") {
            try { commands.manualInterpretation(note, kind, title, planned.takeIf { kind == "task" }); if (version == generation) details.update { it.copy(closeVersion = it.closeVersion + 1) } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (version == generation) { submitted = false; details.update { it.copy(error = operationError(error)) } } }
        }
    }
}
