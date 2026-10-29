package app.planner

import app.planner.application.*
import app.planner.domain.*
import app.planner.domain.model.*
import app.planner.features.UiFeedback
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ApplicationUseCaseTest {
    private class Captures : CaptureStore {
        var raw: CapturePlan? = null
        var local: LocalCapturePlan? = null
        override suspend fun saveCapture(plan: CapturePlan): RawNote { raw = plan; return plan.note }
        override suspend fun saveLocal(plan: LocalCapturePlan, preview: CapturePreview): LocalCapturePlan { local = plan; return plan }
    }

    @Test fun overlayAndComposerUseTheSameRawCapturePolicy() = runTest {
        val store = Captures()
        val capture = CaptureUseCase(store)
        val note = capture.capture("dentist 16", true, source = "OVERLAY")
        assertEquals("dentist 16", note.originalContent)
        assertEquals("OVERLAY", note.source)
        assertEquals("LOCAL_ONLY", note.privacyMode)
        assertNull(store.raw?.record)
        assertNull(store.local)
    }

    @Test fun incompleteEventCannotReachThePersistencePort() = runTest {
        val store = Captures()
        val preview = CapturePreview(records = listOf(PreviewRecord(baseKind = "event", title = "dentist 16", start = 0, end = 10, date = null, time = null, startAt = null, dueDate = null, plannedFor = null, calendarEligible = false)), doubts = listOf(CaptureDoubt(8, 10, "16", "time_or_day")))
        try { CaptureUseCase(store).local("dentist 16", preview, CaptureContext(), "{}"); fail("Unresolved event was accepted") }
        catch (_: IllegalArgumentException) { }
        assertNull(store.raw)
        assertNull(store.local)
    }

    @Test fun staleDevelopmentCannotCreateTasks() = runTest {
        val idea = Record(sourceNoteId = "note", title = "Course", baseKind = "idea", version = 3)
        var created = 0
        val port = object : DevelopmentApprovalPort {
            override suspend fun transaction(recordId: String, jobId: String, apply: suspend (DevelopmentSnapshot, DevelopmentWriter) -> Unit) {
                apply(DevelopmentSnapshot(idea, false, "job", "complete", 2, listOf("Find a course"), false), object : DevelopmentWriter {
                    override suspend fun create(step: String, originId: String, jobId: String) { created++ }
                    override suspend fun defer(record: Record) { fail("Stale idea was deferred") }
                    override suspend fun markApplied(jobId: String) { fail("Stale result was marked applied") }
                })
            }
        }
        try { ApproveDevelopment(port).approve(idea.id, "job", listOf("Find a course")); fail("Stale result was accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, created)
    }

    @Test fun dismissingAnOlderSnackbarPreservesNewerUndo() {
        val feedback = UiFeedback()
        feedback.show("Marked done", "first")
        val old = feedback.state.value!!
        feedback.show("Marked done", "second")
        feedback.dismiss(old.id)
        assertEquals("second", feedback.state.value?.undoRecordId)
        feedback.dismiss(feedback.state.value!!.id)
        assertNull(feedback.state.value)
    }
}
