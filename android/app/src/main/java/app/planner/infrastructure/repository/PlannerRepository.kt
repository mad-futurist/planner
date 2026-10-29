package app.planner.infrastructure.repository

import androidx.room.withTransaction
import app.planner.domain.interpretation.CapturePreview
import app.planner.domain.model.IdeaSlot
import app.planner.domain.model.InterpretationHistory
import app.planner.domain.model.Meta
import app.planner.domain.model.OutboxOperation
import app.planner.domain.model.RawNote
import app.planner.domain.model.Record
import app.planner.domain.model.UserCorrection
import app.planner.domain.policy.CaptureContext
import app.planner.domain.policy.CaptureNote
import app.planner.domain.policy.CapturePlan
import app.planner.domain.policy.LocalCapturePlan
import app.planner.domain.policy.completeTask
import app.planner.domain.policy.dayBounds
import app.planner.domain.policy.isVisibleToday
import app.planner.domain.policy.validateRecord
import app.planner.domain.time.isoInstant
import app.planner.domain.time.normalizeInstant
import app.planner.infrastructure.database.PlannerDatabase
import com.google.gson.*
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

@Singleton
class PlannerRepository @Inject constructor(val database: PlannerDatabase, val gson: Gson, private val scheduler: app.planner.application.port.SyncRequest = app.planner.application.port.SyncRequest {}) : app.planner.application.port.CaptureStore, app.planner.application.port.OrganizerCommands {
    val dao = database.dao()
    val syncMutex = Mutex()
    fun requestSync() = scheduler.request()

    suspend fun mergeNote(note: RawNote, fromAcknowledgment: Boolean = false) = database.withTransaction {
        val existing = dao.noteById(note.id)
        if (existing?.privacyMode == "LOCAL_ONLY") return@withTransaction
        if (dao.pendingForNote(note.id) > 0) {
            if (!fromAcknowledgment) deferIncoming("note", note.id, gson.toJsonTree(note).asJsonObject)
            return@withTransaction
        }
        if (existing == null || note.version >= existing.version) dao.note(note)
    }

    suspend fun mergeRecord(record: Record) = database.withTransaction {
        if (dao.noteById(record.sourceNoteId)?.privacyMode == "LOCAL_ONLY") return@withTransaction
        if (dao.pendingForNote(record.sourceNoteId) > 0) { deferIncoming("record", record.id, gson.toJsonTree(record).asJsonObject); return@withTransaction }
        if (!record.isUserConfirmed && dao.confirmedForNote(record.sourceNoteId) > 0) return@withTransaction
        val existing = dao.recordById(record.id)
        if (existing == null || record.version >= existing.version) dao.record(record)
    }

    private suspend fun deferIncoming(kind: String, id: String, value: JsonObject) {
        val key = "incoming:$kind:$id"
        val previous = dao.metadata(key)?.let { JsonParser.parseString(it.value).asJsonObject }
        if (previous == null || value.get("version").asInt >= previous.get("version").asInt) dao.meta(Meta(key, value.toString()))
    }

    /** Latest remote snapshots survive cursor advancement and process death. */
    suspend fun reconcileIncoming() = database.withTransaction {
        val entries = dao.incoming(dao.metadata("inboxScan")?.value.orEmpty())
        for (entry in entries) {
            if (entry.key.startsWith("incoming:note:")) {
                val note = gson.fromJson(entry.value, RawNote::class.java)
                if (dao.pendingForNote(note.id) > 0) continue
                mergeNote(note)
            } else {
                val record = gson.fromJson(entry.value, Record::class.java)
                if (dao.pendingForNote(record.sourceNoteId) > 0) continue
                mergeRecord(record)
            }
            // Only replay consumes the inbox. Note processing status can change at the
            // same input version, so an older POST acknowledgment must not erase it.
            if (dao.metadata(entry.key)?.value == entry.value) dao.deleteMeta(entry.key)
        }
        dao.meta(Meta("inboxScan", if (entries.size < 100) "" else entries.last().key))
    }

    suspend fun queueOperation(operation: OutboxOperation) {
        val orderedTime = maxOf(System.currentTimeMillis(), (dao.lastOperationTime() ?: 0L) + 1)
        dao.operation(operation.copy(createdAt = orderedTime))
    }

    fun recordJson(record: Record): JsonObject = gson.toJsonTree(record).asJsonObject.apply {
        remove("fieldsJson")
        add("fields", JsonParser.parseString(record.fieldsJson))
    }
    fun noteJson(note: RawNote): JsonObject = gson.toJsonTree(note).asJsonObject.apply {
        remove("interpretationHintsJson")
        add("interpretationHints", JsonParser.parseString(note.interpretationHintsJson))
    }
    fun noteFromJson(value: JsonObject): RawNote = gson.fromJson(value.deepCopy().apply {
        addProperty("interpretationHintsJson", (remove("interpretationHints") ?: JsonObject()).toString())
    }, RawNote::class.java)
    fun recordFromJson(json: JsonObject): Record = gson.fromJson(json.deepCopy().apply {
        addProperty("fieldsJson", (get("fields") ?: JsonObject()).toString()); remove("fields")
    }, Record::class.java)

