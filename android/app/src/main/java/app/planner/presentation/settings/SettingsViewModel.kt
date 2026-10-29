package app.planner.presentation.settings

import androidx.lifecycle.viewModelScope
import app.planner.application.port.CloudGateway
import app.planner.application.port.LocalDataGateway
import app.planner.application.port.OrganizerQueries
import app.planner.application.port.UserPreferences
import app.planner.domain.model.OutboxOperation
import app.planner.presentation.common.FeatureViewModel
import app.planner.presentation.common.UiFeedback
import app.planner.presentation.common.WorkspaceSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.Writer
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val queries: OrganizerQueries,
    private val preferences: UserPreferences,
    private val cloud: CloudGateway,
    private val local: LocalDataGateway,
    private val selection: WorkspaceSelection,
    feedback: UiFeedback,
) : FeatureViewModel(feedback) {
    private val settings = observe("settings", emptyMap<String, String>()) { preferences.values }
    private val metadata = observe("metadata", emptyMap<String, String>()) { queries.metadata() }
    private val outbox = observe("outbox", emptyList<OutboxOperation>()) { queries.outbox() }
    private val operations = MutableStateFlow(SettingsOperationState())
    val uiState = combine(settings, metadata, outbox, operations, busy) { settings, metadata, outbox, operations, busy -> SettingsUiState(settings, metadata, outbox, operations, busy) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())
    init { viewModelScope.launch { queries.metadata().map { it["owner"] }.distinctUntilChanged().collect { owner -> operations.update { it.copy(contribution = null, contributionError = null) }; if (owner != null) loadContribution() } } }
    fun setPreference(key: String, value: String) = execute { preferences.set(key, value) }
    fun connect(server: String, invitation: String) = execute("connect") { cloud.connect(server.trim(), invitation); cloud.sync(); feedback.show("Connected") }
    fun sync() = execute("sync") { cloud.sync() }
    fun account(server: String, action: String, email: String, password: String) = execute("account") {
        operations.update { it.copy(accountFeedback = null) }
        val result = cloud.emailAccount(server.trim(), action, email, password)
        if (action == "login") cloud.sync()
        operations.update { it.copy(accountFeedback = result) }
    }
    fun google(server: String, password: String?) = execute("account") { val url = cloud.beginGoogle(server.trim(), password); operations.update { it.copy(browserUrl = url) } }
    fun browserOpened() { operations.update { it.copy(browserUrl = null) } }
    fun signOut() = execute("account") { cloud.signOut(); operations.update { it.copy(accountFeedback = "Signed out") } }
    fun deleteAccount(password: String) = execute("account") { cloud.deleteAccount(password); operations.update { it.copy(accountFeedback = "Account deleted") } }
    fun loadContribution() = contribution(null)
    fun contribution(enabled: Boolean?) = execute("contribution") {
        operations.update { it.copy(contributionError = null) }
        try { val result = cloud.contribution(enabled); operations.update { it.copy(contribution = result) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { operations.update { it.copy(contribution = null, contributionError = "Couldn't confirm your choice. Reconnect and retry.") } }
    }
    fun export(open: () -> Writer?) = execute("export") {
        withContext(Dispatchers.IO) { (open() ?: error("Export destination unavailable")).use { writer -> local.export(writer::write) } }
        feedback.show("Export saved")
    }
    fun clearLocalData() = execute("clear") { local.clear(); selection.journalPage(0); operations.update { it.copy(clearVersion = it.clearVersion + 1) }; feedback.show("Local data cleared") }
    fun retry(operation: OutboxOperation) = execute("retry:${operation.id}") { local.retry(operation) }
    fun keepLocal(operation: OutboxOperation) = execute("retry:${operation.id}") { cloud.keepLocal(operation) }
    fun report(message: String) = feedback.show(message)
}
