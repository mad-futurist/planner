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
import app.planner.R
import app.planner.features.PlannerViewModel
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Explicit QA-only dataset for runtime screenshots; never called by the production app. */
class PreviewFixtureTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun renderTodayFromPersistedFixtureData() {
        Assume.assumeTrue("Only run on a dedicated disposable emulator", InstrumentationRegistry.getArguments().getString("preview") == "true")
        val deps = EntryPointAccessors.fromApplication(ui.activity.applicationContext, SyncDependencies::class.java)
        val repo = deps.repository()
        val model = ui.runOnIdle { ViewModelProvider(ui.activity)[PlannerViewModel::class.java] }
        OverlayManager.stop(ui.activity)
        val arguments = InstrumentationRegistry.getArguments()
        val empty = arguments.getString("empty") == "true"
        val warmToday = arguments.getString("warmToday") == "true"
        val visualV2 = arguments.getString("visualV2") == "true"
        val locale = ui.activity.resources.configuration.locales[0]
        val russian = locale.language == "ru"
        val taskTitle = if (russian) "Подготовить презентацию" else "Prepare the presentation"
        val meetingTitle = if (russian) "Встреча с командой" else "Meet with the team"
        val coffeeTitle = if (russian) "Купить кофе" else "Buy coffee"
        val day = LocalDate.now(); val zone = ZoneId.systemDefault()
        runBlocking {
            repo.syncMutex.withLock {
                deps.preferences().set("serverUrl", ""); deps.engine().tokens.clear(); repo.clear()
                deps.preferences().set("privateCapture", "true"); deps.preferences().set("draft", "")
                deps.preferences().set("overlay", "false"); deps.preferences().set("notification", "false")
                deps.preferences().set("cloudConsent", ""); deps.preferences().set("appearance", "light"); deps.preferences().set("weekStart", "sunday")
                if(empty) return@withLock
                suspend fun item(title: String, kind: String, planned: LocalDate? = null, hour: Int? = null, minute: Int = 0, pinned: Boolean = false, fields: String = "{}", due: LocalDate? = null, duration: Long? = null): Record {
                    val note = repo.capture(title, true, manualKind = kind, plannedFor = planned?.toString())
                    val record = repo.dao.recordsForNote(note.id).single()
                    val start = hour?.let { (planned ?: day).atTime(it, minute).atZone(zone).toInstant() }
                    val updated = record.copy(startAt = start?.let(::isoInstant),
                        endAt = duration?.let { start?.plusSeconds(it * 60)?.let(::isoInstant) },
                        pinned = pinned, fieldsJson = fields, dueDate = due?.toString())
                    repo.edit(updated)
                    return repo.dao.recordById(record.id)!!
                }
                if (warmToday || visualV2) {
                    item(taskTitle, "task", day, fields = """{"url":"https://example.org/slides"}""", due = day.plusDays(2))
                    item(meetingTitle, "event", hour = 16, duration = 60)
                    repo.complete(item(coffeeTitle, "task", day))
                    if (visualV2) {
                        item(if (russian) "Проект для агентов" else "Agent project", "idea", fields = """{"domain":"Product","details":"DSL and planning notes"}""")
                        item(if (russian) "Идея для поездки" else "Travel idea", "idea", fields = """{"domain":"Travel"}""")
                        item(if (russian) "Стоматолог" else "Dentist", "event", day.minusDays(2), 10, duration = 45)
                        item(if (russian) "Тренировка" else "Training", "event", day.minusDays(1), 9, duration = 90)
                        item(if (russian) "Купить подарок" else "Buy a gift", "task", day.plusDays(4))
                        item(if (russian) "Разобрать входящие" else "Review inbox", "task", day.minusDays(1))
                        item(if (russian) "Позвонить маме" else "Call Mom", "task", day.minusDays(2))
                    } else repeat(3) { item("QA idea ${it + 1}", "idea") }
                    return@withLock
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
        ui.runOnIdle { model.setDraft("") }
        if (visualV2) {
            ui.waitUntil(10_000) { ui.onAllNodesWithText(taskTitle).fetchSemanticsNodes().isNotEmpty() }
            capture("v2-today")
            listOf("Calendar", "Tasks", "Ideas", "Profile").forEach { label ->
                ui.onNodeWithContentDescription("Navigate $label").performClick()
                val tag = "${label.lowercase()}-list"
                ui.waitUntil(8_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
                when (label) {
                    "Calendar" -> {
                        ui.onNodeWithText(if (russian) "Месяц" else "Month").performClick()
                        capture("v2-calendar-month")
                        ui.onNodeWithText(if (russian) "Неделя" else "Week").performClick()
                        ui.onNodeWithText("08:00").assertExists()
                        capture("v2-calendar")
                    }
                    "Tasks" -> {
                        capture("v2-tasks")
                        ui.onNodeWithTag("filter-deadlines").performClick()
                        ui.waitUntil(8_000) { ui.onAllNodesWithText(taskTitle).fetchSemanticsNodes().isNotEmpty() }
                        capture("v2-tasks-deadlines")
                    }
                    "Ideas" -> {
                        val ideaTitle = if (russian) "Проект для агентов" else "Agent project"
                        ui.waitUntil(8_000) { ui.onAllNodesWithText(ideaTitle).fetchSemanticsNodes().isNotEmpty() }
                        capture("v2-ideas")
                    }
                    else -> {
                        ui.waitUntil(8_000) { model.localReads.value["profile"] == "ready" }
                        capture("v2-profile")
                    }
                }
            }
            ui.onNodeWithTag("capture-bar").performClick()
            val raw = if (russian) "Записаться к стоматологу в 6" else "Dentist at 6"
            ui.onNodeWithTag("capture").performTextInput(raw)
            ui.runOnIdle { ViewCompat.getWindowInsetsController(ui.activity.window.decorView)?.hide(WindowInsetsCompat.Type.ime()) }
            val token = if (russian) "в 6" else "at 6"
            ui.waitUntil(8_000) { ui.onAllNodesWithContentDescription(token).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithContentDescription(token).performClick()
            ui.onNodeWithText(if (russian) "Время" else "Time").assertIsDisplayed()
            capture("v2-composer-token")
            ui.onNodeWithText(if (russian) "Время" else "Time").performClick()
            ui.onNodeWithText("18:00").performScrollTo().performClick()
            ui.onNodeWithText(if (russian) "Завтра" else "Tomorrow").performScrollTo()
            capture("v2-composer-question")
            ui.onNodeWithText(if (russian) "Завтра" else "Tomorrow").performScrollTo().performClick()
            ui.onNodeWithText(if (russian) "Интерпретация" else "Interpretation").performScrollTo()
            capture("v2-composer-ready")
            ui.onNodeWithTag("send").performClick()
            ui.waitUntil(10_000) {
                runBlocking {
                    repo.dao.notePage(50, 0).firstOrNull { it.originalContent == raw }?.let { note ->
                        note.privacyMode == "LOCAL_ONLY" && repo.dao.recordsForNote(note.id).singleOrNull()?.let {
                            it.baseKind == "event" && it.isUserConfirmed &&
                                it.startAt == isoInstant(day.plusDays(1).atTime(18, 0).atZone(zone).toInstant())
                        } == true
                    } == true
                }
            }
            ui.waitUntil(8_000) { ui.onAllNodesWithTag("profile-list").fetchSemanticsNodes().isNotEmpty() }
            return
        }
        if(empty) {
            ui.waitUntil(10_000) {ui.onAllNodesWithText("A place for every thought").fetchSemanticsNodes().isNotEmpty()}
            capture("welcome-private")
            ui.onNodeWithTag("capture-bar").performClick()
            ui.onNodeWithTag("privacy").assert(hasContentDescription(ui.activity.getString(R.string.today_private_mode)))
            return
        }
        if (warmToday) {
            val resources = ui.activity.resources
            fun waitForRoute(tag: String) {
                ui.waitUntil(10_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
            }
            fun assertTouchTarget(node: SemanticsNodeInteraction) {
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue(
                    "Expected a 48 dp target, found $bounds",
                    bounds.right - bounds.left >= 47.99.dp && bounds.bottom - bounds.top >= 47.99.dp,
                )
            }
            val readyText = resources.getQuantityString(R.plurals.today_saved_ideas, 3, 3)
            ui.waitUntil(10_000) { ui.onAllNodesWithText(taskTitle).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText(taskTitle).assertIsDisplayed()
            assertTouchTarget(ui.onNodeWithTag("capture-bar"))
            assertTouchTarget(ui.onNodeWithContentDescription(resources.getString(R.string.today_open_profile)).assertHasClickAction())
            assertTouchTarget(ui.onNodeWithContentDescription(resources.getString(R.string.today_open_settings)).assertHasClickAction())
            capture("warm-today")
            ui.onNodeWithTag("today-list").performScrollToNode(hasText(meetingTitle))
            ui.onNodeWithText(meetingTitle).assertIsDisplayed()
            val coffee = resources.getString(R.string.today_restore, coffeeTitle)
            ui.onNodeWithTag("today-list").performScrollToNode(hasContentDescription(coffee))
            assertTouchTarget(ui.onNodeWithContentDescription(coffee).assertIsOn())
            val collection = resources.getString(R.string.today_collection)
            ui.onNodeWithTag("today-list").performScrollToNode(hasText(collection))
            ui.onNode(hasText(collection) and hasClickAction()).assert(hasText(readyText)).performClick()
            waitForRoute("ideas-list")
            ui.onNodeWithTag("ideas-list").assertIsDisplayed()
            ui.onNodeWithContentDescription("Navigate Today").performClick()
            waitForRoute("today-list")
            ui.onNodeWithTag("today-list").performScrollToIndex(0)
            val dayDescription = day.plusDays(1).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
            val tomorrow = ui.onNodeWithContentDescription(dayDescription)
            assertTouchTarget(tomorrow)
            tomorrow.performClick()
            waitForRoute("calendar-list")
            ui.onNodeWithTag("calendar-list").assertIsDisplayed()
            assertEquals(day.plusDays(1), model.selectedDate.value)
            ui.onNodeWithContentDescription("Navigate Today").performClick()
            waitForRoute("today-list")
            ui.onNodeWithTag("today-list").performScrollToIndex(0)
            runBlocking { deps.preferences().set("appearance", "dark") }
            ui.waitUntil(10_000) { model.settings.value["appearance"] == "dark" }
            capture("warm-today-dark")
            ui.onNodeWithTag("capture-bar").performClick()
            ui.onNodeWithTag("capture").performTextInput(if (russian) "Мысль для проверки" else "A thought for the visual check")
            ui.onNodeWithTag("send").assertIsEnabled()
            ui.waitUntil(10_000) {
                ui.runOnIdle {
                    ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
            }
            ui.onNodeWithContentDescription("Navigate Today").assertDoesNotExist()
            capture("warm-today-ime")
            ui.runOnIdle { model.setDraft("") }
            runBlocking { deps.preferences().set("appearance", "light") }
            return
        }
        ui.waitUntil(10_000) {model.todayItems.value.any {it.title=="Finish draft"}}
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
