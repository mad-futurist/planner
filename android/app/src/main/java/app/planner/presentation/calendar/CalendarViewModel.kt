package app.planner.presentation.calendar

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
