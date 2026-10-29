package app.planner.presentation.capture

import androidx.lifecycle.*
import app.planner.domain.interpretation.CaptureDoubt
import app.planner.domain.interpretation.CaptureHint
import app.planner.domain.interpretation.CaptureInterpreter
import app.planner.domain.interpretation.CapturePreview
import app.planner.domain.interpretation.InterpretationHints
import app.planner.domain.interpretation.PreviewRecord
import app.planner.domain.policy.CaptureContext
import app.planner.domain.policy.CapturePrivacy
import app.planner.domain.policy.taskBucket
import app.planner.presentation.common.FeatureViewModel
import app.planner.presentation.common.UiFeedback
import app.planner.presentation.common.WorkspaceSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.*
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CaptureViewModel @Inject constructor(
    private val captures: app.planner.application.usecase.CaptureUseCase,
    private val preferences: app.planner.application.port.UserPreferences,
    private val cloud: app.planner.application.port.CloudGateway,
    private val codec: app.planner.application.port.InterpretationCodec,
    private val commands: app.planner.application.port.OrganizerCommands,
    private val interpreter: CaptureInterpreter,
    private val selection: WorkspaceSelection,
    feedback: UiFeedback,
) : FeatureViewModel(feedback) {
    private val draft = MutableStateFlow("")
    private val privateCapture = MutableStateFlow(true)
    private val captureKind = MutableStateFlow<String?>(null)
    private val captureDeadline = MutableStateFlow<String?>(null)
    private val intentQuestion = MutableStateFlow(false)
    private val choosingIntent = intentQuestion.asStateFlow()
    private val captureHints = MutableStateFlow(InterpretationHints())
    private val clarification = MutableStateFlow<CaptureDoubt?>(null)
    private val clarificationSeed = MutableStateFlow<PreviewRecord?>(null)
    data class PendingSubmission(val kind: String?)
    private val pendingSubmit = MutableStateFlow<PendingSubmission?>(null)
    private val pendingSubmission = pendingSubmit.asStateFlow()
    private val aiDraftPreview = MutableStateFlow<app.planner.application.model.AiPreview?>(null)
    private val aiPreviewBusy = MutableStateFlow(false)
    private val aiPreviewError = MutableStateFlow<String?>(null)
    private var aiPreviewJob: Job? = null
    private var aiPreviewVersion = 0
    private val previewReference = MutableStateFlow(CaptureContext())
    private val capturePreview = combine(draft, captureHints, captureKind, previewReference) { text, hints, kind, reference -> Triple(text, hints, kind) to reference }
        .debounce(120).mapLatest { (input, reference) ->
            val (text, hints, kind) = input
            withContext(Dispatchers.Default) { if (kind == "raw_note") CapturePreview() else runCatching { interpreter.preview(text, reference, hints, previewKind(kind)) }.getOrDefault(CapturePreview()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CapturePreview())
    private val today get() = selection.state.value.today
    private val cloudExplanation = MutableStateFlow(false)
    private val focusCapture = MutableStateFlow(0)
    private val saving = MutableStateFlow(false)
    private var draftSave: Job? = null

    private val input = combine(draft, privateCapture, captureKind, captureDeadline, intentQuestion) { draft, private, kind, deadline, choosing -> CaptureUiState(draft = draft, privateCapture = private, captureKind = kind, captureDeadline = deadline, choosingIntent = choosing) }
    private val clarified = combine(input, captureHints, clarification, clarificationSeed, pendingSubmit) { input, hints, doubt, seed, pending -> input.copy(captureHints = hints, clarification = doubt, clarificationSeed = seed, pendingSubmission = pending) }
    private val interpreted = combine(clarified, aiDraftPreview, aiPreviewBusy, aiPreviewError, capturePreview) { input, ai, busy, error, preview -> input.copy(aiDraftPreview = ai, aiPreviewBusy = busy, aiPreviewError = error, capturePreview = preview) }
    val uiState = combine(interpreted, cloudExplanation, focusCapture, saving) { input, explanation, focus, saving -> input.copy(cloudExplanation = explanation, focusCapture = focus, saving = saving) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CaptureUiState())

    init {
        viewModelScope.launch {
            try { val saved = preferences.values.first(); if (draft.value.isEmpty()) {
                val restored = saved["draft"].orEmpty()
                val hints = runCatching { codec.decode(saved["draftHints"] ?: "{}") }.getOrNull()
                draft.value = restored
                captureHints.value = if (hints != null && runCatching { interpreter.preview(restored, previewReference.value, hints) }.isSuccess) hints else InterpretationHints()
            } }
            catch (e: CancellationException) { throw e } catch (_: Exception) { feedback.show("Couldn't restore the draft. New captures stay private until preferences are readable.") }
        }
        viewModelScope.launch {
            preferences.values.map { CapturePrivacy.isPrivate(it) }.distinctUntilChanged().catch { error -> if (error is CancellationException) throw error; emit(true) }.collect { privateCapture.value = it; if (it) invalidateAiPreview() }
        }
        viewModelScope.launch { while (isActive) { previewReference.value = CaptureContext(); delay(30_000) } }

    }
    fun setDraft(text: String) {
        if (text != draft.value) { captureHints.value = InterpretationHints(); captureDeadline.value = null; intentQuestion.value = false; clarification.value = null; clarificationSeed.value = null; pendingSubmit.value = null; invalidateAiPreview() }
        draft.value = text
        persistCaptureDraft()
    }
    private fun persistCaptureDraft() {
        draftSave?.cancel()
        draftSave = viewModelScope.launch {
            delay(100)
            try { preferences.captureDraft(draft.value, codec.encode(captureHints.value)) } catch (_: java.io.IOException) { feedback.show("Couldn't store the draft yet. Keep the app open or save the note.") }
        }
    }
    fun clarify(hint: CaptureHint) {
        val remaining = captureHints.value.clarifications.filterNot { it.start < hint.end && it.end > hint.start }
        val updated = InterpretationHints(clarifications = remaining + hint)
        require(hint.date != null || hint.time != null || hint.kind == "literal")
        interpreter.preview(draft.value, CaptureContext(), updated)
        captureHints.value = updated; clarification.value = null; invalidateAiPreview(); persistCaptureDraft()
        pendingSubmit.value?.let { capture(it.kind) }
    }
    private fun openClarification(doubt: CaptureDoubt, preview: CapturePreview) {
        clarificationSeed.value = preview.records.firstOrNull { doubt.start >= it.start && doubt.end <= it.end }
        clarification.value = doubt
    }
    fun showClarification(doubt: CaptureDoubt) {
        val text = draft.value
        val hints = captureHints.value
        val kind = previewKind(pendingSubmit.value?.kind ?: captureKind.value)
        execute {
            val preview = withContext(Dispatchers.Default) { interpreter.preview(text, CaptureContext(), hints, kind) }
            if (draft.value == text && captureHints.value == hints) openClarification(doubt, preview)
        }
    }
    fun cancelClarification() { clarification.value = null; clarificationSeed.value = null; pendingSubmit.value = null; intentQuestion.value = false }
    fun selectCaptureKind(kind: String?) {
        require(kind == null || kind in setOf("task", "task_deadline", "task_unscheduled", "idea", "event", "information", "raw_note"))
        captureKind.value = kind; captureDeadline.value = null; cancelClarification(); invalidateAiPreview()
    }
    fun chooseIntent(kind: String, deadline: String? = null) {
        require(kind in setOf("task_deadline", "task_unscheduled", "idea"))
        if (kind == "task_deadline") require(deadline != null && runCatching { LocalDate.parse(deadline) }.isSuccess)
        val resume = pendingSubmit.value != null
        selectCaptureKind(kind); captureDeadline.value = deadline
        if (resume) capture(kind)
    }
    fun chooseEventDay(record: PreviewRecord, day: LocalDate) = execute {
        require(record.time != null && record.end <= draft.value.length && draft.value.substring(record.start, record.end) == record.title) { "The draft changed. Choose its date again." }
        clarify(CaptureHint(record.start, record.end, record.title, "datetime", day.toString(), record.time))
    }
    fun resetClarifications() { captureHints.value = InterpretationHints(); pendingSubmit.value = null; invalidateAiPreview(); persistCaptureDraft() }
    private fun invalidateAiPreview() { aiPreviewVersion++; aiPreviewJob?.cancel(); aiDraftPreview.value = null; aiPreviewError.value = null; aiPreviewBusy.value = false }
    fun requestAiPreview() {
        if (aiPreviewBusy.value || privateCapture.value || draft.value.isBlank()) return
        val text = draft.value
        val hints = captureHints.value
        val reference = CaptureContext()
        val version = ++aiPreviewVersion
        aiPreviewBusy.value = true; aiPreviewError.value = null
        aiPreviewJob = viewModelScope.launch {
            try {
                val value = cloud.preview(text, hints, reference)
                if (version == aiPreviewVersion && draft.value == text && captureHints.value == hints && !privateCapture.value) aiDraftPreview.value = value
            } catch (e: CancellationException) { throw e } catch (e: Exception) { if (version == aiPreviewVersion) aiPreviewError.value = e.message ?: "AI preview unavailable" }
            finally { if (version == aiPreviewVersion) aiPreviewBusy.value = false }
        }
    }
    private fun previewKind(kind: String?) = when (kind) { "task_unscheduled", "task_deadline" -> "task"; "raw_note" -> null; else -> kind }
    fun interpretationPreview(hints: InterpretationHints) = interpreter.preview(draft.value, CaptureContext(), hints, previewKind(pendingSubmit.value?.kind ?: captureKind.value))
    fun createEvent() = capture("event")
    fun capture(manualKind: String? = null, noteOnly: Boolean = false) {
        if (saving.value || draft.value.isBlank()) return
        val text = draft.value
        val private = privateCapture.value
        val hints = captureHints.value
        val originalKind = captureKind.value
        val rawOnly = noteOnly || manualKind == "raw_note"
        val deadline = captureDeadline.value
        saving.value = true
        execute {
            try {
                val started = System.nanoTime()
                val selectedKind = if (rawOnly) null else manualKind
                val reference = CaptureContext()
                val analysis = withContext(Dispatchers.Default) { interpreter.preview(text, reference, hints, previewKind(selectedKind)) }
                val event = analysis.records.singleOrNull()?.takeIf { it.baseKind == "event" }
                val applyLocal = private && selectedKind == null && !rawOnly && analysis.records.isNotEmpty()
                if (!rawOnly && (selectedKind == "task_deadline" && deadline == null || applyLocal && analysis.records.any { it.kindChoices.orEmpty().isNotEmpty() })) {
                    if (draft.value == text) { pendingSubmit.value = PendingSubmission(selectedKind); intentQuestion.value = true }
                    return@execute
                }
                if (!rawOnly && (selectedKind == "event" && event?.calendarEligible != true || selectedKind == null && analysis.doubts.isNotEmpty() && (applyLocal || analysis.records.any { it.baseKind == "event" }))) {
                    if (draft.value == text) {
                        pendingSubmit.value = PendingSubmission(if (event != null && selectedKind == null) "event" else selectedKind)
                        openClarification(analysis.doubts.firstOrNull() ?: CaptureDoubt(0, text.length, text, "missing_date_time"), analysis)
                    }
                    return@execute
                }
                val localRecords = if (applyLocal) captures.local(text, analysis, reference, codec.encode(hints)).records else {
                    captures.capture(text, private, manualKind = previewKind(selectedKind), plannedFor = if (selectedKind == "task") today.toString() else null, interpretationHintsJson = codec.encode(hints), eventStartAt = event?.startAt.takeIf { selectedKind == "event" }, rawOnly = rawOnly, dueDate = deadline.takeIf { selectedKind == "task_deadline" })
                    emptyList()
                }
                pendingSubmit.value = null
                if (draft.value == text) { setDraft(""); if (captureKind.value == originalKind) captureKind.value = null }
                selection.journalPage(0)
                val tasks = localRecords.filter { it.baseKind == "task" }
                if (tasks.isNotEmpty()) selection.filterTasks(if (tasks.size == 1) taskBucket(tasks.single(), today, reference.timezone) else "all")
                else if (selectedKind == "task_unscheduled") selection.filterTasks("someday")
                else if (selectedKind == "task") selection.filterTasks("today")
                else if (selectedKind == "task_deadline") { selection.filterTasks(if (LocalDate.parse(deadline) <= today) "today" else "upcoming"); selection.selectDate(LocalDate.parse(deadline)) }
                if (selectedKind == "idea" || localRecords.any { it.baseKind == "idea" }) { selection.searchIdeas("") }
                val savedStart = localRecords.firstOrNull { it.baseKind == "event" }?.startAt ?: event?.startAt.takeIf { selectedKind == "event" }
                if (savedStart != null) {
                    val local = Instant.parse(savedStart).atZone(reference.timezone)
                    selection.selectDate(local.toLocalDate())
                    feedback.show(if (localRecords.size > 1) "Saved ${localRecords.size} items on this device" else "Event saved for ${local.toLocalDate()} at ${local.toLocalTime()}")
                } else if (tasks.size == 1 || selectedKind in setOf("task", "task_unscheduled", "task_deadline")) feedback.show("Task saved · ${if (selection.state.value.taskFilter == "someday") "Someday (no date)" else selection.state.value.taskFilter.replaceFirstChar { it.uppercase() }}")
                else if (selectedKind == "idea" || localRecords.singleOrNull()?.baseKind == "idea") feedback.show("Idea saved · Ideas")
                else if (rawOnly) feedback.show("Original saved · Journal")
                else if (selectedKind == "information" || localRecords.singleOrNull()?.baseKind == "information") feedback.show("Information saved · Journal")
                else feedback.show(if (localRecords.isNotEmpty()) "Saved ${localRecords.size} items on this device" else "Saved on this device")
                runCatching { commands.captureDuration((System.nanoTime() - started) / 1_000_000); preferences.captureDraft(draft.value, codec.encode(captureHints.value)) }
            } finally { saving.value = false }
        }
    }
    fun focus() { focusCapture.value += 1 }
    fun dismissCloudExplanation() { cloudExplanation.value = false }
    fun setPrivacy(private: Boolean) = execute {
        if (!private && preferences.values.first()["cloudConsent"] != CapturePrivacy.CONSENT_VERSION) cloudExplanation.value = true
        else { preferences.capturePrivacy(private); privateCapture.value = private }
    }
    fun allowCloud() = execute { preferences.capturePrivacy(false, informedConsent = true); privateCapture.value = false; cloudExplanation.value = false }
}
