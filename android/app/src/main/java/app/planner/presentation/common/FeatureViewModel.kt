package app.planner.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
