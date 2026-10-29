package app.planner.features

import androidx.lifecycle.*
import app.planner.application.*
import app.planner.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class TodayUiState(
    val records: List<Record> = emptyList(),
    val tomorrow: List<Record> = emptyList(),
    val completed: List<Record> = emptyList(),
    val idea: Record? = null,
    val review: List<Record> = emptyList(),
    val profile: ProfileSummary = ProfileSummary(),
    val date: LocalDate = LocalDate.now(),
    val reads: Map<String, String> = emptyMap(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TodayViewModel @Inject constructor(
    private val queries: OrganizerQueries,
    commands: OrganizerCommands,
    selection: WorkspaceSelection,
    private val preferences: UserPreferences,
    feedback: UiFeedback,
) : RecordActionsViewModel(commands, selection, preferences, feedback) {
    private val date = selection.state.map { it.today to it.zoneId }.distinctUntilChanged().map { it.first }
    private val records = observe("today", emptyList<Record>()) { date.flatMapLatest(queries::day) }
    private val tomorrow = observe("tomorrow", emptyList<Record>()) { date.flatMapLatest { queries.day(it.plusDays(1)) } }
    private val completed = observe("completed", emptyList<Record>()) { date.flatMapLatest(queries::completed) }
    private val idea = observe<Record?>("idea", null) { combine(queries.ideas(), date) { _, day -> day }.map { queries.selectedIdea(it) } }
    private val review = observe("taskReview", emptyList<Record>()) { date.flatMapLatest(queries::review) }
    private val profile = observe("profile", ProfileSummary()) { queries.profile() }

    private data class Schedule(val records: List<Record>, val tomorrow: List<Record>, val completed: List<Record>, val idea: Record?)
    private val schedule = combine(records, tomorrow, completed, idea) { records, tomorrow, completed, idea -> Schedule(records, tomorrow, completed, idea) }
    val uiState = combine(schedule, review, profile, date, reads) { schedule, review, profile, date, reads -> TodayUiState(schedule.records, schedule.tomorrow, schedule.completed, schedule.idea, review, profile, date, reads) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState())
}
