package app.planner

import androidx.lifecycle.ViewModelStore
import app.planner.application.port.OrganizerCommands
import app.planner.application.port.OrganizerQueries
import app.planner.domain.model.CollectionCount
import app.planner.domain.model.InterpretationHistory
import app.planner.domain.model.OutboxOperation
import app.planner.domain.model.ProfileSummary
import app.planner.domain.model.RawNote
import app.planner.domain.model.Record
import app.planner.domain.model.TypeDefinition
import app.planner.presentation.common.UiFeedback
import app.planner.presentation.interpretation.InterpretationViewModel
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InterpretationViewModelTest {
    private class Queries(var saved: RawNote) : OrganizerQueries {
        override suspend fun note(id: String) = saved
        override suspend fun history(noteId: String): InterpretationHistory? = null
        override suspend fun record(id: String): Record? = null
        override suspend fun selectedIdea(date: LocalDate): Record? = null
        override fun journal(page: Int): Flow<List<RawNote>> = flowOf(listOf(saved))
        override fun day(date: LocalDate): Flow<List<Record>> = flowOf(emptyList())
        override fun dayCount(date: LocalDate) = flowOf(0)
        override fun completed(date: LocalDate): Flow<List<Record>> = flowOf(emptyList())
        override fun ideas(query: String, page: Int): Flow<List<Record>> = flowOf(emptyList())
        override fun tasks(filter: String, date: LocalDate, page: Int): Flow<List<Record>> = flowOf(emptyList())
        override fun taskCounts(date: LocalDate): Flow<Map<String, Int>> = flowOf(emptyMap())
        override fun profile() = flowOf(ProfileSummary())
        override fun collections(): Flow<List<CollectionCount>> = flowOf(emptyList())
        override fun review(date: LocalDate): Flow<List<Record>> = flowOf(emptyList())
        override fun triage(): Flow<List<RawNote>> = flowOf(emptyList())
        override fun types(): Flow<List<TypeDefinition>> = flowOf(emptyList())
        override fun metadata(): Flow<Map<String, String>> = flowOf(emptyMap())
        override fun outbox(): Flow<List<OutboxOperation>> = flowOf(emptyList())
        override fun processing(): Flow<Map<String, Int>> = flowOf(emptyMap())
        override fun records(noteId: String): Flow<List<Record>> = flowOf(emptyList())
        override fun job(recordId: String): Flow<String?> = flowOf(null)
    }
    private class Commands : OrganizerCommands {
        var interpretations = 0
        var fail = false
        val gate = CompletableDeferred<Unit>()
        override suspend fun manualInterpretation(note: RawNote, kind: String, title: String, plannedFor: String?) {
            interpretations++
            if (fail) throw IllegalArgumentException("Synthetic save failure")
            gate.await()
        }
        override suspend fun edit(record: Record) = Unit
        override suspend fun complete(record: Record, undo: Boolean) = Unit
        override suspend fun later(record: Record, date: LocalDate) = Unit
        override suspend fun snoozeTask(record: Record, date: LocalDate) = Unit
        override suspend fun linkedTask(origin: Record, title: String, plannedFor: String?, private: Boolean): Record = error("Unused")
        override suspend fun reprocess(note: RawNote) = Unit
        override suspend fun postponeReview(noteId: String, date: LocalDate) = Unit
        override suspend fun captureDuration(milliseconds: Long) = Unit
    }
    @Test fun repeatedSaveCreatesOneInterpretationAndPublishesBusyUntilCommit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val note = RawNote(content = "maybe find ai course")
            val commands = Commands()
            val vm = InterpretationViewModel(Queries(note), commands, UiFeedback())
            store.put("interpretation", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
            vm.open(note.id); runCurrent()
            vm.save("idea", note.content, null); vm.save("idea", note.content, null); runCurrent()
            assertEquals(1, commands.interpretations)
            assertTrue(vm.uiState.value.saving)
            assertEquals(0, vm.uiState.value.closeVersion)
            commands.gate.complete(Unit); runCurrent()
            assertFalse(vm.uiState.value.saving)
            assertEquals(1, vm.uiState.value.closeVersion)
            vm.save("idea", note.content, null); runCurrent()
            assertEquals(1, commands.interpretations)
        } finally { backgroundScope.cancel(); store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun failedSaveRetainsTheNoteAndAllowsAnExplicitRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val note = RawNote(content = "Synthetic note")
            val commands = Commands().apply { fail = true }
            val feedback = UiFeedback()
            val vm = InterpretationViewModel(Queries(note), commands, feedback)
            store.put("interpretation", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
            vm.open(note.id); runCurrent(); vm.save("task", note.content, null); runCurrent()
            assertEquals(note.originalContent, vm.uiState.value.note?.originalContent)
            assertEquals(0, vm.uiState.value.closeVersion)
            assertEquals("Synthetic save failure", vm.uiState.value.error)
            commands.fail = false; commands.gate.complete(Unit)
            vm.save("task", note.content, null); runCurrent()
            assertEquals(2, commands.interpretations)
            assertEquals(1, vm.uiState.value.closeVersion)
        } finally { backgroundScope.cancel(); store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun finishingAnOlderSaveDoesNotCloseANewlyOpenedNote() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val first = RawNote(content = "First synthetic note")
            val second = RawNote(content = "Second synthetic note")
            val queries = Queries(first)
            val commands = Commands()
            val vm = InterpretationViewModel(queries, commands, UiFeedback())
            store.put("interpretation", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
            vm.open(first.id); runCurrent(); vm.save("task", first.content, null); runCurrent()
            queries.saved = second
            vm.open(second.id); runCurrent()
            commands.gate.complete(Unit); runCurrent()
            assertEquals(second.id, vm.uiState.value.note?.id)
            assertEquals(0, vm.uiState.value.closeVersion)
        } finally { backgroundScope.cancel(); store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
