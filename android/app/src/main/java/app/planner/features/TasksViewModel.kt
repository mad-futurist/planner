package app.planner.features

import androidx.lifecycle.*
import app.planner.application.*
import app.planner.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class TasksUiState(
    val tasks: List<Record> = emptyList(),
    val filter: String = "today",
    val counts: Map<String, Int> = emptyMap(),
    val page: Int = 0,
    val reads: Map<String, String> = emptyMap(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TasksViewModel @Inject constructor(
    private val queries: OrganizerQueries,
    commands: OrganizerCommands,
    selection: WorkspaceSelection,
    private val preferences: UserPreferences,
    feedback: UiFeedback,
) : RecordActionsViewModel(commands, selection, preferences, feedback) {
    private data class TaskQuery(val filter: String, val date: LocalDate, val page: Int, val zoneId: String)
    private val tasks = observe("tasks", emptyList<Record>()) { selection.state.map { TaskQuery(it.taskFilter, it.today, it.taskPage, it.zoneId) }.distinctUntilChanged().flatMapLatest { query -> queries.tasks(query.filter, query.date, query.page) } }
    private val counts = observe("taskCounts", emptyMap<String, Int>()) { selection.state.map { it.today to it.zoneId }.distinctUntilChanged().map { it.first }.flatMapLatest(queries::taskCounts) }

    val uiState = combine(tasks, selection.state, counts, reads) { tasks, selection, counts, reads -> TasksUiState(tasks, selection.taskFilter, counts, selection.taskPage, reads) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TasksUiState())
    fun filterTasks(value: String) = selection.filterTasks(value)
    fun page(value: Int) = selection.taskPage(value)
}
