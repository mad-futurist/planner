package app.planner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import app.planner.sync.SyncDependencies
import dagger.hilt.android.EntryPointAccessors
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

/** Run on the dedicated disposable Planner_Clarification_QA AVD. */
class CaptureClarificationAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val dependencies get() = EntryPointAccessors.fromApplication(ui.activity.applicationContext, SyncDependencies::class.java)
    private val repo get() = dependencies.repository()
    @Before fun privateDraft() {
        runBlocking { dependencies.preferences().capturePrivacy(true) }
        ui.onNodeWithTag("capture").performTextClearance()
    }
    private fun doubt() {
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("capture-doubt-0").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("capture-doubt-0").performClick()
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        // Semantics can lead the emulator's displayed buffer. Await an actual rendered frame.
        val committed = java.util.concurrent.CountDownLatch(1)
        ui.activityRule.scenario.onActivity { activity ->
            val view = activity.window.decorView
            val presented = Runnable { view.postOnAnimation { view.postOnAnimation { committed.countDown() } } }
            if (android.os.Build.VERSION.SDK_INT >= 29 && view.isHardwareAccelerated) {
                view.viewTreeObserver.registerFrameCommitCallback(presented)
                view.invalidate()
            } else presented.run()
        }
        assertTrue("Rendered frame did not commit", committed.await(10, java.util.concurrent.TimeUnit.SECONDS))
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        java.io.File(ui.activity.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun aAmbiguousNumberRequiresExplicitChoiceAndCreatesExactCalendarEvent() {
        val text = "dentist 16 audit${UUID.randomUUID().toString().replace("-", "")}"
        ui.onNodeWithTag("capture").performTextInput(text)
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("capture-doubt-0").fetchSemanticsNodes().isNotEmpty() }
        screenshot("capture-preview")
        ui.onNodeWithTag("send").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("clarify-today-hour").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { repo.dao.notePage(100, 0).none { it.content == text } })
        screenshot("capture-clarification")
        ui.onNodeWithTag("clarify-today-hour").performClick()
        ui.onNodeWithTag("apply-clarification").performClick()
        ui.waitUntil(10_000) { runBlocking { repo.dao.notePage(100, 0).any { it.content == text } } }
        runBlocking {
            val note = repo.dao.notePage(100, 0).first { it.content == text }
            val record = repo.dao.recordsForNote(note.id).single()
            assertEquals(text, note.originalContent)
            assertEquals("LOCAL_ONLY", note.privacyMode)
            assertEquals("event", record.baseKind)
            assertEquals(LocalDate.now().atTime(16, 0).atZone(ZoneId.systemDefault()).toInstant(), Instant.parse(record.startAt))
            assertTrue(note.interpretationHintsJson.contains("16:00"))
            assertEquals(0, repo.dao.pendingForNote(note.id))
        }
    }
    @Test fun bDateOnlyChoiceDoesNotInventTimeAndRawNoteCanBeSavedInstead() {
        val text = "dentiste 16 audit${UUID.randomUUID().toString().replace("-", "")}"
        ui.onNodeWithTag("capture").performTextInput(text)
        doubt()
        ui.onNodeWithText("Day 16 of a month…").performClick()
        ui.waitForIdle()
        ui.activityRule.scenario.onActivity {
            val button = android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { root -> root.findViewById<android.widget.Button>(android.R.id.button1) }
            assertNotNull(button)
            button!!.performClick()
        }
        ui.onNodeWithTag("apply-clarification").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("capture-doubt-0").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("create-preview-event").assertDoesNotExist()
        doubt()
        ui.onNodeWithTag("save-note-without-event").performClick()
        ui.waitUntil(10_000) { runBlocking { repo.dao.notePage(100, 0).any { it.content == text } } }
        runBlocking {
            val note = repo.dao.notePage(100, 0).first { it.content == text }
            assertEquals(text, note.originalContent)
            assertTrue(repo.dao.recordsForNote(note.id).isEmpty())
            assertTrue(note.manual)
            assertTrue(note.interpretationHintsJson.contains("date"))
        }
    }
    @Test fun cEditingInvalidatesClarificationAndDoesNotTransmitPrivateDraft() {
        ui.onNodeWithTag("capture").performTextInput("стоматолог 16")
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("capture-doubt-0").fetchSemanticsNodes().isNotEmpty() }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        ui.onNodeWithTag("capture").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val number = layouts.single().getBoundingBox("стоматолог ".length)
        ui.onNodeWithTag("capture").performTouchInput { click(number.center) }
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("clarify-today-hour").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("clarify-today-hour").performClick()
        ui.onNodeWithTag("apply-clarification").performClick()
        ui.onNodeWithTag("capture").performTextReplacement("стоматолог 17")
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("capture-doubt-0").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("create-preview-event").assertDoesNotExist()
        ui.onNodeWithTag("preview-ai").assertDoesNotExist()
    }
    @Test fun dDateChoiceResumesSubmittedDentistAt16AndAppearsOnChosenCalendarDay() {
        val text = "dentist at 16"
        val before = runBlocking { repo.dao.notePage(100, 0).map { it.id }.toSet() }
        val target = LocalDate.now().plusDays(3)
        ui.onNodeWithTag("capture").performTextInput(text)
        ui.onNodeWithTag("send").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Date: Not set").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Time: 16:00").assertExists()
        ui.onNodeWithText("Date: Not set").performClick()
        ui.waitForIdle()
        ui.activityRule.scenario.onActivity {
            fun picker(view: android.view.View): android.widget.DatePicker? {
                if (view is android.widget.DatePicker) return view
                if (view is android.view.ViewGroup) for (index in 0 until view.childCount) picker(view.getChildAt(index))?.let { return it }
                return null
            }
            val roots = android.view.inspector.WindowInspector.getGlobalWindowViews()
            val date = roots.firstNotNullOfOrNull(::picker)
            assertNotNull(date)
            date!!.updateDate(target.year, target.monthValue-1, target.dayOfMonth)
            roots.firstNotNullOf { root -> root.findViewById<android.widget.Button>(android.R.id.button1) }.performClick()
        }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Save event").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("apply-clarification").performClick()
        ui.waitUntil(10_000) { runBlocking { repo.dao.notePage(100, 0).any { it.content == text && it.id !in before } } }
        runBlocking {
            val note = repo.dao.notePage(100, 0).first { it.content == text && it.id !in before }
            val record = repo.dao.recordsForNote(note.id).single()
            assertEquals(text, note.originalContent)
            assertEquals("event", record.baseKind)
            assertEquals(target.atTime(16, 0).atZone(ZoneId.systemDefault()).toInstant(), Instant.parse(record.startAt))
            val bounds = app.planner.domain.dayBounds(target, ZoneId.systemDefault())
            assertTrue(repo.dao.day(bounds.day, bounds.start, bounds.end).first().any { it.id == record.id })
        }
        val vm = androidx.lifecycle.ViewModelProvider(ui.activity)[app.planner.features.PlannerViewModel::class.java]
        assertEquals(target, vm.uiState.value.selection.selectedDate)
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("apply-clarification").fetchSemanticsNodes().isEmpty() }
        ui.activityRule.scenario.onActivity { it.window.insetsController?.hide(android.view.WindowInsets.Type.ime()) }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Calendar").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Calendar").performClick()
        val calendarTitle = hasText(text) and hasAnyAncestor(hasTestTag("calendar-list"))
        ui.waitUntil(10_000) { ui.onAllNodes(calendarTitle).fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodes(calendarTitle)[0].assertIsDisplayed()
        ui.waitUntil(15_000) { ui.activity.hasWindowFocus() }
        screenshot("dentist-at-16-saved-calendar")
    }
    private fun hideKeyboard() {
        ui.activityRule.scenario.onActivity { it.window.insetsController?.hide(android.view.WindowInsets.Type.ime()) }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Calendar").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun savedNote(text: String, before: Set<String>): app.planner.core.model.RawNote {
        ui.waitUntil(10_000) { runBlocking { repo.dao.notePage(100, 0).any { it.content == text && it.id !in before } } }
        return runBlocking { repo.dao.notePage(100, 0).first { it.content == text && it.id !in before } }
    }
    private fun visibleSavedCard(note: app.planner.core.model.RawNote, list: String) {
        val record = runBlocking { repo.dao.recordsForNote(note.id).single() }
        val tag = when (record.baseKind) { "task" -> "task-${record.id}"; "idea" -> "idea-${record.id}"; else -> "schedule-${record.id}" }
        ui.waitUntil(15_000) {
            runCatching {
                ui.onNodeWithTag(list).performScrollToNode(hasTestTag(tag))
                ui.onNodeWithTag(tag).assertIsDisplayed()
            }.isSuccess
        }
        ui.onNodeWithTag(tag).assertIsDisplayed()
    }
    private fun chooseNativeDate(target: LocalDate) {
        ui.waitForIdle()
        ui.activityRule.scenario.onActivity {
            fun find(view: android.view.View): android.widget.DatePicker? {
                if (view is android.widget.DatePicker) return view
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                return null
            }
            val roots = android.view.inspector.WindowInspector.getGlobalWindowViews()
            val picker = roots.firstNotNullOf(::find)
            picker.updateDate(target.year, target.monthValue - 1, target.dayOfMonth)
            roots.firstNotNullOf { it.findViewById<android.widget.Button>(android.R.id.button1) }.performClick()
        }
    }
    @Test fun iMaybeFindCourseRequiresChoiceThenAppearsInSomeday() {
        val text = "maybe find ai course"
        val before = runBlocking { repo.dao.notePage(100, 0).map { it.id }.toSet() }
        ui.onNodeWithTag("capture").performTextInput(text)
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("intent-someday").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("intent-deadline").assertIsDisplayed()
        ui.onNodeWithTag("intent-someday").assertIsDisplayed()
        ui.onNodeWithTag("intent-idea").assertIsDisplayed()
        ui.onNodeWithText("Local interpretation preview · no AI request").assertDoesNotExist()
        ui.onNodeWithText("Choose where this belongs").assertDoesNotExist()
        ui.onNodeWithTag("change-preview-kind").assertDoesNotExist()
        ui.onNodeWithTag("preview-ai").assertDoesNotExist()
        ui.waitUntil(10_000) { androidx.lifecycle.ViewModelProvider(ui.activity)[app.planner.features.PlannerViewModel::class.java].uiState.value.message == null }
        screenshot("compact-capture-actions")
        ui.onNodeWithTag("send").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("submit-intent-someday").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { repo.dao.notePage(100, 0).none { it.content == text && it.id !in before } })
        screenshot("maybe-course-choice")
        ui.onNodeWithTag("submit-intent-someday").performClick()
        val note = savedNote(text, before)
        runBlocking { val record = repo.dao.recordsForNote(note.id).single(); assertEquals("task", record.baseKind); assertNull(record.plannedFor); assertNull(record.dueDate); assertNull(record.startAt); assertEquals(text, note.originalContent); assertEquals(0, repo.dao.pendingForNote(note.id)) }
        hideKeyboard(); ui.onNodeWithText("Tasks").performClick()
        visibleSavedCard(note, "tasks-list")
        ui.waitUntil(15_000) { ui.activity.hasWindowFocus() }; screenshot("maybe-course-someday")
    }
    @Test fun jMaybeCourseCanBecomeAnIdeaBeforeSend() {
        val text = "maybe find ai course"
        val before = runBlocking { repo.dao.notePage(100, 0).map { it.id }.toSet() }
        ui.onNodeWithTag("capture").performTextInput(text)
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("intent-idea").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("intent-idea").performClick()
        assertTrue(runBlocking { repo.dao.notePage(100, 0).none { it.content == text && it.id !in before } })
        ui.onNodeWithTag("send").performClick()
        val note = savedNote(text, before)
        runBlocking { assertEquals("idea", repo.dao.recordsForNote(note.id).single().baseKind); assertEquals(text, note.originalContent) }
        hideKeyboard(); ui.onNodeWithText("Ideas").performClick()
        visibleSavedCard(note, "ideas-list")
    }
    @Test fun kMaybeCourseCanBecomeATaskWithChosenDeadline() {
        val text = "maybe find ai course"
        val target = LocalDate.now().plusDays(5)
        val before = runBlocking { repo.dao.notePage(100, 0).map { it.id }.toSet() }
        ui.onNodeWithTag("capture").performTextInput(text); ui.onNodeWithTag("send").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("submit-intent-deadline").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("submit-intent-deadline").performClick(); chooseNativeDate(target)
        val note = savedNote(text, before)
        runBlocking { val record = repo.dao.recordsForNote(note.id).single(); assertEquals("task", record.baseKind); assertEquals(target.toString(), record.dueDate); assertNull(record.plannedFor); assertNull(record.startAt); assertEquals(text, note.originalContent) }
        hideKeyboard(); ui.onNodeWithText("Tasks").performClick()
        visibleSavedCard(note, "tasks-list")
        ui.waitUntil(15_000) { ui.activity.hasWindowFocus() }; screenshot("maybe-course-deadline")
    }
    @Test fun lKnownDentistClockOffersTodayWithoutSomedayChoice() {
        val text = "dentist at 16"
        val before = runBlocking { repo.dao.notePage(100, 0).map { it.id }.toSet() }
        ui.onNodeWithTag("capture").performTextInput(text)
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("event-today").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("intent-someday").assertDoesNotExist()
        ui.onNodeWithTag("event-today").performClick()
        assertTrue(runBlocking { repo.dao.notePage(100, 0).none { it.content == text && it.id !in before } })
        ui.onNodeWithTag("send").performClick()
        val note = savedNote(text, before)
        runBlocking { val record = repo.dao.recordsForNote(note.id).single(); assertEquals("event", record.baseKind); assertEquals(LocalDate.now().atTime(16, 0).atZone(ZoneId.systemDefault()).toInstant(), Instant.parse(record.startAt)) }
    }
    @Test fun mUnknownInformationTypeCanBeChangedInPreview() {
        val text = "course reference notes"
        val before = runBlocking { repo.dao.notePage(100, 0).map { it.id }.toSet() }
        ui.onNodeWithTag("capture").performTextInput(text)
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("change-preview-kind").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("change-preview-kind").performClick(); ui.onNodeWithText("Idea").performClick()
        ui.onNodeWithTag("send").performClick()
        val note = savedNote(text, before)
        runBlocking { assertEquals("idea", repo.dao.recordsForNote(note.id).single().baseKind); assertEquals(text, note.originalContent) }
    }
    @Test fun eDefaultPrivateTaskIsCreatedAndVisibleInTasksWithoutChoosingKind() {
        val text = "buy milk"
        val before = runBlocking { repo.dao.notePage(100, 0).map { it.id }.toSet() }
        ui.onNodeWithTag("capture").performTextInput(text)
        ui.onNodeWithTag("send").performClick()
        val note = savedNote(text, before)
        runBlocking {
            val record = repo.dao.recordsForNote(note.id).single()
            assertEquals("task", record.baseKind)
            assertNull(record.plannedFor)
            assertEquals(0, repo.dao.pendingForNote(note.id))
            assertEquals("local-rules-v1", repo.dao.latestHistory(note.id)!!.modelVersion)
        }
        hideKeyboard()
        ui.onNodeWithText("Tasks").performClick()
        val taskTitle = hasText(text) and hasAnyAncestor(hasTestTag("tasks-list"))
        ui.waitUntil(10_000) { ui.onAllNodes(taskTitle).fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodes(taskTitle)[0].assertIsDisplayed()
        val vm = androidx.lifecycle.ViewModelProvider(ui.activity)[app.planner.features.PlannerViewModel::class.java]
        assertEquals("someday", vm.uiState.value.selection.taskFilter)
        ui.waitUntil(15_000) { ui.activity.hasWindowFocus() }
        screenshot("private-default-task-in-tasks")
    }
    @Test fun fDefaultPrivateExactEventIsCreatedWithoutAnotherSaveButton() {
        val target = LocalDate.now().plusDays(4)
        val text = "dentist $target at 16"
        val before = runBlocking { repo.dao.notePage(100, 0).map { it.id }.toSet() }
        ui.onNodeWithTag("capture").performTextInput(text)
        ui.onNodeWithTag("send").performClick()
        val note = savedNote(text, before)
        runBlocking {
            val record = repo.dao.recordsForNote(note.id).single()
            assertEquals(text, note.originalContent)
            assertEquals("event", record.baseKind)
            assertEquals(target.atTime(16, 0).atZone(ZoneId.systemDefault()).toInstant(), Instant.parse(record.startAt))
            assertEquals(0, repo.dao.pendingForNote(note.id))
        }
        ui.onNodeWithTag("apply-clarification").assertDoesNotExist()
        hideKeyboard()
        ui.onNodeWithText("Calendar").performClick()
        val calendarTitle = hasText(text) and hasAnyAncestor(hasTestTag("calendar-list"))
        ui.waitUntil(10_000) { ui.onAllNodes(calendarTitle).fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodes(calendarTitle)[0].assertIsDisplayed()
        ui.waitUntil(15_000) { ui.activity.hasWindowFocus() }
        screenshot("private-default-event-in-calendar")
    }
    @Test fun gExplicitOriginalOnlyDoesNotCreateAnEventOrAskForItsDate() {
        val text = "dentist at 16"
        val before = runBlocking { repo.dao.notePage(100, 0).map { it.id }.toSet() }
        ui.onNodeWithContentDescription("Choose capture type").performClick()
        ui.onNodeWithText("Original note only").performClick()
        ui.onNodeWithTag("capture").performTextInput(text)
        ui.onNodeWithTag("send").performClick()
        val note = savedNote(text, before)
        runBlocking { assertTrue(repo.dao.recordsForNote(note.id).isEmpty()); assertTrue(note.manual) }
        ui.onNodeWithTag("apply-clarification").assertDoesNotExist()
    }
    @Test fun hMixedPrivateCaptureResumesAfterClarificationAndCreatesBothEntities() {
        val text = "dentist 16; buy milk"
        val before = runBlocking { repo.dao.notePage(100, 0).map { it.id }.toSet() }
        ui.onNodeWithTag("capture").performTextInput(text)
        ui.onNodeWithTag("send").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("clarify-today-hour").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("clarify-today-hour").performClick()
        ui.onNodeWithTag("apply-clarification").performClick()
        val note = savedNote(text, before)
        runBlocking {
            val records = repo.dao.recordsForNote(note.id)
            assertEquals(setOf("event", "task"), records.map { it.baseKind }.toSet())
            assertEquals(2, records.size)
            assertEquals(text, note.originalContent)
            assertEquals(0, repo.dao.pendingForNote(note.id))
        }
    }
}
