package app.planner.features

import androidx.lifecycle.*
import app.planner.application.*
import app.planner.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class JournalEntry(val note: RawNote, val records: List<Record>)
data class JournalUiState(val entries: List<JournalEntry> = emptyList(), val page: Int = 0, val reads: Map<String, String> = emptyMap())
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
