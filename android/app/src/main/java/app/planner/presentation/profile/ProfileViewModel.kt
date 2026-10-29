package app.planner.presentation.profile

import androidx.lifecycle.*
import app.planner.application.port.OrganizerCommands
import app.planner.application.port.OrganizerQueries
import app.planner.application.port.UserPreferences
import app.planner.domain.model.CollectionCount
import app.planner.domain.model.ProfileSummary
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
