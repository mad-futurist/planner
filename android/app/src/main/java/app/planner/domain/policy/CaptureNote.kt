package app.planner.domain.policy

import app.planner.domain.interpretation.CapturePreview
import app.planner.domain.model.RawNote
import app.planner.domain.model.Record
import app.planner.domain.time.isoInstant
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

data class CaptureContext(val instant: Instant = Instant.now(), val timezone: ZoneId = ZoneId.systemDefault(), val locale: String = Locale.getDefault().toLanguageTag())

data class CapturePlan(val note: RawNote, val record: Record?)

data class LocalCapturePlan(val note: RawNote, val records: List<Record>)

/** Pure capture policy; persistence and scheduling are applied by the repository. */
object CaptureNote {
    /** Apply only a complete local proposal; uncertainty must be resolved before this command. */
    fun planLocal(text: String, preview: CapturePreview, context: CaptureContext, interpretationHintsJson: String = "{}"): LocalCapturePlan {
        require(preview.schemaVersion == 1 && preview.engine == "local-rules-v1")
        require(preview.doubts.isEmpty() && preview.records.isNotEmpty() && preview.records.size <= 12) { "Resolve the remaining questions or save the original note only." }
        require(preview.records.all { it.kindChoices.orEmpty().isEmpty() }) { "Choose a task, Someday or an idea before saving." }
        val note = plan(text, true, "APP", null, null, context, interpretationHintsJson).note
        val records = preview.records.map { proposal ->
            require(proposal.start >= 0 && proposal.end <= text.length && proposal.start < proposal.end)
            require(text.substring(proposal.start, proposal.end) == proposal.title)
            require(proposal.baseKind != "event" || proposal.calendarEligible && proposal.startAt != null) { "An event needs a confirmed day and time." }
            val deadline = proposal.startAt.takeIf { proposal.baseKind == "task" && proposal.dueDate != null }
            Record(sourceNoteId = note.id, baseKind = proposal.baseKind, title = proposal.title.take(1000),
                startAt = proposal.startAt.takeIf { deadline == null && proposal.baseKind in setOf("task", "event") },
                dueAt = deadline, dueDate = proposal.dueDate.takeIf { proposal.baseKind == "task" && deadline == null },
                plannedFor = proposal.plannedFor.takeIf { proposal.baseKind == "task" },
                timezone = context.timezone.id, createdAt = note.createdAt).also(::validateRecord)
        }
        return LocalCapturePlan(note, records)
    }

    fun plan(text: String, private: Boolean, source: String, manualKind: String?, plannedFor: String?, context: CaptureContext = CaptureContext(), interpretationHintsJson: String = "{}", eventStartAt: String? = null, rawOnly: Boolean = false, dueDate: String? = null): CapturePlan {
        require(text.isNotBlank() && text.length <= 20_000) { "Write a note of up to 20,000 characters." }
        require(source in setOf("APP", "OVERLAY"))
        require(manualKind == null || manualKind in setOf("task", "event", "idea", "information"))
        require(!rawOnly || manualKind == null)
        require(dueDate == null || manualKind == "task")
        dueDate?.let(java.time.LocalDate::parse)
        val manual = rawOnly || manualKind != null
        val note = RawNote(content = text, createdAt = isoInstant(context.instant), privacyMode = if (private) "LOCAL_ONLY" else "CLOUD_ALLOWED", source = source, processingStatus = if (private || manual) "local" else "pending", syncStatus = if (private) "local" else "pending", locale = context.locale, timezone = context.timezone.id, manual = manual)
        val record = manualKind?.let { Record(sourceNoteId = note.id, baseKind = it, title = text.lineSequence().first { line -> line.isNotBlank() }.trim().take(1000), plannedFor = if (it == "task") plannedFor else null, timezone = note.timezone, createdAt = note.createdAt, isUserConfirmed = true) }
        return CapturePlan(note.copy(interpretationHintsJson = interpretationHintsJson), record?.copy(startAt = eventStartAt, dueDate = dueDate))
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
