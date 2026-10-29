package app.planner

import app.planner.domain.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CaptureInterpreterTest {
    private val gson = Gson()
    private val assets = File("../../backend/app/domain/assets")
    private val interpreter = CaptureInterpreter(gson.fromJson(File(assets, "capture_rules_v1.json").readText(), CaptureRules::class.java))
    @Test fun sharedNoncalendarCorpusPreservesUserChoiceAndDates() {
        val corpus = JsonParser.parseString(File(assets, "noncalendar_choices_v1.json").readText()).asJsonObject
        val context = CaptureContext(Instant.parse(corpus["createdAt"].asString), ZoneId.of(corpus["timezone"].asString))
        for (entry in corpus.getAsJsonArray("cases")) {
            val case = entry.asJsonObject; val id = case["id"].asString
            val result = interpreter.preview(case["text"].asString, context)
            assertEquals(id, case.getAsJsonArray("kinds").map { it.asString }, result.records.map { it.baseKind })
            assertEquals(id, case.getAsJsonArray("kindChoices").map { row -> row.asJsonArray.map { it.asString } }, result.records.map { it.kindChoices })
            assertEquals(id, case.getAsJsonArray("calendar").map { it.asBoolean }, result.records.map { it.calendarEligible })
            assertEquals(id, case.getAsJsonArray("doubts").map { it.asString }.toSet(), result.doubts.map { it.reason }.toSet())
            case["plannedFor"]?.let { assertEquals(id, it.asString, result.records.first().plannedFor) }
        }
        assertTrue(interpreter.preview("maybe find ai course", context, forcedKind = "task").records.single().kindChoices.isEmpty())
    }
    @Test fun sharedMultilingualBenchmarkMatchesExpectedEntitiesDatesAndDoubts() {
        val corpus = JsonParser.parseString(File(assets, "interpretation_benchmark_v1.json").readText()).asJsonObject
        val context = CaptureContext(Instant.parse(corpus["createdAt"].asString), ZoneId.of(corpus["timezone"].asString))
        for (entry in corpus.getAsJsonArray("cases")) {
            val case = entry.asJsonObject
            val hints = case["hints"]?.let { gson.fromJson(it, InterpretationHints::class.java) } ?: InterpretationHints()
            val result = interpreter.preview(case["text"].asString, context, hints)
            val id = case["id"].asString
            assertEquals(id, case.getAsJsonArray("kinds").map { it.asString }, result.records.map { it.baseKind })
            assertEquals(id, case.getAsJsonArray("calendar").map { it.asBoolean }, result.records.map { it.calendarEligible })
            assertEquals(id, case.getAsJsonArray("doubts").map { it.asString }.toSet(), result.doubts.map { it.reason }.toSet())
            case["startAt"]?.let { assertEquals(id, it.asString, result.records.first().startAt) }
            case["dueDate"]?.let { assertEquals(id, it.asString, result.records.first().dueDate) }
            case["plannedFor"]?.let { assertEquals(id, it.asString, result.records.first().plannedFor) }
        }
    }
}
