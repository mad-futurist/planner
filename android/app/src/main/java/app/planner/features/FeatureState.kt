package app.planner.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.application.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalDate

/** A snackbar owns its undo payload; dismissing an older message cannot clear a newer one. */
data class FeedbackMessage(val id: Long, val text: String, val undoRecordId: String? = null)
@Singleton
class UiFeedback @Inject constructor() {
    private val current = MutableStateFlow<FeedbackMessage?>(null)
    val state = current.asStateFlow()
    private var sequence = 0L
    fun show(text: String, undoRecordId: String? = null) { current.value = FeedbackMessage(++sequence, text, undoRecordId) }
    fun dismiss(id: Long) { current.update { if (it?.id == id) null else it } }
}

data class WorkspaceSelectionState(
    val today: LocalDate = LocalDate.now(), val selectedDate: LocalDate = today,
    val taskFilter: String = "today", val taskPage: Int = 0,
    val ideaSearch: String = "", val ideaPage: Int = 0, val journalPage: Int = 0,
    val zoneId: String = java.time.ZoneId.systemDefault().id,
)
/** Presentation coordination only; no repositories or business data are cached here. */
@Singleton
class WorkspaceSelection @Inject constructor() {
    private val current = MutableStateFlow(WorkspaceSelectionState())
    val state = current.asStateFlow()
    fun selectDate(date: LocalDate) { current.update { it.copy(selectedDate = date) } }
    fun filterTasks(filter: String) { require(filter in setOf("today", "upcoming", "someday", "all", "completed", "archived")); current.update { it.copy(taskFilter = filter, taskPage = 0) } }
    fun taskPage(page: Int) { current.update { it.copy(taskPage = page.coerceAtLeast(0)) } }
    fun searchIdeas(query: String) { current.update { it.copy(ideaSearch = query, ideaPage = 0) } }
    fun ideaPage(page: Int) { current.update { it.copy(ideaPage = page.coerceAtLeast(0)) } }
    fun journalPage(page: Int) { current.update { it.copy(journalPage = page.coerceAtLeast(0)) } }
    fun refreshDay() { current.update { it.copy(today = LocalDate.now(), zoneId = java.time.ZoneId.systemDefault().id) } }
    internal fun restore(value: WorkspaceSelectionState) { current.value = value }
}

/** Shared lifecycle/error mechanics only. Feature state and business capabilities stay in each VM. */
abstract class FeatureViewModel(protected val feedback: UiFeedback) : ViewModel() {
    private val reload = MutableStateFlow(0)
    protected val reads = MutableStateFlow<Map<String, String>>(emptyMap())
    fun reloadLocalQueries() { reload.value += 1 }
    @OptIn(ExperimentalCoroutinesApi::class)
    protected fun <T> observe(key: String, initial: T, source: () -> Flow<T>): StateFlow<T> = reload.flatMapLatest {
        source().onStart { reads.update { it + (key to "loading") } }
            .onEach { reads.update { it + (key to "ready") } }
            .catch { e -> if (e is CancellationException) throw e; reads.update { it + (key to "error") }; emit(initial) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)
    protected fun operationError(error: Exception): String =
        if (error is IllegalArgumentException) error.message ?: "Check this change and try again." else "Couldn't complete this action. Try again."
    private val running = mutableSetOf<String>()
    protected val busy = MutableStateFlow<Set<String>>(emptySet())
    protected fun execute(key: String? = null, block: suspend () -> Unit) {
        if (key != null && !running.add(key)) return
        if (key != null) busy.value = running.toSet()
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { feedback.show(e.message ?: "Couldn't save this change. Please try again.") }
            finally { if (key != null) { running.remove(key); busy.value = running.toSet() } }
        }
    }
}
