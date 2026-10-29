package app.planner.application.port

import app.planner.domain.model.CollectionCount
import app.planner.domain.model.InterpretationHistory
import app.planner.domain.model.OutboxOperation
import app.planner.domain.model.ProfileSummary
import app.planner.domain.model.RawNote
import app.planner.domain.model.Record
import app.planner.domain.model.TypeDefinition
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

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
