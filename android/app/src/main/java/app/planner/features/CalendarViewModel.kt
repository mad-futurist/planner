package app.planner.features

import androidx.lifecycle.*
import app.planner.application.*
import app.planner.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class CalendarUiState(
    val date: LocalDate = LocalDate.now(),
    val records: List<Record> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val counts: Map<LocalDate, Int> = emptyMap(),
    val reads: Map<String, String> = emptyMap(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CalendarViewModel @Inject constructor(
    private val queries: OrganizerQueries,
    commands: OrganizerCommands,
    selection: WorkspaceSelection,
    private val preferences: UserPreferences,
    feedback: UiFeedback,
) : RecordActionsViewModel(commands, selection, preferences, feedback) {
    private val date = selection.state.map { it.selectedDate to it.zoneId }.distinctUntilChanged().map { it.first }
    private val records = observe("calendar", emptyList<Record>()) { date.flatMapLatest(queries::day) }
    private val settings = observe("settings", emptyMap<String, String>()) { preferences.values }
    private val counts = observe("dayCounts", emptyMap<LocalDate, Int>()) {
        combine(date, settings) { selected, settings -> selected.with(java.time.temporal.TemporalAdjusters.previousOrSame(if (settings["weekStart"] == "monday") DayOfWeek.MONDAY else DayOfWeek.SUNDAY)) }.distinctUntilChanged().flatMapLatest { first ->
            val days = (0..6).map { first.plusDays(it.toLong()) }
            combine(days.map(queries::dayCount)) { counts -> days.zip(counts.toList()).toMap() }
        }
    }

    val uiState = combine(date, records, settings, counts, reads) { date, records, settings, counts, reads -> CalendarUiState(date, records, settings, counts, reads) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())
}
