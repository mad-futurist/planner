package app.planner.presentation.design.format

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import app.planner.domain.model.Record
import com.google.gson.JsonParser
import java.time.*
import java.time.format.DateTimeFormatter

fun recordDetails(record: Record): String = runCatching {
    JsonParser.parseString(record.fieldsJson).asJsonObject.entrySet().filter { it.value.isJsonPrimitive && it.key !in setOf("developedFrom","splitFrom","suggestionJobId","domain","category") }.take(2).joinToString(" · ") { it.value.asString }
}.getOrDefault("")

fun recordDomain(record: Record): String = runCatching {
    val fields=JsonParser.parseString(record.fieldsJson).asJsonObject
    (fields.get("domain") ?: fields.get("category"))?.asString.orEmpty()
}.getOrDefault("")

fun recordClock(value: String?): String = value?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) }.orEmpty()

fun taskDateLabel(record: Record, today: LocalDate=LocalDate.now()): String {
    fun dayLabel(day:String):String = when(day) { today.toString()->"Today"; today.plusDays(1).toString()->"Tomorrow"; else->LocalDate.parse(day).format(DateTimeFormatter.ofPattern("MMM d")) }
    return when {
        record.plannedFor!=null->dayLabel(record.plannedFor)
        record.dueDate!=null->"Due ${dayLabel(record.dueDate)}"
        record.startAt!=null->recordClock(record.startAt)
        record.dueAt!=null->"Due ${Instant.parse(record.dueAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d · HH:mm"))}"
        else->"No date"
    }
}
