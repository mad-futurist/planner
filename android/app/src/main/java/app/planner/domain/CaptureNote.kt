package app.planner.domain

import app.planner.core.isoInstant
import app.planner.core.model.RawNote
import app.planner.core.model.Record
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

data class CaptureContext(val instant: Instant = Instant.now(), val timezone: ZoneId = ZoneId.systemDefault(), val locale: String = Locale.getDefault().toLanguageTag())
data class CapturePlan(val note: RawNote, val record: Record?)

/** Pure capture policy; persistence and scheduling are applied by the repository. */
object CaptureNote {
    fun plan(text: String, private: Boolean, source: String, manualKind: String?, plannedFor: String?, context: CaptureContext = CaptureContext()): CapturePlan {
        require(text.isNotBlank() && text.length <= 20_000) { "Write a note of up to 20,000 characters." }
        require(source in setOf("APP", "OVERLAY"))
        require(manualKind == null || manualKind in setOf("task", "event", "idea", "information"))
        val note = RawNote(content = text, createdAt = isoInstant(context.instant), privacyMode = if (private) "LOCAL_ONLY" else "CLOUD_ALLOWED", source = source, processingStatus = if (private || manualKind != null) "local" else "pending", syncStatus = if (private) "local" else "pending", locale = context.locale, timezone = context.timezone.id, manual = manualKind != null)
        val record = manualKind?.let { Record(sourceNoteId = note.id, baseKind = it, title = text.lineSequence().first { line -> line.isNotBlank() }.trim().take(1000), plannedFor = if (it == "task") plannedFor else null, timezone = note.timezone, createdAt = note.createdAt, isUserConfirmed = true) }
        return CapturePlan(note, record)
    }
}

fun validateRecord(record: Record) {
    require(record.title.isNotBlank() && record.title.length <= 1000) { "Enter a title of up to 1,000 characters." }
    require(record.baseKind in setOf("task", "event", "idea", "information"))
    require(record.state in setOf("pending", "completed", "archived"))
    require(record.endAt == null || record.startAt != null) { "Choose a start before setting the end." }
    require(record.startAt == null || record.endAt == null || Instant.parse(record.endAt) > Instant.parse(record.startAt)) { "The end must be after the start." }
    require(record.dueAt == null || record.dueDate == null) { "Choose a deadline day or time." }
    require(record.state != "completed" || (record.baseKind == "task" && record.completedAt != null))
}

fun completeTask(record: Record, undo: Boolean, now: Instant = Instant.now()): Record {
    require(record.baseKind == "task") { "Only tasks can be completed." }
    return record.copy(state = if (undo) "pending" else "completed", completedAt = if (undo) null else isoInstant(now))
}
