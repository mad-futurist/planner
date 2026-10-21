package app.planner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import app.planner.core.database.*
import app.planner.core.model.*
import app.planner.domain.dayBounds
import app.planner.sync.SyncDependencies
import dagger.hilt.android.EntryPointAccessors
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class AppAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val dependencies get() = EntryPointAccessors.fromApplication(ui.activity.applicationContext, SyncDependencies::class.java)
    private val repository get() = dependencies.repository()
    private fun navigate(label: String) {
        val primary = label in setOf("Today","Calendar","Tasks","Ideas","Profile")
        if(android.os.Build.VERSION.SDK_INT>=30) ui.activityRule.scenario.onActivity {it.window.insetsController?.hide(android.view.WindowInsets.Type.ime())}
        ui.waitForIdle()
        ui.waitUntil(5_000) {ui.onAllNodesWithContentDescription("Navigate ${if(primary) label else "Profile"}").fetchSemanticsNodes().size==1}
        ui.onNodeWithContentDescription("Navigate ${if(primary) label else "Profile"}").performClick()
        val primaryTag="${if(primary) label.lowercase() else "profile"}-list"
        ui.waitUntil(5_000) {ui.onAllNodesWithTag(primaryTag).fetchSemanticsNodes().isNotEmpty()}
        if(!primary) {
            ui.onNodeWithTag("profile-list").performScrollToNode(hasText(label))
            ui.onNode(hasText(label) and hasClickAction()).performClick()
        }
        ui.waitForIdle()
    }

    @Test fun aCaptureAndActivityRecreationKeepOriginalInput() {
        val text = "Capture ${UUID.randomUUID()}"
        runBlocking { dependencies.preferences().values.first() }
        ui.waitForIdle()
        if (ui.onAllNodesWithText("Cloud AI").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithTag("privacy").performClick()
        ui.onNodeWithTag("capture").performTextClearance()
        ui.onNodeWithTag("capture").performTextInput(text)
        ui.onNodeWithTag("send").performClick()
        ui.waitUntil(10_000) { runBlocking { repository.dao.notePage(50, 0).any { it.originalContent == text && it.privacyMode == "LOCAL_ONLY" } } }
        navigate("Journal")
        ui.onNodeWithText(text).assertIsDisplayed()
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        navigate("Journal")
        ui.onNodeWithText(text).assertIsDisplayed()
    }

    @Test fun bTodayCompletionAndRestorationReactToRoom() {
        val title = "Focus ${UUID.randomUUID().toString().take(8)}"
        val date = LocalDate.now()
        val note = runBlocking { repository.capture(title, true, manualKind = "task", plannedFor = date.toString()) }
        val task = runBlocking { repository.dao.recordsForNote(note.id).single() }
        runBlocking { repository.edit(task.copy(pinned = true)) }
        navigate("Today")
        val initialBounds = dayBounds(date, ZoneId.systemDefault())
        val focusCount = runBlocking { repository.dao.day(initialBounds.day, initialBounds.start, initialBounds.end).first().count { it.baseKind == "task" && !app.planner.domain.isScheduledOnDay(it, date, ZoneId.systemDefault()) } }
        if (focusCount > 2) {
            ui.onNodeWithTag("today-list").performScrollToNode(hasTestTag("anytime-more"))
            ui.onNodeWithTag("anytime-more").performClick()
        }
        ui.onNodeWithTag("today-list").performScrollToNode(hasContentDescription("Complete $title"))
        ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription("Complete $title").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("Complete $title").performClick()
        ui.waitUntil(10_000) { runBlocking { repository.dao.recordById(task.id)?.state == "completed" } }
        // Exercise the row after transient completion feedback stops covering its hit target.
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Marked done").fetchSemanticsNodes().isEmpty() }
        val bounds = dayBounds(date, ZoneId.systemDefault())
        val count = runBlocking { repository.dao.completed(bounds.start, bounds.end).first().size }
        ui.onNodeWithTag("today-list").performScrollToNode(hasText("Done today: $count", substring = true))
        ui.onNode(hasText("Done today: $count", substring = true) and hasClickAction()).performClick()
        ui.onNodeWithTag("today-list").performScrollToNode(hasContentDescription("Restore $title"))
        ui.onNodeWithContentDescription("Restore $title").performClick()
        ui.waitUntil(10_000) { runBlocking { repository.dao.recordById(task.id)?.state == "pending" } }
    }

    @Test fun cAllPrimaryRoutesAreReachable() {
        for ((route, title) in listOf("Journal" to "Journal", "Calendar" to "Calendar", "Tasks" to "Tasks", "Ideas" to "Ideas", "Profile" to "Profile", "Review" to "Review", "Settings" to "Settings", "Today" to "Today")) {
            navigate(route)
            ui.onNode(hasText(title) and !hasClickAction()).assertExists()
            ui.onNodeWithTag("capture").assertExists()
        }
    }

    @Test fun dManualInterpretationSurvivesRecreation() {
        val note = runBlocking { repository.capture("Interpret ${UUID.randomUUID().toString().take(8)}", true) }
        runBlocking { repository.manualInterpretation(note, "idea", "An editable idea", null) }
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        val saved = runBlocking { repository.dao.recordsForNote(note.id).single() }
        assertEquals("idea", saved.baseKind)
        assertTrue(saved.isUserConfirmed)
        assertEquals(note.content, runBlocking { repository.dao.noteById(note.id)?.originalContent })
    }

    @Test fun eRealBackendSyncWorksWithoutProviderConfiguration() {
        val arguments = InstrumentationRegistry.getArguments()
        val invite = arguments.getString("invite")
        Assume.assumeTrue("Run with a one-time local backend invitation", !invite.isNullOrBlank())
        val engine = dependencies.engine()
        val note = runBlocking { repository.capture("Backend compatibility ${UUID.randomUUID().toString().take(8)}", false) }
        runBlocking { engine.connect(arguments.getString("serverUrl") ?: "http://10.0.2.2:8000", invite!!) }
        var status = "pending"
        runBlocking {
            repeat(15) { engine.run(); status = repository.dao.noteById(note.id)!!.processingStatus; if (status != "pending") return@runBlocking; delay(1000) }
        }
        assertEquals("unconfigured", status)
        assertTrue(runBlocking { repository.dao.recordsForNote(note.id).isEmpty() })
        runBlocking { repository.manualInterpretation(repository.dao.noteById(note.id)!!, "task", "Manual correction over real sync", LocalDate.now().toString()); engine.run() }
        val record = runBlocking { repository.dao.recordsForNote(note.id).single() }
        runBlocking { repository.complete(record); engine.run() }
        assertEquals("completed", runBlocking { repository.dao.recordById(record.id)?.state })
        assertNull(runBlocking { repository.dao.pendingOperation(record.id) })
        runBlocking { repository.complete(repository.dao.recordById(record.id)!!, true); engine.run() }
        assertEquals("pending", runBlocking { repository.dao.recordById(record.id)?.state })
        // A rejected payload must be recoverable after the user corrects the saved record.
        runBlocking {
            val current = repository.dao.recordById(record.id)!!
            val oversizedFields = com.google.gson.JsonObject().apply { repeat(33) { addProperty("field$it", "fixture") } }
            repository.edit(current.copy(fieldsJson = oversizedFields.toString()))
            engine.run()
            val rejected = repository.dao.operationsForEntity(record.id).first { it.status == "failed" }
            repository.edit(repository.dao.recordById(record.id)!!.copy(fieldsJson = "{}", title = "Corrected after rejection"))
            engine.keepLocal(rejected)
            engine.run()
            assertNull(repository.dao.pendingOperation(record.id))
            assertEquals("Corrected after rejection", repository.dao.recordById(record.id)?.title)
            val ideaNote = repository.capture("Cloud provenance fixture", false, manualKind = "idea")
            engine.run()
            val idea = repository.dao.recordsForNote(ideaNote.id).single()
            val converted = repository.linkedTask(idea, private = false)
            engine.run()
            assertEquals("idea", repository.dao.recordById(idea.id)?.baseKind)
            assertEquals(idea.id, com.google.gson.JsonParser.parseString(repository.dao.recordById(converted.id)!!.fieldsJson).asJsonObject.get("developedFrom").asString)
            assertNull(repository.dao.pendingOperation(converted.sourceNoteId))
        }
    }
    @Test fun fCloudCaptureRequiresConsentAndCancellationKeepsNotesPrivate() {
        val prefs=dependencies.preferences()
        runBlocking {prefs.set("cloudConsent","");prefs.capturePrivacy(true)}
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithTag("privacy").performClick()
        ui.onNodeWithText("Keep private").performClick()
        val text="Private consent check ${UUID.randomUUID()}"
        ui.onNodeWithTag("capture").performTextClearance();ui.onNodeWithTag("capture").performTextInput(text)
        ui.onNodeWithTag("send").performClick()
        ui.waitUntil(10_000) {runBlocking {repository.dao.notePage(50,0).any {it.originalContent==text && it.privacyMode=="LOCAL_ONLY"}}}
        val note=runBlocking {repository.dao.notePage(50,0).first {it.originalContent==text}}
        assertEquals(0,runBlocking {repository.dao.pendingForNote(note.id)})
        ui.onNodeWithTag("privacy").performClick()
        ui.onNodeWithTag("allow-cloud").performClick()
        ui.waitUntil(5_000) {runBlocking {prefs.values.first()["cloudConsent"]==app.planner.core.CapturePrivacy.CONSENT_VERSION}}
        val cloud="Consented capture ${UUID.randomUUID()}"
        ui.onNodeWithTag("capture").performTextInput(cloud);ui.onNodeWithTag("send").performClick()
        ui.waitUntil(10_000) {runBlocking {repository.dao.notePage(50,0).any {it.originalContent==cloud && it.privacyMode=="CLOUD_ALLOWED"}}}
        runBlocking {prefs.capturePrivacy(true)}
    }
    @Test fun gUndatedTaskCanBeFoundCompletedUndoneAndRestoredFromArchive() {
        val title="Undated ${UUID.randomUUID().toString().take(8)}"
        val note=runBlocking {repository.capture(title,true,manualKind="task")}
        val task=runBlocking {repository.dao.recordsForNote(note.id).single()}
        navigate("Tasks");ui.onNodeWithTag("filter-someday").performClick()
        ui.onNodeWithTag("tasks-list").performScrollToNode(hasText(title))
        ui.onNodeWithContentDescription("Complete $title").performClick()
        ui.waitUntil(10_000) {runBlocking {repository.dao.recordById(task.id)?.state=="completed"}}
        ui.onNodeWithText("Undo").performClick()
        ui.waitUntil(10_000) {runBlocking {repository.dao.recordById(task.id)?.state=="pending"}}
        runBlocking {repository.edit(repository.dao.recordById(task.id)!!.copy(state="archived"))}
        ui.onNodeWithTag("tasks-list").performScrollToIndex(0)
        ui.onNodeWithTag("task-views").performClick();ui.onNodeWithText("Archived").performClick()
        ui.onNodeWithTag("tasks-list").performScrollToNode(hasText(title))
        ui.onNodeWithContentDescription("Details $title").performClick()
        ui.onNodeWithText("Restore item").performScrollTo().performClick()
        ui.waitUntil(10_000) {runBlocking {repository.dao.recordById(task.id)?.state=="pending"}}
        ui.activityRule.scenario.recreate();navigate("Tasks")
        ui.onNodeWithTag("filter-someday").performClick()
        ui.onNodeWithTag("tasks-list").performScrollToNode(hasText(title))
        ui.onNodeWithText(title).assertIsDisplayed()
    }

    @Test fun hGroupedSettingsApplyWeekPreferenceAndClearCancellationPreservesData() {
        val before=runBlocking {repository.dao.noteCount()}
        navigate("Settings")
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Calendar behavior"))
        ui.onNodeWithText("Calendar behavior").performClick()
        ui.onNodeWithText("Monday").performScrollTo().performClick()
        ui.waitUntil(5_000) {runBlocking {dependencies.preferences().values.first()["weekStart"]=="monday"}}
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Data & export"))
        ui.onNodeWithText("Data & export").performClick()
        ui.onNodeWithText("Clear local data").performScrollTo().performClick()
        ui.onNodeWithText("Clear this device?").assertExists()
        ui.onNodeWithText("Cancel").performClick()
        assertEquals(before,runBlocking {repository.dao.noteCount()})
        runBlocking {dependencies.preferences().set("weekStart","sunday")}
    }

    @Test fun iReviewCreatesASmallerLinkedActionWithoutRemovingTheOriginal() {
        val title="Revisit ${UUID.randomUUID().toString().take(8)}"
        val day=LocalDate.now()
        val task=runBlocking {
            val note=repository.capture(title,true,manualKind="task")
            val task=repository.dao.recordsForNote(note.id).single()
            repository.dao.record(task.copy(createdAt="2026-01-01T00:00:00.000Z",updatedAt="2026-01-01T00:00:00.000Z"))
            repository.dao.meta(Meta("task-review:$day","[\"${task.id}\"]"))
            task
        }
        navigate("Review")
        ui.waitUntil(8_000) {ui.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("review-list").performScrollToNode(hasText(title))
        ui.onNodeWithText("Smaller action").performClick()
        val action="One small action ${UUID.randomUUID().toString().take(8)}"
        ui.onNode(hasSetTextAction() and hasText("Next action")).performTextInput(action)
        ui.onNodeWithText("Create action").performClick()
        ui.waitUntil(10_000) {runBlocking {repository.dao.notePage(50,0).any {it.originalContent==action}}}
        val linked=runBlocking {val note=repository.dao.notePage(50,0).first {it.originalContent==action};repository.dao.recordsForNote(note.id).single()}
        assertEquals(task.id,com.google.gson.JsonParser.parseString(linked.fieldsJson).asJsonObject.get("splitFrom").asString)
        assertEquals(day.toString(),linked.plannedFor)
        assertEquals("pending",runBlocking {repository.dao.recordById(task.id)?.state})
    }
    @Test fun jConvertedIdeaRemainsSearchableAndItsSourceLinkIsEditable() {
        val title="Possibility ${UUID.randomUUID().toString().take(8)}"
        val idea=runBlocking {val note=repository.capture(title,true,manualKind="idea");repository.dao.recordsForNote(note.id).single()}
        navigate("Ideas")
        ui.onNodeWithTag("idea-search").performTextInput(title)
        ui.waitUntil(8_000) {ui.onAllNodesWithTag("convert-${idea.id}").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("ideas-list").performScrollToNode(hasTestTag("convert-${idea.id}"))
        ui.onNodeWithTag("convert-${idea.id}").performClick()
        val bounds=dayBounds(LocalDate.now(),ZoneId.systemDefault())
        ui.waitUntil(8_000) {runBlocking {repository.dao.tasks("someday",bounds.day,bounds.end).first().any {it.title==title}}}
        val task=runBlocking {repository.dao.tasks("someday",bounds.day,bounds.end).first().first {it.title==title}}
        assertEquals("idea",runBlocking {repository.dao.recordById(idea.id)?.baseKind})
        navigate("Tasks");ui.onNodeWithTag("filter-someday").performClick()
        ui.onNodeWithTag("tasks-list").performScrollToNode(hasText(title))
        ui.onNodeWithContentDescription("Details $title").performClick()
        ui.onNodeWithText("From idea: $title").performScrollTo().performClick()
        ui.waitUntil(8_000) {ui.onAllNodesWithText("Kind: Idea").fetchSemanticsNodes().isNotEmpty()}
        ui.onNode(hasSetTextAction() and hasText(title)).performTextClearance()
        ui.onNode(hasSetTextAction() and hasText("Title")).performTextInput("$title edited")
        ui.onNodeWithText("Save").performClick()
        ui.waitUntil(8_000) {runBlocking {repository.dao.recordById(idea.id)?.title=="$title edited"}}
        assertEquals(idea.id,com.google.gson.JsonParser.parseString(task.fieldsJson).asJsonObject.get("developedFrom").asString)
        navigate("Ideas")
        ui.waitUntil(8_000) {ui.onAllNodesWithText("$title edited").fetchSemanticsNodes().isNotEmpty()}
    }

}
