package app.planner.application

import app.planner.domain.*
import app.planner.domain.model.*
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

fun interface SyncRequest {
    fun request()
}

/** Bounded local projections. Room remains authoritative; no transport objects escape this port. */
interface OrganizerQueries {
    fun journal(page: Int): Flow<List<RawNote>>
    fun day(date: LocalDate): Flow<List<Record>>
    fun dayCount(date: LocalDate): Flow<Int>
    fun completed(date: LocalDate): Flow<List<Record>>
    fun ideas(query: String = "", page: Int = 0): Flow<List<Record>>
    fun tasks(filter: String, date: LocalDate, page: Int): Flow<List<Record>>
    fun taskCounts(date: LocalDate): Flow<Map<String, Int>>
    fun profile(): Flow<ProfileSummary>
    fun collections(): Flow<List<CollectionCount>>
    fun review(date: LocalDate): Flow<List<Record>>
    fun triage(): Flow<List<RawNote>>
    fun types(): Flow<List<TypeDefinition>>
    fun metadata(): Flow<Map<String, String>>
    fun outbox(): Flow<List<OutboxOperation>>
    fun processing(): Flow<Map<String, Int>>
    fun records(noteId: String): Flow<List<Record>>
    fun job(recordId: String): Flow<String?>
    suspend fun record(id: String): Record?
    suspend fun note(id: String): RawNote?
    suspend fun history(noteId: String): InterpretationHistory?
    suspend fun selectedIdea(date: LocalDate): Record?
}

interface CaptureStore {
    /** Atomically persists original, records, history and eligible outbox entries. */
    suspend fun saveCapture(plan: CapturePlan): RawNote
    suspend fun saveLocal(plan: LocalCapturePlan, preview: CapturePreview): LocalCapturePlan
}

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

interface UserPreferences {
    val values: Flow<Map<String, String>>
    suspend fun set(key: String, value: String)
    suspend fun capturePrivacy(private: Boolean, informedConsent: Boolean = false)
    suspend fun captureDraft(text: String, hints: String)
}

interface InterpretationCodec {
    fun encode(hints: InterpretationHints): String
    fun decode(value: String): InterpretationHints?
}
