package app.planner.application.port

import app.planner.domain.model.RawNote
import app.planner.domain.model.Record
import java.time.LocalDate

interface OrganizerCommands {
    suspend fun edit(record: Record)
    suspend fun complete(record: Record, undo: Boolean = false)
    suspend fun later(record: Record, date: LocalDate)
    suspend fun snoozeTask(record: Record, date: LocalDate)
    suspend fun linkedTask(origin: Record, title: String = origin.title, plannedFor: String? = null, private: Boolean = true): Record
    suspend fun manualInterpretation(note: RawNote, kind: String, title: String, plannedFor: String?)
    suspend fun reprocess(note: RawNote)
    suspend fun postponeReview(noteId: String, date: LocalDate)
    suspend fun captureDuration(milliseconds: Long)
}
