package app.planner.presentation.shell

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.planner.application.port.CloudGateway
import app.planner.application.port.OrganizerCommands
import app.planner.application.port.OrganizerQueries
import app.planner.application.port.UserPreferences
import app.planner.presentation.common.FeatureViewModel
import app.planner.presentation.common.FeedbackMessage
import app.planner.presentation.common.UiFeedback
import app.planner.presentation.common.WorkspaceSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Activity shell only. Feature business state belongs to feature ViewModels. */
@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val queries: OrganizerQueries,
    private val commands: OrganizerCommands,
    private val preferences: UserPreferences,
    private val cloud: CloudGateway,
    private val selection: WorkspaceSelection,
    feedback: UiFeedback,
    saved: SavedStateHandle,
) : FeatureViewModel(feedback) {
    private val settings = observe("settings", emptyMap<String, String>()) { preferences.values }
    private val metadata = observe("metadata", emptyMap<String, String>()) { queries.metadata() }
    private val processing = observe("processing", emptyMap<String, Int>()) { queries.processing() }
    val uiState = combine(settings, metadata, processing, feedback.state, selection.state) { settings, metadata, processing, feedback, selection -> ShellUiState(settings, metadata, processing, feedback, selection) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShellUiState())
    init {
        val current = selection.state.value
        selection.restore(current.copy(
            selectedDate = saved.get<String>("calendarDate")?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: current.selectedDate,
            taskFilter = saved.get<String>("taskFilter")?.takeIf { it in setOf("today", "upcoming", "someday", "all", "completed", "archived") } ?: current.taskFilter,
            taskPage = saved.get<Int>("taskPage")?.coerceAtLeast(0) ?: 0,
            ideaSearch = saved.get<String>("ideaSearch").orEmpty(), ideaPage = saved.get<Int>("ideaPage")?.coerceAtLeast(0) ?: 0,
            journalPage = saved.get<Int>("journalPage")?.coerceAtLeast(0) ?: 0,
        ))
        viewModelScope.launch { selection.state.collect { state ->
            saved["calendarDate"] = state.selectedDate.toString(); saved["taskFilter"] = state.taskFilter; saved["taskPage"] = state.taskPage
            saved["ideaSearch"] = state.ideaSearch; saved["ideaPage"] = state.ideaPage; saved["journalPage"] = state.journalPage
        } }
    }
    fun refreshDay() = selection.refreshDay()
    fun dismissMessage(id: Long) = feedback.dismiss(id)
    fun undo(message: FeedbackMessage) = execute("undo:${message.id}") { message.undoRecordId?.let { queries.record(it)?.let { record -> commands.complete(record, true) } }; feedback.dismiss(message.id) }
    fun finishGoogle(callback: String) = execute("google") { cloud.finishGoogle(callback); feedback.show("Signed in with Google") }
    fun report(message: String) = feedback.show(message)
    suspend fun capturePreferences() = preferences.values.first()
}
