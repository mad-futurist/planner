package app.planner.features

import androidx.lifecycle.*
import app.planner.core.Preferences
import app.planner.core.CapturePrivacy
import app.planner.core.database.*
import app.planner.core.model.*
import app.planner.data.PlannerRepository
import app.planner.domain.dayBounds
import app.planner.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PlannerViewModel @Inject constructor(val repository: PlannerRepository, val preferences: Preferences, val sync: SyncEngine) : ViewModel() {
    private val dao = repository.dao
    val draft = MutableStateFlow("")
    val privateCapture = MutableStateFlow(true)
    val captureKind = MutableStateFlow<String?>(null)
    val undoId = MutableStateFlow<String?>(null)
    val focusCapture = MutableStateFlow(0)
    val saving = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val selectedDate = MutableStateFlow(LocalDate.now())
    val taskFilter = MutableStateFlow("today")
    val taskPage = MutableStateFlow(0)
    val ideaSearch = MutableStateFlow("")
    val ideaPage = MutableStateFlow(0)
    val cloudExplanation = MutableStateFlow(false)
    val page = MutableStateFlow(0)
    val localReads = MutableStateFlow<Map<String, String>>(emptyMap())
    private val reload = MutableStateFlow(0)
    private fun <T> observe(key: String, initial: T, source: () -> Flow<T>): StateFlow<T> = reload.flatMapLatest {
        source().onStart { localReads.update { it + (key to "loading") } }.onEach { localReads.update { it + (key to "ready") } }.catch { error ->
            if (error is CancellationException) throw error
            localReads.update { it + (key to "error") }; emit(initial)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)
    val journal = observe("journal", emptyList<RawNote>()) { page.flatMapLatest { dao.journal(50, it * 50).onStart { localReads.update { it + ("journal" to "loading") } } } }
    val calendar = observe("calendar", emptyList<Record>()) { selectedDate.flatMapLatest { day -> dayBounds(day, ZoneId.systemDefault()).let { dao.day(it.day, it.start, it.end).onStart { localReads.update { it + ("calendar" to "loading") } } } } }
    val today = MutableStateFlow(LocalDate.now())
    val todayItems = observe("today", emptyList<Record>()) { today.flatMapLatest { day -> dayBounds(day, ZoneId.systemDefault()).let { dao.day(it.day, it.start, it.end) } } }
    val tomorrowItems = observe("tomorrow", emptyList<Record>()) { today.flatMapLatest { day -> dayBounds(day.plusDays(1), ZoneId.systemDefault()).let { dao.day(it.day, it.start, it.end) } } }
    val completed = observe("completed", emptyList<Record>()) { today.flatMapLatest { day -> dayBounds(day, ZoneId.systemDefault()).let { dao.completed(it.start, it.end) } } }
    val ideas = observe("ideas", emptyList<Record>()) { combine(ideaSearch.debounce(180),ideaPage) { query,page->query to page }.flatMapLatest { (query,page)->dao.ideas(50,page*50,query) } }
    val tasks = observe("tasks", emptyList<Record>()) { combine(taskFilter, taskPage, today) { filter, page, day -> Triple(filter, page, day) }.flatMapLatest { (filter, page, day) -> dao.tasks(filter, day.toString(), dayBounds(day, ZoneId.systemDefault()).end, offset = page * 50) } }
    val taskCounts = observe("taskCounts", emptyMap<String, Int>()) { today.flatMapLatest { day -> dao.taskCounts(day.toString(), dayBounds(day, ZoneId.systemDefault()).end) }.map { it.associate { count -> count.status to count.count } } }
    val profile = observe("profile", ProfileSummary()) { dao.profile() }
    val collections = observe("collections", emptyList<CollectionCount>()) { dao.collections() }
    val taskReview = observe("taskReview", emptyList<Record>()) { today.flatMapLatest { day -> dao.reviewCandidates(day.toString(), dayBounds(day, ZoneId.systemDefault()).start, app.planner.core.isoInstant(day.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant())).map { repository.reviewTasks(day) } } }
    val triage = observe("triage", emptyList<RawNote>()) { dao.triage() }
    val types = observe("types", emptyList<TypeDefinition>()) { dao.types() }
    val metadata = observe("metadata", emptyMap<String, String>()) { dao.observeMeta().map { rows -> rows.associate { it.key to it.value } } }
    val outbox = observe("outbox", emptyList<OutboxOperation>()) { dao.observeOutbox() }
    val processing = observe("processing", emptyMap<String, Int>()) { dao.processingCounts().map { rows -> rows.associate { it.status to it.count } } }
    val settings = observe("settings", emptyMap<String, String>()) { preferences.values }
    val idea = MutableStateFlow<Record?>(null)
    private var draftSave: Job? = null

    init {
        viewModelScope.launch {
            try { val saved = preferences.values.first(); if (draft.value.isEmpty()) draft.value = saved["draft"].orEmpty() }
            catch (e: CancellationException) { throw e } catch (_: Exception) { message.value = "Couldn't restore the draft. New captures stay private until preferences are readable." }
        }
        viewModelScope.launch {
            preferences.values.map { CapturePrivacy.isPrivate(it) }.distinctUntilChanged().catch { error -> if (error is CancellationException) throw error; emit(true) }.collect { privateCapture.value = it }
        }
        viewModelScope.launch {
            reload.flatMapLatest { combine(dao.ideas(), today) { _, date -> date }.map { repository.selectedIdea(it) }.onStart { localReads.update { it + ("idea" to "loading") } }.onEach { selected -> idea.value = selected; localReads.update { it + ("idea" to "ready") } }.catch { error -> if (error is CancellationException) throw error; idea.value = null; localReads.update { it + ("idea" to "error") } } }.collect()
        }
        viewModelScope.launch { while (isActive) { today.value = LocalDate.now(); delay(30_000) } }
    }
    fun setDraft(text: String) {
        draft.value = text
        draftSave?.cancel()
        draftSave = viewModelScope.launch {
            delay(100)
            try { preferences.set("draft", text) } catch (_: java.io.IOException) { message.value = "Couldn't store the draft yet. Keep the app open or save the note." }
        }
    }
    fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: IllegalArgumentException) { message.value = e.message } catch (_: Exception) { message.value = "Couldn't save this change. Please try again." }
    }
    fun capture(manualKind: String? = null) {
        if (saving.value || draft.value.isBlank()) return
        val text = draft.value
        val private = privateCapture.value
        saving.value = true
        action {
            try {
                val started = System.nanoTime()
                repository.capture(text, private, manualKind = if(manualKind == "task_unscheduled") "task" else manualKind, plannedFor = if (manualKind == "task") today.value.toString() else null)
                if (draft.value == text) setDraft("")
                captureKind.value = null
                page.value = 0
                message.value = "Saved on this device"
                runCatching { dao.meta(Meta("lastCaptureMs", ((System.nanoTime() - started) / 1_000_000).toString())); preferences.set("draft", draft.value) }
            } finally { saving.value = false }
        }
    }
    private var observedZone = ZoneId.systemDefault()
    fun refreshDay() {
        today.value = LocalDate.now()
        val currentZone = ZoneId.systemDefault()
        if (currentZone != observedZone) { observedZone = currentZone; reloadLocalQueries() }
    }
    fun reloadLocalQueries() { reload.value += 1 }
    fun setPrivacy(private: Boolean) = action {
        if (!private && preferences.values.first()["cloudConsent"] != CapturePrivacy.CONSENT_VERSION) cloudExplanation.value = true
        else { preferences.capturePrivacy(private); privateCapture.value = private }
    }
    fun allowCloud() = action { preferences.capturePrivacy(false, informedConsent = true); privateCapture.value = false; cloudExplanation.value = false }
    fun filterTasks(value: String) { taskPage.value = 0; taskFilter.value = value }
    fun finish(record: Record) = action { repository.complete(record); undoId.value = record.id; message.value = "Marked done" }
    fun undo() = action { undoId.value?.let { id -> dao.recordById(id)?.let { repository.complete(it, true) } }; undoId.value = null }
}
