package app.planner.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

enum class CaptureKind(val recordKind: String?) {
    NOTE(null), TASK("task"), EVENT("event"), DEADLINE("task"), IDEA("idea"), INFORMATION("information"),
}

data class CaptureInterpretation(val kind: CaptureKind, val day: LocalDate? = null, val time: LocalTime? = null)

enum class TokenMeaning { ENTITY, TIME, DATE, DEADLINE, IDEA, LINK }
data class DraftToken(val range: IntRange, val meaning: TokenMeaning)
data class DraftReading(
    val kind: CaptureKind,
    val day: LocalDate?,
    val time: LocalTime?,
    val ambiguousHour: Int?,
    val tokens: List<DraftToken>,
)

private val idea = Regex("""^\s*(idea|idée|идея)\s*:""", RegexOption.IGNORE_CASE)
private val event = Regex("""(?<![\p{L}\p{N}])(appointment|meeting|meet|dentist|réunion|dentiste|rendez-vous|стоматолог\p{L}*|созвон\p{L}*|встреч\p{L}*)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
private val task = Regex("""^\s*(buy|finish|prepare|send|submit|call|acheter|envoyer|купить|подготовить|отправить|сделать)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
private val url = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
private val dayWords = Regex("""(?<![\p{L}\p{N}])(today|tomorrow|aujourd'hui|demain|сегодня|завтра)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
private val clock = Regex("""(?<![\d:])([01]?\d|2[0-3]):([0-5]\d)(?![\d:])""")
private val bareHour = Regex("""(?<![\p{L}\p{N}])(?:at|à|в)\s+(1[0-2]|[1-9])(?![\d:])(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
private val fridayDeadline = Regex("""(?<![\p{L}\p{N}])(?:by\s+friday|avant\s+vendredi|до\s+пятницы)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

/** Bounded, local suggestions. The caller decides which interpretation to commit. */
fun readDraft(text: String, today: LocalDate): DraftReading {
    val ideaMatch = idea.find(text)
    val link = url.find(text)
    val prose = if (link == null) text else url.replace(text) { " ".repeat(it.value.length) }
    val eventMatch = event.find(prose)
    val deadline = fridayDeadline.findAll(prose).singleOrNull()
    val day = dayWords.findAll(prose).singleOrNull()
    val exactTimes = clock.findAll(prose).take(2).toList()
    val exact = exactTimes.singleOrNull()
    val hour = if (exactTimes.isEmpty()) bareHour.findAll(prose).singleOrNull() else null
    val kind = when {
        ideaMatch != null -> CaptureKind.IDEA
        deadline != null -> CaptureKind.DEADLINE
        eventMatch != null -> CaptureKind.EVENT
        task.containsMatchIn(text) -> CaptureKind.TASK
        link != null -> CaptureKind.INFORMATION
        else -> CaptureKind.NOTE
    }
    val datedKind = kind in setOf(CaptureKind.EVENT, CaptureKind.TASK, CaptureKind.DEADLINE)
    val tokens = buildList {
        ideaMatch?.let { add(DraftToken(it.range, TokenMeaning.IDEA)) }
        if (kind == CaptureKind.EVENT) eventMatch?.let { add(DraftToken(it.range, TokenMeaning.ENTITY)) }
        if (datedKind) {
            deadline?.let { add(DraftToken(it.range, TokenMeaning.DEADLINE)) }
            day?.let { add(DraftToken(it.range, TokenMeaning.DATE)) }
            (exact ?: hour)?.let { add(DraftToken(it.range, TokenMeaning.TIME)) }
        }
        link?.let { add(DraftToken(it.range, TokenMeaning.LINK)) }
    }.sortedBy { it.range.first }
    val resolvedDay = when {
        !datedKind -> null
        deadline != null -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        day == null -> null
        day.value.lowercase() in setOf("tomorrow", "demain", "завтра") -> today.plusDays(1)
        else -> today
    }
    val resolvedTime = exact?.takeIf { datedKind }?.let { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
    return DraftReading(kind, resolvedDay, resolvedTime, hour?.takeIf { datedKind }?.groupValues?.get(1)?.toInt(), tokens)
}
