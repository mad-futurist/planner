package app.planner.data

import app.planner.core.database.*
import app.planner.core.model.*
import app.planner.domain.*
import app.planner.core.isoInstant
import app.planner.core.normalizeInstant
import androidx.room.withTransaction
import com.google.gson.*
import java.time.*
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.first

@Singleton
class PlannerRepository @Inject constructor(val database: PlannerDatabase, val gson: Gson) {
    val dao = database.dao()
    val syncMutex = Mutex()
    var requestSync: () -> Unit = {}

    suspend fun mergeNote(note: RawNote) {
        val existing = dao.noteById(note.id)
        if (existing?.privacyMode == "LOCAL_ONLY" || dao.pendingForNote(note.id) > 0) return
        if (existing == null || note.version >= existing.version) dao.note(note)
    }

    suspend fun mergeRecord(record: Record) {
        if (dao.noteById(record.sourceNoteId)?.privacyMode == "LOCAL_ONLY" || dao.pendingForNote(record.sourceNoteId) > 0) return
        if (!record.isUserConfirmed && dao.confirmedForNote(record.sourceNoteId) > 0) return
        val existing = dao.recordById(record.id)
        if (existing == null || record.version >= existing.version) dao.record(record)
    }

    suspend fun queueOperation(operation: OutboxOperation) {
        val orderedTime = maxOf(System.currentTimeMillis(), (dao.lastOperationTime() ?: 0L) + 1)
        dao.operation(operation.copy(createdAt = orderedTime))
    }

    fun recordJson(record: Record): JsonObject = gson.toJsonTree(record).asJsonObject.apply {
        remove("fieldsJson")
        add("fields", JsonParser.parseString(record.fieldsJson))
    }
    fun recordFromJson(json: JsonObject): Record = gson.fromJson(json.deepCopy().apply {
        addProperty("fieldsJson", (get("fields") ?: JsonObject()).toString()); remove("fields")
    }, Record::class.java)

    suspend fun capture(text: String, private: Boolean, source: String = "APP", manualKind: String? = null, plannedFor: String? = null): RawNote {
        val (note, record) = CaptureNote.plan(text, private, source, manualKind, plannedFor)
        database.withTransaction {
            dao.note(note)
            record?.let { dao.record(it) }
            if (!private) {
                val payload = gson.toJsonTree(note).asJsonObject
                payload.add("records", JsonArray().apply { record?.let { add(recordJson(it)) } })
                queueOperation(OutboxOperation(entityId = note.id, noteId = note.id, path = "notes", payload = payload.toString()))
            }
        }
        runCatching { requestSync() }
        return note
    }

    suspend fun edit(record: Record) {
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

    suspend fun complete(record: Record, undo: Boolean = false) = database.withTransaction {
        val current = dao.recordById(record.id) ?: return@withTransaction
        if ((undo && current.state == "pending") || (!undo && current.state == "completed")) return@withTransaction
        edit(completeTask(current, undo))
    }

    suspend fun selectedIdea(date: LocalDate): Record? = database.withTransaction {
        val day = date.toString()
        val slot = dao.ideaSlot(day) ?: dao.ideaCandidate(day)?.let { IdeaSlot(day, it.id).also { chosen -> dao.slot(chosen) } } ?: return@withTransaction null
        slot.recordId?.let { dao.recordById(it) }?.takeIf { it.baseKind == "idea" && it.state == "pending" && (it.deferredUntil == null || it.deferredUntil <= day) }
    }
    suspend fun later(record: Record, date: LocalDate) = edit(record.copy(deferredUntil = date.plusDays(1).toString()))

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

    suspend fun snoozeTask(record: Record, date: LocalDate) {
        dao.meta(Meta("task-snooze:${record.id}", date.plusDays(7).toString()))
    }

    /** New raw capture + new confirmed record; the originating idea/task is never rewritten. */
    suspend fun linkedTask(origin: Record, title: String = origin.title, plannedFor: String? = null, private: Boolean = true): Record = database.withTransaction {
        require(title.isNotBlank() && title.length <= 1000) { "Enter an action of up to 1,000 characters." }
        val current = dao.recordById(origin.id) ?: error("The original item is no longer available.")
        require(current.state == "pending" && current.baseKind in setOf("idea", "task")) { "Restore this item before creating an action." }
        val note = dao.noteById(current.sourceNoteId) ?: error("The original note is unavailable.")
        val (newNote, planned) = CaptureNote.plan(title, private || note.privacyMode == "LOCAL_ONLY", "APP", "task", plannedFor)
        val task = planned!!.copy(fieldsJson = JsonObject().apply { addProperty(if (current.baseKind == "idea") "developedFrom" else "splitFrom", current.id) }.toString())
        dao.note(newNote); dao.record(task)
        if (newNote.privacyMode != "LOCAL_ONLY") {
            val payload = gson.toJsonTree(newNote).asJsonObject.apply { add("records", JsonArray().apply { add(recordJson(task)) }) }
            queueOperation(OutboxOperation(entityId = newNote.id, noteId = newNote.id, path = "notes", payload = payload.toString()))
        }
        runCatching { requestSync() }
        task
    }

    suspend fun manualInterpretation(note: RawNote, kind: String, title: String, plannedFor: String?) {
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

    suspend fun reprocess(note: RawNote) {
        require(note.privacyMode != "LOCAL_ONLY") { "This note is private. Organize it manually." }
        database.withTransaction {
            dao.noteStatus(note.id, "pending")
            queueOperation(OutboxOperation(entityId = note.id, noteId = note.id, path = "notes/${note.id}/reprocess", payload = "{}"))
        }
        runCatching { requestSync() }
    }

    suspend fun clear() = database.withTransaction {
        dao.clearOutbox(); dao.clearRecords(); dao.clearNotes(); dao.clearHistories(); dao.clearCorrections(); dao.clearSlots(); dao.clearTypes(); dao.clearRules(); dao.clearMeta()
    }
}
