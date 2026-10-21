package app.planner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.planner.core.model.*
import app.planner.core.isoInstant
import app.planner.sync.SyncDependencies
import dagger.hilt.android.EntryPointAccessors
import java.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.*

/** Explicit QA-only dataset for runtime screenshots; never called by the production app. */
class PreviewFixtureTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun renderTodayFromPersistedFixtureData() {
        Assume.assumeTrue("Only run on a dedicated disposable emulator", InstrumentationRegistry.getArguments().getString("preview") == "true")
        val deps = EntryPointAccessors.fromApplication(ui.activity.applicationContext, SyncDependencies::class.java)
        val repo = deps.repository()
        val day = LocalDate.now(); val zone = ZoneId.systemDefault()
        runBlocking {
            repo.syncMutex.withLock {
                deps.preferences().set("serverUrl", ""); deps.engine().tokens.clear(); repo.clear()
                deps.preferences().set("privateCapture", "true"); deps.preferences().set("draft", "")
                suspend fun item(title: String, kind: String, planned: LocalDate? = null, hour: Int? = null, minute: Int = 0, pinned: Boolean = false, fields: String = "{}"): Record {
                    val note = repo.capture(title, true, manualKind = kind, plannedFor = planned?.toString())
                    val record = repo.dao.recordsForNote(note.id).single()
                    val updated = record.copy(startAt = hour?.let { isoInstant((planned ?: day).atTime(it, minute).atZone(zone).toInstant()) }, pinned = pinned, fieldsJson = fields)
                    repo.edit(updated)
                    return repo.dao.recordById(record.id)!!
                }
                item("Meeting", "event", hour = 13, fields = """{"bring":"Laptop, notebook"}""")
                item("Call doctor", "task", day, 15, 30)
                for (title in listOf("Finish draft", "Buy lamp", "Read an article", "Plan dinner")) item(title, "task", day, pinned = true)
                val idea = item("Volunteer program…", "idea", fields = """{"details":"Build a community tutoring program for local high school students."}""")
                repo.dao.slot(IdeaSlot(day.toString(), idea.id))
                item("Meeting", "event", day.plusDays(1), 9)
                item("Prepare questions", "task", day.plusDays(1)); item("Collect references", "task", day.plusDays(1))
                for (title in listOf("Clear desk", "Reply to email", "Book train")) repo.complete(item(title, "task", day))
            }
        }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Finish draft").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Buy lamp").assertExists()
        ui.onNodeWithTag("today-list").performScrollToNode(hasText("Done today: 3", substring = true))
        ui.onNode(hasText("Done today: 3", substring = true)).assertExists()
        ui.onNodeWithTag("today-list").performScrollToIndex(0)
    }
}
