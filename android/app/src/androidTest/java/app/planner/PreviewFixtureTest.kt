package app.planner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.planner.core.model.*
import app.planner.core.isoInstant
import app.planner.sync.SyncDependencies
import dagger.hilt.android.EntryPointAccessors
import java.time.*
import java.io.File
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import app.planner.features.overlay.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.*
import androidx.lifecycle.ViewModelProvider
import app.planner.features.PlannerViewModel

/** Explicit QA-only dataset for runtime screenshots; never called by the production app. */
class PreviewFixtureTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun renderTodayFromPersistedFixtureData() {
        Assume.assumeTrue("Only run on a dedicated disposable emulator", InstrumentationRegistry.getArguments().getString("preview") == "true")
        val deps = EntryPointAccessors.fromApplication(ui.activity.applicationContext, SyncDependencies::class.java)
        val repo = deps.repository()
        OverlayManager.stop(ui.activity)
        val empty = InstrumentationRegistry.getArguments().getString("empty") == "true"
        val day = LocalDate.now(); val zone = ZoneId.systemDefault()
        runBlocking {
            repo.syncMutex.withLock {
                deps.preferences().set("serverUrl", ""); deps.engine().tokens.clear(); repo.clear()
                deps.preferences().set("privateCapture", "true"); deps.preferences().set("draft", "")
                deps.preferences().set("overlay", "false"); deps.preferences().set("notification", "false")
                deps.preferences().set("cloudConsent", ""); deps.preferences().set("appearance", "light"); deps.preferences().set("weekStart", "sunday")
                if(empty) return@withLock
                suspend fun item(title: String, kind: String, planned: LocalDate? = null, hour: Int? = null, minute: Int = 0, pinned: Boolean = false, fields: String = "{}"): Record {
                    val note = repo.capture(title, true, manualKind = kind, plannedFor = planned?.toString())
                    val record = repo.dao.recordsForNote(note.id).single()
                    val updated = record.copy(startAt = hour?.let { isoInstant((planned ?: day).atTime(it, minute).atZone(zone).toInstant()) }, pinned = pinned, fieldsJson = fields)
                    repo.edit(updated)
                    return repo.dao.recordById(record.id)!!
                }
                item("Meeting", "event", hour = 13, fields = """{"bring":"Laptop, notebook"}""")
                item("Call doctor", "task", day, 15, 30)
                for (title in listOf("Finish draft", "Buy lamp", "Read an article", "Plan dinner")) item(title, "task", day, pinned = title in listOf("Finish draft", "Buy lamp"))
                val idea = item("Volunteer program…", "idea", fields = """{"details":"Build a community tutoring program for local high school students."}""")
                repo.dao.slot(IdeaSlot(day.toString(), idea.id))
                item("Meeting", "event", day.plusDays(1), 9)
                item("Prepare questions", "task", day.plusDays(1)); item("Collect references", "task", day.plusDays(1))
                item("Read 20 minutes", "task", fields = """{"domain":"Personal"}""")
                item("Start a weekend hike club", "task")
                val old = item("Plan a small reading corner", "task")
                repo.dao.record(old.copy(createdAt=isoInstant(day.minusDays(20).atStartOfDay(zone).toInstant()),updatedAt=isoInstant(day.minusDays(20).atStartOfDay(zone).toInstant())))
                item("Neighborhood seed exchange", "idea", fields = """{"details":"Share seeds and stories with people nearby."}""")
                for (title in listOf("Clear desk", "Reply to email", "Book train")) repo.complete(item(title, "task", day))
            }
        }
        if(empty) {
            ui.waitUntil(10_000) {ui.onAllNodesWithText("A place for every thought").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("privacy").assert(hasText("Private"))
            capture("welcome-private")
            return
        }
        ui.waitUntil(10_000) {ui.onAllNodesWithText("Finish draft").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("today-list").performScrollToNode(hasText("Buy lamp"))
        ui.onNodeWithText("Buy lamp").assertExists()
        ui.onNodeWithTag("today-list").performScrollToNode(hasText("Done today: 3", substring = true))
        ui.onNode(hasText("Done today: 3", substring = true)).assertExists()
        ui.onNodeWithTag("today-list").performScrollToIndex(0)
        if(InstrumentationRegistry.getArguments().getString("audit")=="true") {
            capture("today-top")
            ui.onNodeWithTag("today-list").performScrollToNode(hasText("Done today: 3",substring=true))
            capture("today-bottom")
            for((label,tag) in listOf("Calendar" to "calendar-list","Tasks" to "tasks-list","Ideas" to "ideas-list","Profile" to "profile-list")) {
                ui.onNodeWithContentDescription("Navigate $label").performClick()
                ui.onNodeWithTag(tag).performScrollToIndex(0)
                capture(label.lowercase())
            }
            for((label,tag) in listOf("Journal" to "journal-list", "Review" to "review-list")) {
                ui.onNodeWithTag("profile-list").performScrollToNode(hasText(label))
                ui.onNode(hasText(label) and hasClickAction()).performClick()
                ui.onNodeWithTag(tag).assertIsDisplayed()
                capture(label.lowercase())
                ui.onNodeWithContentDescription("Navigate Profile").performClick()
            }
            ui.onNodeWithTag("profile-list").performScrollToNode(hasText("Settings"))
            ui.onNode(hasText("Settings") and hasClickAction()).performClick()
            capture("settings")
            ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Data & export"))
            capture("settings-bottom")
            runBlocking { deps.preferences().set("appearance","dark") }
            ui.onNodeWithContentDescription("Navigate Today").performClick()
            ui.onNodeWithTag("today-list").performScrollToIndex(0)
            capture("today-dark")
            runBlocking { deps.preferences().set("appearance","light");deps.preferences().set("overlay","true") }
            ui.activity.runOnUiThread { OverlayManager.start(ui.activity) }
            val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.serviceInfo=automation.serviceInfo.apply {flags=flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
            automation.executeShellCommand("input keyevent 3").close()
            ui.waitUntil(10_000) {nativeNode("Quick capture")!=null}
            nativeNode("Quick capture")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ui.waitUntil(8_000) {nativeNode("Quick capture note")!=null}
            nativeNode("Quick capture note")!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"A thought from another app")})
            capture("quick-capture")
            nativeNode("Save quick capture")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ui.waitUntil(10_000) {runBlocking {repo.dao.notePage(50,0).any {it.originalContent=="A thought from another app" && it.source=="OVERLAY" && it.privacyMode=="LOCAL_ONLY"}}}
            // Validate the configured factory independently of Android's validated-network gate.
            val factoryProbe = androidx.work.OneTimeWorkRequestBuilder<app.planner.sync.SyncWorker>().build()
            val workManager = androidx.work.WorkManager.getInstance(ui.activity)
            workManager.enqueue(factoryProbe)
            ui.waitUntil(30_000) {
                workManager.getWorkInfoById(factoryProbe.id).get()?.state?.isFinished == true
            }
            org.junit.Assert.assertEquals(androidx.work.WorkInfo.State.SUCCEEDED, workManager.getWorkInfoById(factoryProbe.id).get()?.state)
            runBlocking {deps.preferences().set("overlay","false")}; OverlayManager.stop(ui.activity)
        }
    }

    private fun capture(name:String) {
        ui.waitForIdle()
        // Allow a real display frame after asynchronous Room/font/window updates.
        Thread.sleep(500)
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("No emulator screenshot")
        val file=File(ui.activity.getExternalFilesDir(null),"visual-audit/$name.png").apply {parentFile!!.mkdirs()}
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    private fun nativeNode(description:String):AccessibilityNodeInfo? {
        fun find(node:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
            if(node==null) return null
            if(node.contentDescription?.toString()==description) return node
            for(i in 0 until node.childCount) find(node.getChild(i))?.let {return it}
            return null
        }
        return InstrumentationRegistry.getInstrumentation().uiAutomation.windows.firstNotNullOfOrNull {find(it.root)}
    }
}
