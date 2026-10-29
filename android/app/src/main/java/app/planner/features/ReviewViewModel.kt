package app.planner.features

import androidx.lifecycle.*
import app.planner.application.*
import app.planner.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class ReviewUiState(val notes: List<RawNote> = emptyList(), val tasks: List<Record> = emptyList(), val date: LocalDate = LocalDate.now(), val metadata: Map<String, String> = emptyMap(), val reads: Map<String, String> = emptyMap(), val split: Record? = null, val saving: Boolean = false)
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
        commands.linkedTask(origin, title, selection.state.value.today.toString(), app.planner.core.CapturePrivacy.isPrivate(preferences.values.first()))
        commands.snoozeTask(origin, selection.state.value.today); split.value = null; feedback.show("Action saved")
    }
}
