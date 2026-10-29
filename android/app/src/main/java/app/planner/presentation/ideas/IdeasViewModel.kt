package app.planner.presentation.ideas

import androidx.lifecycle.*
import app.planner.application.port.OrganizerCommands
import app.planner.application.port.OrganizerQueries
import app.planner.application.port.UserPreferences
import app.planner.domain.model.Record
import app.planner.presentation.common.RecordActionsViewModel
import app.planner.presentation.common.UiFeedback
import app.planner.presentation.common.WorkspaceSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.*
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class IdeasViewModel @Inject constructor(
    private val queries: OrganizerQueries,
    commands: OrganizerCommands,
    selection: WorkspaceSelection,
    private val preferences: UserPreferences,
    feedback: UiFeedback,
) : RecordActionsViewModel(commands, selection, preferences, feedback) {
    private val ideas = observe("ideas", emptyList<Record>()) { selection.state.map { it.ideaSearch to it.ideaPage }.distinctUntilChanged().debounce(180).flatMapLatest { (query, page) -> queries.ideas(query, page) } }

    val uiState = combine(ideas, selection.state, reads) { ideas, selection, reads -> IdeasUiState(ideas, selection.ideaSearch, selection.ideaPage, reads) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IdeasUiState())
    fun search(value: String) = selection.searchIdeas(value)
    fun page(value: Int) = selection.ideaPage(value)
}
