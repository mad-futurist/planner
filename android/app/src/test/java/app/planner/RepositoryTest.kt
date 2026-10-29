package app.planner

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.planner.core.database.*
import app.planner.core.model.*
import app.planner.data.PlannerRepository
import app.planner.domain.*
import com.google.gson.Gson
import java.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class RepositoryTest {
    private lateinit var db: PlannerDatabase
    private lateinit var repo: PlannerRepository
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PlannerDatabase::class.java).build(); repo = PlannerRepository(db, Gson()) }
    @After fun close() { db.close() }
    @Test fun captureAtomicallySavesNoteAndOutbox() = runBlocking {
        val note = repo.capture("A thought", false)
        assertEquals("A thought", db.dao().noteById(note.id)?.originalContent)
        assertEquals(note.id, db.dao().nextOperation(Long.MAX_VALUE)?.entityId)
        assertEquals(note, PlannerRepository(db, Gson()).dao.noteById(note.id))
    }
    @Test fun privateCaptureNeverUploads() = runBlocking {
        repo.capture("Private", true)
        assertNull(db.dao().nextOperation(Long.MAX_VALUE))
    }
    private fun localInterpreter() = CaptureInterpreter(Gson().fromJson(java.io.File("../../backend/app/domain/assets/capture_rules_v1.json").readText(), CaptureRules::class.java))
    @Test fun tentativeActionCannotBeAppliedBeforeUserChoosesItsMeaning() = runBlocking {
        val text = "maybe find ai course"
        val proposal = localInterpreter().preview(text, localContext)
        assertEquals(listOf("task_deadline", "task_unscheduled", "idea"), proposal.records.single().kindChoices)
        assertTrue(runCatching { repo.captureLocal(text, proposal, localContext) }.isFailure)
        assertEquals(0, repo.dao.noteCount())
        val chosen = repo.capture(text, true, manualKind = "idea")
        assertEquals("idea", repo.dao.recordsForNote(chosen.id).single().baseKind)
        assertEquals(text, chosen.originalContent)
    }
    @Test fun explicitTaskDeadlineDoesNotInventAScheduleOrExecutionDay() = runBlocking {
        val note = repo.capture("maybe find ai course", true, manualKind = "task", dueDate = "2026-11-03")
        val record = repo.dao.recordsForNote(note.id).single()
        assertEquals("2026-11-03", record.dueDate)
        assertNull(record.startAt); assertNull(record.plannedFor)
        assertTrue(record.isUserConfirmed)
        assertNull(repo.dao.nextOperation(Long.MAX_VALUE))
        assertEquals("upcoming", taskBucket(record, LocalDate.parse("2026-10-28"), ZoneId.of("Europe/Paris")))
    }
    private val localContext get() = CaptureContext(Instant.parse("2026-10-28T10:00:00Z"), ZoneId.of("Europe/Paris"), "en")
    @Test fun localDefaultProposalCreatesAllRecordsAndHistoryWithoutUpload() = runBlocking {
        val text = "dentist 2026-10-31 at 16; buy milk; idea build a garden"
        val result = repo.captureLocal(text, localInterpreter().preview(text, localContext), localContext)
        assertEquals(text, repo.dao.noteById(result.note.id)!!.originalContent)
        assertEquals(listOf("event", "task", "idea"), result.records.map { it.baseKind })
        assertEquals(3, repo.dao.recordsForNote(result.note.id).size)
        assertEquals("LOCAL_ONLY", result.note.privacyMode)
        assertFalse(result.records.any { it.isUserConfirmed })
        assertNull(repo.dao.nextOperation(Long.MAX_VALUE))
        assertEquals("local-rules-v1", repo.dao.latestHistory(result.note.id)!!.modelVersion)
        val bounds = dayBounds(LocalDate.parse("2026-10-31"), localContext.timezone)
        assertTrue(repo.dao.day(bounds.day, bounds.start, bounds.end).first().any { it.id == result.records.first().id })
        assertTrue(repo.dao.tasks("someday", "2026-10-28", bounds.end).first().any { it.id == result.records[1].id })
    }
    @Test fun localEnFrRuTasksAndAppointmentsArePersisted() = runBlocking {
        val parser = localInterpreter()
        for (text in listOf("buy milk", "acheter du lait", "купить молоко", "dentist 2026-10-31 at 16", "dentiste 2026-10-31 à 16", "стоматолог 2026-10-31 в 16")) {
            val result = repo.captureLocal(text, parser.preview(text, localContext), localContext)
            assertEquals(text, result.note.originalContent)
            val record = repo.dao.recordsForNote(result.note.id).single()
            if (text.contains("2026")) assertEquals(Instant.parse("2026-10-31T15:00:00Z"), Instant.parse(record.startAt))
            else { assertEquals("task", record.baseKind); assertEquals("someday", taskBucket(record, LocalDate.parse("2026-10-28"), localContext.timezone)) }
        }
    }
    @Test fun incompleteLocalInterpretationCannotLeavePartialNoteOrRecords() = runBlocking {
        val text = "buy milk; dentist at 16"
        assertTrue(runCatching { repo.captureLocal(text, localInterpreter().preview(text, localContext), localContext) }.isFailure)
        assertEquals(0, repo.dao.noteCount())
        val resolved = localInterpreter().preview("dentist 2026-10-31 at 16", localContext)
        val forged = resolved.copy(records = resolved.records.map { it.copy(calendarEligible = false) })
        assertTrue(runCatching { repo.captureLocal("dentist 2026-10-31 at 16", forged, localContext) }.isFailure)
        assertEquals(0, repo.dao.noteCount())
    }
    @Test fun localDeadlineWithClockIsDueTimeRatherThanAppointment() = runBlocking {
        val text = "finish report by 2026-10-31 at 16"
        val record = repo.captureLocal(text, localInterpreter().preview(text, localContext), localContext).records.single()
        assertNull(record.startAt)
        assertNull(record.dueDate)
        assertEquals("2026-10-31T15:00:00.000Z", record.dueAt)
        assertFalse(isScheduledOnDay(record, LocalDate.parse("2026-10-31"), localContext.timezone))
        assertEquals("upcoming", taskBucket(record, LocalDate.parse("2026-10-28"), localContext.timezone))
    }
    @Test fun localInterpretationRemainsCorrectableAndPreservesOriginal() = runBlocking {
        val text = "buy milk"
        val result = repo.captureLocal(text, localInterpreter().preview(text, localContext), localContext)
        repo.edit(result.records.single().copy(title = "Buy oat milk"))
        assertEquals(text, repo.dao.noteById(result.note.id)!!.originalContent)
        assertEquals("Buy oat milk", repo.dao.recordsForNote(result.note.id).single().title)
        assertNotNull(repo.dao.latestHistory(result.note.id))
        assertNull(repo.dao.nextOperation(Long.MAX_VALUE))
    }
    @Test fun explicitlySavingRawOnlyNeverQueuesAutomaticInterpretation() = runBlocking {
        val note = repo.capture("dentist 16", false, rawOnly = true)
        assertTrue(note.manual)
        assertTrue(db.dao().recordsForNote(note.id).isEmpty())
        val payload = com.google.gson.JsonParser.parseString(db.dao().nextOperation(Long.MAX_VALUE)!!.payload).asJsonObject
        assertTrue(payload["manual"].asBoolean)
        assertEquals(0, payload.getAsJsonArray("records").size())
    }
    @Test fun pendingEditRetainsNewestSiblingSnapshotBeyondCursorAndAcknowledgment() = runBlocking {
        val note = repo.capture("Two tasks", false, manualKind = "task")
        db.dao().operationsForEntity(note.id).forEach { db.dao().deleteOperation(it.id) }
        val first = db.dao().recordsForNote(note.id).single()
        val sibling = Record(sourceNoteId = note.id, title = "Before", isUserConfirmed = true)
        db.dao().record(sibling)
        repo.edit(first.copy(title = "Local edit"))
        repo.mergeRecord(sibling.copy(title = "Newest", version = 3))
        repo.mergeRecord(sibling.copy(title = "Older", version = 2))
        db.dao().meta(Meta("cursor", "42"))
        assertEquals("Before", db.dao().recordById(sibling.id)!!.title)
        db.dao().operationsForEntity(first.id).forEach { db.dao().deleteOperation(it.id) }
        PlannerRepository(db, Gson()).reconcileIncoming()
        assertEquals("Newest", db.dao().recordById(sibling.id)!!.title)
        assertEquals("42", db.dao().metadata("cursor")!!.value)
        assertTrue(db.dao().incoming().isEmpty())
    }
    @Test fun olderAcknowledgmentCannotEraseDeferredStatusAtTheSameNoteVersion() = runBlocking {
        val note = repo.capture("Cloud note", false)
        repo.mergeNote(note.copy(processingStatus = "organized", syncStatus = "synced"))
        db.dao().operationsForEntity(note.id).forEach { db.dao().deleteOperation(it.id) }
        val followup = OutboxOperation(entityId = note.id, noteId = note.id, path = "notes/${note.id}/reprocess", payload = "{}")
        repo.queueOperation(followup)
        repo.mergeNote(note.copy(processingStatus = "pending"), fromAcknowledgment = true)
        db.dao().deleteOperation(followup.id)
        repo.mergeNote(note.copy(processingStatus = "pending"), fromAcknowledgment = true)
        repo.reconcileIncoming()
        assertEquals("organized", db.dao().noteById(note.id)!!.processingStatus)
        assertTrue(db.dao().incoming().isEmpty())
    }
    @Test fun diskDatabaseSurvivesCloseAndRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("persistence-test.db")
        val first = Room.databaseBuilder(context, PlannerDatabase::class.java, "persistence-test.db").build()
        val note = PlannerRepository(first, Gson()).capture("Persistent", true)
        first.close()
        val second = Room.databaseBuilder(context, PlannerDatabase::class.java, "persistence-test.db").build()
        assertEquals("Persistent", second.dao().noteById(note.id)?.content)
        second.close(); context.deleteDatabase("persistence-test.db"); Unit
    }
    @Test fun completionAndUndoPreserveRawInput() = runBlocking {
        val note = repo.capture("Read", true, manualKind = "task", plannedFor = "2026-10-14")
        val record = db.dao().recordsForNote(note.id).single()
        repo.complete(record)
        val complete = db.dao().recordById(record.id)!!
        assertEquals("completed", complete.state); assertNotNull(complete.completedAt)
        repo.complete(complete, true)
        assertNull(db.dao().recordById(record.id)?.completedAt)
        assertEquals("Read", db.dao().noteById(note.id)?.originalContent)
    }
    @Test fun ideaSelectionIsStableAndLaterDoesNotReplaceIt() = runBlocking {
        repo.capture("First idea", true, manualKind = "idea")
        val date = LocalDate.of(2026, 10, 14)
        val chosen = repo.selectedIdea(date)!!
        repo.capture("Another idea", true, manualKind = "idea")
        assertEquals(chosen.id, repo.selectedIdea(date)?.id)
        repo.later(chosen, date)
        assertNull(repo.selectedIdea(date))
        assertNotNull(repo.selectedIdea(date.plusDays(1)))
    }
    @Test fun todaySeparatesDeadlineAndSchedulingAndHandlesDst() {
        val day = LocalDate.of(2026, 10, 25); val zone = ZoneId.of("Europe/Paris")
        val bounds = dayBounds(day, zone)
        assertEquals(25, Duration.between(Instant.parse(bounds.start), Instant.parse(bounds.end)).toHours())
        assertTrue(isVisibleToday(Record(sourceNoteId = "n", title = "Due", dueDate = day.toString()), day, zone))
        assertFalse(isVisibleToday(Record(sourceNoteId = "n", title = "Later", plannedFor = day.plusDays(1).toString()), day, zone))
        assertFalse(isVisibleToday(Record(sourceNoteId = "n", title = "Done", plannedFor = day.toString(), state = "completed"), day, zone))
    }
    @Test fun retriesAreBoundedAndJittered() { assertTrue(retryDelayMillis(2, 0.0) < retryDelayMillis(2, 1.0)); assertTrue(retryDelayMillis(100, 1.0) <= 21_600_000) }
    @Test fun scheduledMembershipHandlesContinuingSpansAndFutureStartsSeparatelyFromDeadlines() = runBlocking {
        val day = LocalDate.of(2026, 10, 15); val zone = ZoneId.of("Europe/Paris")
        val note = repo.capture("A timed item", true)
        val ongoing = Record(sourceNoteId = note.id, title = "Continues", baseKind = "event", startAt = "2026-10-14T13:00:00.000Z", endAt = "2026-10-15T16:00:00.000Z")
        val future = Record(sourceNoteId = note.id, title = "Future start, today's deadline", startAt = "2026-10-16T13:00:00.000Z", dueDate = day.toString())
        db.dao().record(ongoing); db.dao().record(future)
        val bounds = dayBounds(day, zone)
        assertEquals(2, db.dao().day(bounds.day, bounds.start, bounds.end).first().size)
        assertTrue(isScheduledOnDay(ongoing, day, zone))
        assertFalse(isScheduledOnDay(future, day, zone))
        assertFalse(isScheduledOnDay(ongoing.copy(state = "archived"), day, zone))
    }

    @Test fun pendingCorrectionsAndConfirmedRecordsRejectStaleAi() = runBlocking {
        val note = repo.capture("Idea", false, manualKind = "idea")
        val local = db.dao().recordsForNote(note.id).single()
        repo.edit(local.copy(title = "Corrected"))
        repo.mergeRecord(local.copy(title = "Late AI", isUserConfirmed = false, version = 100))
        assertEquals("Corrected", db.dao().recordById(local.id)?.title)
        for (op in db.dao().operationsForEntity(note.id) + db.dao().operationsForEntity(local.id)) db.dao().deleteOperation(op.id)
        repo.mergeRecord(local.copy(id = java.util.UUID.randomUUID().toString(), title = "Unexpected task", baseKind = "task", isUserConfirmed = false))
        assertEquals(1, db.dao().recordsForNote(note.id).size)
    }
    @Test fun outboxRetainsRetryIdentityAndOrdersParentBeforeEdits() = runBlocking {
        val note = repo.capture("Task", false, manualKind = "task")
        val record = db.dao().recordsForNote(note.id).single()
        repo.edit(record.copy(title = "Changed"))
        val first = db.dao().nextOperation(Long.MAX_VALUE)!!
        assertEquals("notes", first.path)
        db.dao().operation(first.copy(retryCount = 1))
        assertEquals(first.id, db.dao().nextOperation(Long.MAX_VALUE)?.id)
        db.dao().deleteOperation(first.id)
        assertEquals("PATCH", db.dao().nextOperation(Long.MAX_VALUE)?.method)
    }
    @Test fun failedBackgroundSchedulingDoesNotTurnCommittedCaptureIntoFailure() = runBlocking {
        repo = PlannerRepository(db, Gson(), app.planner.application.SyncRequest { error("Unavailable scheduler") })
        val saved = repo.capture("Durable without scheduler", false)
        assertEquals(saved.content, db.dao().noteById(saved.id)?.content)
        assertNotNull(db.dao().nextOperation(Long.MAX_VALUE))
    }
    @Test fun staleEditorCannotOverwriteANewerCorrection() = runBlocking {
        val note = repo.capture("Original", true, manualKind = "task")
        val before = db.dao().recordsForNote(note.id).single()
        repo.edit(before.copy(title = "New correction"))
        val result = runCatching { repo.edit(before.copy(title = "Stale form")) }
        assertTrue(result.isFailure)
        assertEquals("New correction", db.dao().recordById(before.id)?.title)
    }
    @Test fun completionUsesLatestFieldsAndDuplicateTapsDoNotRewriteTimestamp() = runBlocking {
        val note = repo.capture("Original title", true, manualKind = "task")
        val row = db.dao().recordsForNote(note.id).single()
        repo.edit(row.copy(title = "Updated title"))
        repo.complete(row)
        val done = db.dao().recordById(row.id)!!
        repo.complete(row)
        assertEquals(done, db.dao().recordById(row.id))
        assertEquals("Updated title", done.title)
        repo.complete(row, true)
        assertEquals("pending", db.dao().recordById(row.id)?.state)
    }
    @Test fun legacySchemasUpgradeWithoutLosingNotesOrOutbox() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (version in listOf(1, 2, 3)) {
            val name = "migration-$version.db"
            context.deleteDatabase(name)
            val schema = com.google.gson.JsonParser.parseString(java.io.File("schemas/app.planner.core.database.PlannerDatabase/$version.json").readText()).asJsonObject.getAsJsonObject("database")
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { legacy ->
                for (entry in schema.getAsJsonArray("entities")) {
                    val entity = entry.asJsonObject
                    fun sql(value: String) = value.replace("\${TABLE_NAME}", entity.get("tableName").asString)
                    legacy.execSQL(sql(entity.get("createSql").asString))
                    for (index in entity.getAsJsonArray("indices") ?: com.google.gson.JsonArray()) legacy.execSQL(sql(index.asJsonObject.get("createSql").asString))
                }
                legacy.execSQL("INSERT INTO notes VALUES (?, ?, ?, ?, ?, 'APP', 'CLOUD_ALLOWED', 'pending', 'pending', 1, 'en', 'UTC', 0)", arrayOf("legacy", "Unchanged", "Unchanged", "2026-10-14T00:00:00Z", "2026-10-14T00:00:00.123456Z"))
                legacy.execSQL("INSERT INTO outbox VALUES ('op', 'legacy', 'legacy', 'notes', 'POST', '{}', 2, 0, NULL, 'queued', 1)")
                legacy.version = version
            }
            val upgraded = Room.databaseBuilder(context, PlannerDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
            try {
                val saved = upgraded.dao().noteById("legacy")!!
                assertEquals("Unchanged", saved.originalContent)
                assertEquals("{}", saved.interpretationHintsJson)
                if (version == 1) assertEquals("2026-10-14T00:00:00.000Z", saved.createdAt)
                assertEquals(2, upgraded.dao().nextOperation(Long.MAX_VALUE)?.retryCount)
            } finally { upgraded.close(); context.deleteDatabase(name) }
        }
    }
    @Test fun undatedAndEarlierTasksRemainDiscoverableAndArchiveIsSeparate() = runBlocking {
        val date = LocalDate.of(2026, 10, 21); val bounds = dayBounds(date, ZoneId.of("UTC"))
        suspend fun task(title: String, planned: String? = null): Record = repo.dao.recordsForNote(repo.capture(title, true, manualKind = "task", plannedFor = planned).id).single()
        val undated = task("Unscheduled")
        val earlier = task("Earlier", "2026-10-01")
        val future = task("Future", "2026-10-22")
        val archived = task("Archived"); repo.edit(archived.copy(state = "archived"))
        assertEquals(listOf(undated.id), repo.dao.tasks("someday", bounds.day, bounds.end).first().map { it.id })
        assertEquals(listOf(earlier.id), repo.dao.tasks("today", bounds.day, bounds.end).first().map { it.id })
        assertEquals(listOf(future.id), repo.dao.tasks("upcoming", bounds.day, bounds.end).first().map { it.id })
        assertEquals(3, repo.dao.tasks("all", bounds.day, bounds.end).first().size)
        assertEquals(listOf(archived.id), repo.dao.tasks("archived", bounds.day, bounds.end).first().map { it.id })
        assertFalse(isVisibleToday(undated, date, ZoneId.of("UTC")))
        assertFalse(isVisibleToday(earlier, date, ZoneId.of("UTC")))
    }
    @Test fun taskDiscoveryPagesBeyondTheTodayBoundWithoutDroppingRecords() = runBlocking {
        repeat(106) { repo.capture("Unscheduled $it", true, manualKind = "task") }
        val day = dayBounds(LocalDate.now(), ZoneId.systemDefault())
        val ids = (0..2).flatMap { repo.dao.tasks("someday", day.day, day.end, offset = it * 50).first().map { it.id } }
        assertEquals(106, ids.toSet().size)
        assertEquals(106, repo.dao.taskCounts(day.day, day.end).first().single().count)
    }
    @Test fun overdueInstantUsesLocalDayBoundaryAndCompletionIsExcluded() = runBlocking {
        val bounds = dayBounds(LocalDate.of(2026, 10, 21), ZoneId.of("Europe/Paris"))
        val note = repo.capture("Deadline", true, manualKind = "task")
        val task = repo.dao.recordsForNote(note.id).single().copy(dueAt = "2026-10-21T22:15:00.000Z")
        repo.edit(task)
        assertEquals(1, repo.dao.tasks("upcoming", bounds.day, bounds.end).first().size)
        repo.complete(task)
        assertTrue(repo.dao.tasks("all", bounds.day, bounds.end).first().isEmpty())
        assertEquals(1, repo.dao.tasks("completed", bounds.day, bounds.end).first().size)
    }
    @Test fun ideaRotationIsStableAcrossDaysAndSkipsArchivedAlternatives() = runBlocking {
        repeat(3) { repo.capture("Idea $it", true, manualKind = "idea") }
        val date = LocalDate.of(2026, 10, 21)
        val first = repo.selectedIdea(date)!!; repo.later(first, date)
        assertNull(repo.selectedIdea(date))
        val second = repo.selectedIdea(date.plusDays(1))!!
        assertEquals(second.id, repo.selectedIdea(date.plusDays(1))!!.id)
        val third = repo.selectedIdea(date.plusDays(2))!!
        assertEquals(3, setOf(first.id, second.id, third.id).size)
        assertEquals(first.id, repo.selectedIdea(date.plusDays(3))!!.id)
        repo.edit(repo.dao.recordById(second.id)!!.copy(state = "archived"))
        assertNotEquals(second.id, repo.selectedIdea(date.plusDays(4))!!.id)
        val corrected = repo.selectedIdea(date.plusDays(4))!!
        repo.edit(corrected.copy(baseKind = "task"))
        assertNull(repo.selectedIdea(date.plusDays(4)))
    }
    @Test fun conversionPreservesIdeaAndQueuesProvenanceWithNewCloudCapture() = runBlocking {
        val note = repo.capture("Original idea", false, manualKind = "idea")
        val idea = repo.dao.recordsForNote(note.id).single()
        val task = repo.linkedTask(idea, private = false)
        assertEquals(idea, repo.dao.recordById(idea.id))
        assertEquals(idea.id, repo.dao.ideas(query = "Original idea").first().single().id)
        assertEquals("Original idea", repo.dao.noteById(note.id)!!.originalContent)
        assertNotEquals(note.id, task.sourceNoteId)
        assertEquals(idea.id, com.google.gson.JsonParser.parseString(task.fieldsJson).asJsonObject.get("developedFrom").asString)
        val operation = repo.dao.operationsForEntity(task.sourceNoteId).single()
        assertTrue(operation.payload.contains("developedFrom"))
        val before = repo.dao.noteCount()
        assertTrue(runCatching { repo.linkedTask(idea, " ") }.isFailure)
        assertEquals(before, repo.dao.noteCount())
    }
    @Test fun linkedActionsFailClosedToPrivateEvenWhenTheirSourceWasCloudAllowed() = runBlocking {
        val cloud = repo.capture("Previously shared idea", false, manualKind = "idea")
        val idea = repo.dao.recordsForNote(cloud.id).single()
        val privateTask = repo.linkedTask(idea, "A new private next step")
        assertEquals("LOCAL_ONLY", repo.dao.noteById(privateTask.sourceNoteId)?.privacyMode)
        assertEquals(0, repo.dao.pendingForNote(privateTask.sourceNoteId))
        val local = repo.capture("Private idea", true, manualKind = "idea")
        val localTask = repo.linkedTask(repo.dao.recordsForNote(local.id).single(), private = false)
        assertEquals("LOCAL_ONLY", repo.dao.noteById(localTask.sourceNoteId)?.privacyMode)
    }
    @Test fun oldTaskReviewIsBoundedPersistedAndSnoozeDoesNotInventDates() = runBlocking {
        val day = LocalDate.of(2026, 10, 21)
        repeat(5) {
            val note = repo.capture("Old task $it", true, manualKind = "task")
            val task = repo.dao.recordsForNote(note.id).single()
            repo.dao.record(task.copy(createdAt = "2026-09-01T00:00:00.000Z", updatedAt = "2026-09-01T00:00:00.000Z"))
        }
        val initial = repo.reviewTasks(day)
        assertEquals(3, initial.size)
        assertEquals(initial.map { it.id }, repo.reviewTasks(day).map { it.id })
        repo.snoozeTask(initial.first(), day)
        assertEquals(2, repo.reviewTasks(day).size)
        assertNull(repo.dao.recordById(initial.first().id)!!.dueDate)
        assertFalse(repo.reviewTasks(day.plusDays(1)).any { it.id == initial.first().id })
        assertTrue(repo.reviewTasks(day.plusDays(7)).any { it.id == initial.first().id })
    }
    @Test fun absentLegacyOrRevokedCloudConsentIsAlwaysPrivate() {
        val privacy = app.planner.core.CapturePrivacy
        assertTrue(privacy.isPrivate(emptyMap()))
        assertTrue(privacy.isPrivate(mapOf("privateCapture" to "false")))
        assertFalse(privacy.isPrivate(mapOf("privateCapture" to "false", "cloudConsent" to privacy.CONSENT_VERSION)))
        assertTrue(privacy.isPrivate(mapOf("privateCapture" to "true", "cloudConsent" to privacy.CONSENT_VERSION)))
    }
    @Test fun profileCountsActualSavedRecordsIncludingPreservedIdeas() = runBlocking {
        assertEquals(ProfileSummary(), repo.dao.profile().first())
        val note = repo.capture("Keep this idea", true, manualKind = "idea")
        val idea = repo.dao.recordsForNote(note.id).single()
        val task = repo.linkedTask(idea)
        assertEquals(ProfileSummary(notes = 2, ideas = 1, tasks = 1), repo.dao.profile().first())
        repo.complete(task)
        assertEquals(ProfileSummary(notes = 2, completed = 1, ideas = 1), repo.dao.profile().first())
    }
    @Test fun developmentApprovalRejectsStaleResultsAndCreatesTasksOnce() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = app.planner.sync.SyncEngine(context, repo, app.planner.core.Preferences(context), app.planner.core.network.TokenStore(context))
        val note = repo.capture("Saved idea", false, manualKind = "idea")
        val idea = db.dao().recordsForNote(note.id).single()
        fun result(version: Int) = """{"id":"job-fixture","status":"complete","recordVersion":$version,"result":{"steps":["Contact school","Recruit tutors","Schedule a pilot"]}}"""
        db.dao().meta(Meta("job:${idea.id}", result(idea.version + 1)))
        assertTrue(runCatching { engine.approve(idea, "job-fixture", listOf("Contact school")) }.isFailure)
        assertEquals(1, db.dao().noteCount())
        db.dao().meta(Meta("job:${idea.id}", result(idea.version)))
        engine.approve(idea, "job-fixture", listOf("Contact school", "Recruit tutors"))
        engine.approve(idea, "job-fixture", listOf("Contact school", "Recruit tutors"))
        assertEquals(3, db.dao().noteCount())
        assertEquals(2, db.dao().notePage(50, 0).flatMap { db.dao().recordsForNote(it.id) }.count { it.baseKind == "task" })
    }
}
