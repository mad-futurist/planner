package app.planner

import app.planner.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SemanticDraftTest {
    private val today = LocalDate.of(2026, 11, 19)

    @Test fun recognizesCyrillicAndLeavesAmbiguousTimeUnresolved() {
        val raw = "Записаться к стоматологу в 6"
        val draft = readDraft(raw, today)
        assertEquals(CaptureKind.EVENT, draft.kind)
        assertEquals(6, draft.ambiguousHour)
        assertNull(draft.day)
        assertNull(draft.time)
        assertEquals(listOf("стоматологу", "в 6"), draft.tokens.map { raw.substring(it.range) })
        val explicit = readDraft("Завтра в 15:00 созвон", today)
        assertEquals(today.plusDays(1), explicit.day)
        assertEquals(LocalTime.of(15, 0), explicit.time)
    }

    @Test fun deadlineDoesNotCreateAWorkingSessionAndPreservesOriginal() {
        val raw = "  Доклад до пятницы\n"
        val draft = readDraft(raw, today)
        val capture = CaptureNote.plan(raw, true, "APP", null, null,
            CaptureContext(Instant.parse("2026-11-19T12:00:00Z"), ZoneId.of("Europe/Paris")),
            CaptureInterpretation(draft.kind, draft.day, draft.time))
        assertEquals(raw, capture.note.originalContent)
        val record = capture.record!!
        assertEquals("task", record.baseKind)
        assertEquals("2026-11-20", record.dueDate)
        assertNull(record.startAt)
        assertNull(record.plannedFor)
        assertTrue(record.isUserConfirmed)
    }
}
