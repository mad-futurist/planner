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
        ui.onNodeWithContentDescription("Open navigation").performClick()
        ui.onNode(hasText(label) and hasClickAction()).performClick()
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
            val expansion = SemanticsMatcher("Collapsed Anytime pool") { node -> node.config.contains(SemanticsProperties.Text) && node.config[SemanticsProperties.Text].any { it.text.matches(Regex("\\+\\d+ more")) } }
            ui.onNodeWithTag("today-list").performScrollToNode(expansion)
            ui.onNode(expansion).performClick()
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
        for ((route, title) in listOf("Journal" to "Journal", "Calendar" to "Calendar", "Ideas" to "Ideas", "Review" to "Review", "Settings" to "Settings", "Today" to "Today")) {
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
        }
    }
}
