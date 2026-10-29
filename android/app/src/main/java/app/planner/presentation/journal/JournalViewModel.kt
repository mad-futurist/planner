package app.planner.presentation.journal

import androidx.lifecycle.*
import app.planner.application.port.OrganizerCommands
import app.planner.application.port.OrganizerQueries
import app.planner.domain.model.RawNote
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
class JournalViewModel @Inject constructor(private val queries: OrganizerQueries, private val commands: OrganizerCommands, private val selection: WorkspaceSelection, feedback: UiFeedback) : FeatureViewModel(feedback) {
    private val entries = observe("journal", emptyList<JournalEntry>()) {
        selection.state.map { it.journalPage }.distinctUntilChanged().flatMapLatest(queries::journal).flatMapLatest { notes ->
            if (notes.isEmpty()) flowOf(emptyList()) else combine(notes.map { note -> queries.records(note.id).map { JournalEntry(note, it) } }) { it.toList() }
        }
    }
    val uiState = combine(entries, selection.state, reads) { entries, selection, reads -> JournalUiState(entries, selection.journalPage, reads) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalUiState())
    fun page(value: Int) = selection.journalPage(value)
    fun reprocess(note: RawNote) = execute("reprocess:${note.id}") { commands.reprocess(note) }
}
