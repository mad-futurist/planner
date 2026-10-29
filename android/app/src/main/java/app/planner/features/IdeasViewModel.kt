package app.planner.features

import androidx.lifecycle.*
import app.planner.application.*
import app.planner.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class IdeasUiState(
    val ideas: List<Record> = emptyList(),
    val query: String = "",
    val page: Int = 0,
    val reads: Map<String, String> = emptyMap(),
)

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
