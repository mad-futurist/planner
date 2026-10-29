package app.planner.features

import androidx.lifecycle.*
import app.planner.application.*
import app.planner.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class ProfileUiState(
    val profile: ProfileSummary = ProfileSummary(),
    val collections: List<CollectionCount> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val reads: Map<String, String> = emptyMap(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ProfileViewModel @Inject constructor(
    private val queries: OrganizerQueries,
    commands: OrganizerCommands,
    selection: WorkspaceSelection,
    private val preferences: UserPreferences,
    feedback: UiFeedback,
) : RecordActionsViewModel(commands, selection, preferences, feedback) {
    private val profile = observe("profile", ProfileSummary()) { queries.profile() }
    private val collections = observe("collections", emptyList<CollectionCount>()) { queries.collections() }
    private val metadata = observe("metadata", emptyMap<String, String>()) { queries.metadata() }

    val uiState = combine(profile, collections, metadata, reads) { profile, collections, metadata, reads -> ProfileUiState(profile, collections, metadata, reads) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())
}
