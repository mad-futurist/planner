package app.planner.presentation.review

import androidx.lifecycle.*
import app.planner.application.port.OrganizerCommands
import app.planner.application.port.OrganizerQueries
import app.planner.application.port.UserPreferences
import app.planner.domain.model.RawNote
import app.planner.domain.model.Record
import app.planner.presentation.common.FeatureViewModel
import app.planner.presentation.common.UiFeedback
import app.planner.presentation.common.WorkspaceSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.*
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModel @Inject constructor(private val queries: OrganizerQueries, private val commands: OrganizerCommands, private val preferences: UserPreferences, private val selection: WorkspaceSelection, feedback: UiFeedback) : FeatureViewModel(feedback) {
    private val date = selection.state.map { it.today to it.zoneId }.distinctUntilChanged().map { it.first }
    private val notes = observe("triage", emptyList<RawNote>()) { queries.triage() }
    private val tasks = observe("taskReview", emptyList<Record>()) { date.flatMapLatest(queries::review) }
    private val metadata = observe("metadata", emptyMap<String, String>()) { queries.metadata() }
    private val split = MutableStateFlow<Record?>(null)
    private val content = combine(notes, tasks, date, metadata, reads) { notes, tasks, date, metadata, reads -> ReviewUiState(notes, tasks, date, metadata, reads) }
    val uiState = combine(content, split, busy) { content, split, busy -> content.copy(split = split, saving = busy.isNotEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReviewUiState())
    fun plan(task: Record) = execute("plan:${task.id}") { commands.edit(task.copy(plannedFor = selection.state.value.today.toString())) }
    fun snooze(task: Record) = execute("snooze:${task.id}") { commands.snoozeTask(task, selection.state.value.today) }
    fun archive(task: Record) = execute("archive:${task.id}") { commands.edit(task.copy(state = "archived")) }
    fun postpone(noteId: String) = execute("postpone:$noteId") { commands.postponeReview(noteId, selection.state.value.today) }
    fun split(task: Record?) { split.value = task }
    fun createAction(title: String) = execute("split") {
        val origin = split.value ?: return@execute
        commands.linkedTask(origin, title, selection.state.value.today.toString(), app.planner.domain.policy.CapturePrivacy.isPrivate(preferences.values.first()))
        commands.snoozeTask(origin, selection.state.value.today); split.value = null; feedback.show("Action saved")
    }
}