    suspend fun capture(text: String, private: Boolean, source: String = "APP", manualKind: String? = null, plannedFor: String? = null, interpretationHintsJson: String = "{}", eventStartAt: String? = null, rawOnly: Boolean = false, dueDate: String? = null): RawNote {
        return app.planner.application.usecase.CaptureUseCase(this).capture(text, private, source, manualKind, plannedFor, interpretationHintsJson, eventStartAt, rawOnly, dueDate)
    }

    override suspend fun saveCapture(plan: CapturePlan): RawNote {
        val (note, record) = plan
        database.withTransaction {
            dao.note(note)
            record?.let { dao.record(it) }
            if (note.privacyMode != "LOCAL_ONLY") {
                val payload = noteJson(note)
                payload.add("records", JsonArray().apply { record?.let { add(recordJson(it)) } })
                queueOperation(OutboxOperation(entityId = note.id, noteId = note.id, path = "notes", payload = payload.toString()))
            }
        }
        runCatching { requestSync() }
        return note
    }

    suspend fun captureLocal(text: String, preview: CapturePreview, context: CaptureContext, interpretationHintsJson: String = "{}"): LocalCapturePlan {
        return app.planner.application.usecase.CaptureUseCase(this).local(text, preview, context, interpretationHintsJson)
    }

    override suspend fun saveLocal(plan: LocalCapturePlan, preview: CapturePreview): LocalCapturePlan {
        database.withTransaction {
            dao.note(plan.note)
            plan.records.forEach { dao.record(it) }
            dao.history(InterpretationHistory(id = java.util.UUID.randomUUID().toString(), noteId = plan.note.id,
                inputVersion = plan.note.version, schemaVersion = preview.schemaVersion, modelVersion = preview.engine,
                proposedData = gson.toJson(preview), validationResult = "valid", createdAt = plan.note.createdAt))
        }
        return plan
    }

    override suspend fun edit(record: Record) {
        validateRecord(record)
        database.withTransaction {
            val before = dao.recordById(record.id) ?: return@withTransaction
            require(record.version == before.version) { "This item changed while you were editing. Reopen its details to review the latest version." }
            val updated = record.copy(version = before.version + 1, isUserConfirmed = true, updatedAt = isoInstant(), startAt = normalizeInstant(record.startAt), endAt = normalizeInstant(record.endAt), dueAt = normalizeInstant(record.dueAt), remindAt = normalizeInstant(record.remindAt), completedAt = normalizeInstant(record.completedAt))
            dao.record(updated)
            dao.correction(UserCorrection(sourceNoteId = updated.sourceNoteId, previousInterpretation = recordJson(before).toString(), correctedInterpretation = recordJson(updated).toString()))
            val note = dao.noteById(updated.sourceNoteId) ?: return@withTransaction
            if (note.privacyMode != "LOCAL_ONLY") {
                val payload = recordJson(updated).apply { addProperty("expectedVersion", before.version) }
                queueOperation(OutboxOperation(entityId = updated.id, noteId = note.id, path = "records/${updated.id}", method = "PATCH", payload = payload.toString()))
            }
        }
        runCatching { requestSync() }
    }

    override suspend fun complete(record: Record, undo: Boolean) = database.withTransaction {
        val current = dao.recordById(record.id) ?: return@withTransaction
        if ((undo && current.state == "pending") || (!undo && current.state == "completed")) return@withTransaction
        edit(completeTask(current, undo))
    }

    suspend fun selectedIdea(date: LocalDate): Record? = database.withTransaction {
        val day = date.toString()
        val slot = dao.ideaSlot(day) ?: dao.ideaCandidate(day)?.let { IdeaSlot(day, it.id).also { chosen -> dao.slot(chosen) } } ?: return@withTransaction null
        slot.recordId?.let { dao.recordById(it) }?.takeIf { it.baseKind == "idea" && it.state == "pending" && (it.deferredUntil == null || it.deferredUntil <= day) }
    }
    override suspend fun later(record: Record, date: LocalDate) = edit(record.copy(deferredUntil = date.plusDays(1).toString()))

