package app.planner.domain.interpretation

import app.planner.domain.policy.CaptureContext
import app.planner.domain.time.isoInstant
import java.time.*

data class KindRule(val kind: String, val pattern: String)

data class RelativeDayRule(val pattern: String, val offset: Long)

data class CaptureRules(val version: Int, val kinds: List<KindRule>, val split: String, val clock: String, val hour: String, val ampm: String, val isoDate: String, val shortDate: String, val bareNumber: String, val uncertainDate: String, val deadline: String, val address: String, val relativeDays: List<RelativeDayRule>, val weekdays: List<String>, val tentativeAction: String? = null)

data class CaptureHint(val start: Int, val end: Int, val text: String, val kind: String, val date: String? = null, val time: String? = null)

data class InterpretationHints(val schemaVersion: Int = 1, val clarifications: List<CaptureHint> = emptyList())

data class CaptureDoubt(val start: Int, val end: Int, val text: String, val reason: String)

data class PreviewRecord(val baseKind: String, val title: String, val start: Int, val end: Int, val date: String?, val time: String?, val startAt: String?, val dueDate: String?, val plannedFor: String?, val calendarEligible: Boolean, val kindChoices: List<String> = emptyList())

data class CapturePreview(val schemaVersion: Int = 1, val engine: String = "local-rules-v1", val records: List<PreviewRecord> = emptyList(), val doubts: List<CaptureDoubt> = emptyList())

/** Pure preview/safety policy. Shared versioned rules are loaded once by the data boundary. */
class CaptureInterpreter(private val rules: CaptureRules) {
    private val regexes = java.util.concurrent.ConcurrentHashMap<String, Regex>()
    private fun regex(pattern: String) = regexes.computeIfAbsent(pattern) { Regex(it, RegexOption.IGNORE_CASE) }

    fun preview(content: String, context: CaptureContext = CaptureContext(), hints: InterpretationHints = InterpretationHints(), forcedKind: String? = null): CapturePreview {
        require(content.length <= 20_000)
        require(hints.schemaVersion == 1 && hints.clarifications.size <= 24)
        val selectedHints = hints.clarifications.filter { hint ->
            hint.start >= 0 && hint.end <= content.length && hint.start < hint.end && content.substring(hint.start, hint.end) == hint.text
        }
        require(selectedHints.size == hints.clarifications.size) { "Clarifications no longer match this draft." }
        val reference = context.instant.atZone(context.timezone)
        val pieces = mutableListOf<Pair<Int, Int>>()
        var last = 0
        regex(rules.split).findAll(content).forEach { pieces += last to it.range.first; last = it.range.last + 1 }
        pieces += last to content.length
        val records = mutableListOf<PreviewRecord>()
        val doubts = mutableListOf<CaptureDoubt>()
        for ((begin, end) in pieces.take(12)) {
            val raw = content.substring(begin, end)
            val text = raw.trim()
            if (text.isBlank()) continue
            val offset = begin + raw.indexOfFirst { !it.isWhitespace() }
            val selected = selectedHints.filter { it.start >= offset && it.end <= offset + text.length }
            val kind = forcedKind ?: rules.kinds.firstOrNull { regex(it.pattern).containsMatchIn(text) }?.kind ?: "information"
            var day: LocalDate? = null
            var clock: LocalTime? = null
            val used = mutableListOf<IntRange>()
            val questions = mutableListOf<CaptureDoubt>()
            fun covered(range: IntRange) = selected.any { it.start <= offset + range.first && it.end >= offset + range.last + 1 }
            fun doubt(range: IntRange, reason: String) {
                questions += CaptureDoubt(offset + range.first, offset + range.last + 1, text.substring(range), reason)
            }
            val iso = regex(rules.isoDate).find(text)
            if (iso != null) {
                day = runCatching { LocalDate.parse(iso.value) }.getOrNull()
                if (day == null && !covered(iso.range)) doubt(iso.range, "invalid_date")
                used += iso.range
            } else {
                val relative = rules.relativeDays.firstOrNull { regex(it.pattern).containsMatchIn(text) }
                if (relative != null) day = reference.toLocalDate().plusDays(relative.offset)
                else {
                    val weekday = rules.weekdays.indexOfFirst { regex(it).containsMatchIn(text) }
                    if (weekday >= 0) {
                        if (regex(rules.uncertainDate).containsMatchIn(text)) {
                            if (!covered(text.indices)) doubt(text.indices, "ambiguous_weekday")
                        } else day = reference.toLocalDate().plusDays(((weekday + 1 - reference.dayOfWeek.value + 7) % 7).toLong())
                    }
                }
            }
            val ampm = regex(rules.ampm).find(text)
            val precise = ampm ?: regex(rules.clock).find(text)
            if (precise != null) {
                clock = runCatching {
                    var hour = precise.groupValues[1].toInt()
                    val minute = precise.groupValues[2].ifEmpty { "0" }.toInt()
                    if (ampm != null) hour = hour % 12 + if (precise.groupValues[3].equals("pm", true)) 12 else 0
                    LocalTime.of(hour, minute)
                }.getOrNull()
                if (clock == null && !covered(precise.range)) doubt(precise.range, "invalid_time")
                used += precise.range
            } else if (!regex(rules.address).containsMatchIn(text)) {
                regex(rules.hour).find(text)?.let { match ->
                    clock = runCatching { LocalTime.of(match.groupValues[1].toInt(), 0) }.getOrNull()
                    if (clock == null && !covered(match.range)) doubt(match.range, "invalid_time")
                    used += match.range
                }
            }
            if (iso == null) regex(rules.shortDate).find(text)?.let { match ->
                used += match.range
                if (!covered(match.range)) doubt(match.range, "ambiguous_date")
            }
            if (kind == "event") regex(rules.bareNumber).findAll(text).forEach { match ->
                if (used.none { match.range.first <= it.last && match.range.last >= it.first } && !covered(match.range)) doubt(match.range, "time_or_day")
            }
            selected.forEach { hint ->
                hint.date?.let { day = LocalDate.parse(it) }
                hint.time?.let { clock = LocalTime.parse(it) }
            }
            var startAt: String? = null
            if (day != null && clock != null) {
                val local = LocalDateTime.of(day, clock)
                val offsets = context.timezone.rules.getValidOffsets(local)
                if (offsets.size != 1) doubt(text.indices, "clock_change")
                else startAt = app.planner.domain.time.isoInstant(local.atOffset(offsets.single()).toInstant())
            }
            if (kind == "event" && (day == null || clock == null) && questions.isEmpty()) {
                doubt(text.indices, if (day == null && clock == null) "missing_date_time" else if (day == null) "missing_date" else "missing_time")
            }
            if (kind == "task" && clock != null && day == null && questions.isEmpty()) doubt(text.indices, "missing_date")
            val deadline = regex(rules.deadline).containsMatchIn(text)
            records += PreviewRecord(kind, text, offset, offset + text.length, day?.toString(), clock?.toString(), startAt.takeIf { questions.isEmpty() }, day?.toString()?.takeIf { kind == "task" && deadline && questions.isEmpty() }, day?.toString()?.takeIf { kind == "task" && !deadline && clock == null && questions.isEmpty() }, kind == "event" && startAt != null && questions.isEmpty())
            doubts += questions
            if (forcedKind == null && kind == "task" && day == null && clock == null && rules.tentativeAction?.let { regex(it).containsMatchIn(text) } == true) {
                records[records.lastIndex] = records.last().copy(kindChoices = listOf("task_deadline", "task_unscheduled", "idea"))
            }
        }
        return CapturePreview(records = records, doubts = doubts.take(24))
    }
}
