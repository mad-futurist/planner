package app.planner.presentation.common

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