    /** Keep one daily set stable. Snoozes and completion hide a slot without silently replacing it. */
    suspend fun reviewTasks(date: LocalDate): List<Record> = database.withTransaction {
        val key = "task-review:$date"
        val saved = dao.metadata(key)
        val ids = if (saved != null) JsonParser.parseString(saved.value).asJsonArray.map { it.asString } else {
            val bounds = dayBounds(date, ZoneId.systemDefault())
            val candidates = dao.reviewCandidates(date.toString(), bounds.start, isoInstant(date.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant())).first()
            if (candidates.isNotEmpty()) {
                dao.meta(Meta(key, gson.toJson(candidates.map { it.id })))
                candidates.forEach { dao.meta(Meta("task-seen:${it.id}", date.toString())) }
            }
            candidates.map { it.id }
        }
        val bounds = dayBounds(date, ZoneId.systemDefault())
        val day = date.toString()
        val cutoff = isoInstant(date.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant())
        ids.mapNotNull { dao.recordById(it) }.filter {
            it.state == "pending" && it.updatedAt < cutoff && (it.plannedFor ?: it.dueDate ?: "") < day &&
                (it.startAt ?: it.dueAt ?: "") < bounds.start && (dao.metadata("task-snooze:${it.id}")?.value ?: "") <= day &&
                !isVisibleToday(it, date, ZoneId.systemDefault())
        }
    }

    override suspend fun snoozeTask(record: Record, date: LocalDate) {
        dao.meta(Meta("task-snooze:${record.id}", date.plusDays(7).toString()))
    }

    /** New raw capture + new confirmed record; the originating idea/task is never rewritten. */
    override suspend fun linkedTask(origin: Record, title: String, plannedFor: String?, private: Boolean): Record = database.withTransaction {
        require(title.isNotBlank() && title.length <= 1000) { "Enter an action of up to 1,000 characters." }
        val current = dao.recordById(origin.id) ?: error("The original item is no longer available.")
        require(current.state == "pending" && current.baseKind in setOf("idea", "task")) { "Restore this item before creating an action." }
        val note = dao.noteById(current.sourceNoteId) ?: error("The original note is unavailable.")
        val (newNote, planned) = CaptureNote.plan(title, private || note.privacyMode == "LOCAL_ONLY", "APP", "task", plannedFor)
        val task = planned!!.copy(fieldsJson = JsonObject().apply { addProperty(if (current.baseKind == "idea") "developedFrom" else "splitFrom", current.id) }.toString())
        dao.note(newNote); dao.record(task)
        if (newNote.privacyMode != "LOCAL_ONLY") {
            val payload = noteJson(newNote).apply { add("records", JsonArray().apply { add(recordJson(task)) }) }
            queueOperation(OutboxOperation(entityId = newNote.id, noteId = newNote.id, path = "notes", payload = payload.toString()))
        }
        runCatching { requestSync() }
        task
    }

    override suspend fun manualInterpretation(note: RawNote, kind: String, title: String, plannedFor: String?) {
        require(title.isNotBlank() && title.length <= 1000) { "Enter a title of up to 1,000 characters." }
        database.withTransaction {
            val record = Record(sourceNoteId = note.id, baseKind = kind, title = title, plannedFor = plannedFor, timezone = note.timezone, isUserConfirmed = true)
            dao.record(record)
            dao.noteStatus(note.id, "organized")
            dao.correction(UserCorrection(sourceNoteId = note.id, previousInterpretation = "{}", correctedInterpretation = recordJson(record).toString()))
            if (note.privacyMode != "LOCAL_ONLY") {
                val payload = JsonObject().apply {
                    addProperty("noteId", note.id)
                    addProperty("expectedNoteVersion", note.version)
                    add("records", JsonArray().apply { add(recordJson(record)) })
                }
                queueOperation(OutboxOperation(entityId = record.id, noteId = note.id, path = "notes/${note.id}/corrections", payload = payload.toString()))
            }
        }
        runCatching { requestSync() }
    }

    override suspend fun reprocess(note: RawNote) {
        require(note.privacyMode != "LOCAL_ONLY") { "This note is private. Organize it manually." }
        database.withTransaction {
            dao.noteStatus(note.id, "pending")
            queueOperation(OutboxOperation(entityId = note.id, noteId = note.id, path = "notes/${note.id}/reprocess", payload = "{}"))
        }
        runCatching { requestSync() }
    }

    override suspend fun postponeReview(noteId: String, date: LocalDate) { dao.meta(Meta("triage:$noteId", date.toString())) }
    override suspend fun captureDuration(milliseconds: Long) { dao.meta(Meta("lastCaptureMs", milliseconds.toString())) }

    suspend fun clear() = database.withTransaction {
        dao.clearOutbox(); dao.clearRecords(); dao.clearNotes(); dao.clearHistories(); dao.clearCorrections(); dao.clearSlots(); dao.clearTypes(); dao.clearRules(); dao.clearMeta()
    }
}
