package app.planner.presentation.today.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import app.planner.domain.model.Record
import app.planner.domain.policy.isScheduledOnDay
import app.planner.presentation.common.LocalQueryHint
import app.planner.presentation.common.TaskRow
import app.planner.presentation.design.components.ExpandRow
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.components.SectionHeader
import java.time.*

import app.planner.presentation.today.*

internal fun LazyListScope.anytimeSection(records: List<Record>, date: LocalDate, reads: Map<String,String>, retry: () -> Unit, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, complete: (Record) -> Unit, edit: (String) -> Unit, addTask: () -> Unit) {
    val anytime=records.filter {it.baseKind=="task" && !isScheduledOnDay(it,date,ZoneId.systemDefault())}.sortedWith(compareByDescending<Record> {it.pinned}.thenBy {it.createdAt})
    item {SectionHeader("Anytime",Glyph.Sun,warm=true)}
    if(anytime.isEmpty()) item {LocalQueryHint(reads,"today","Choose a small focus for today.",retry);TextButton(addTask,Modifier.padding(start=42.dp)) {Text("+ Add a task")}}
    items(if(expanded) anytime else anytime.take(2),key={"task:${it.id}"}) {Box(Modifier.padding(start=44.dp)) {TaskRow(it,complete,compact=true) {edit(it.id)}}}
    if(anytime.size>2) item {Box(Modifier.padding(start=44.dp)) {ExpandRow(if(expanded) "Show less" else "+${anytime.size-2} more",Modifier.testTag("anytime-more"),expanded) {onExpandedChange(!expanded)}}}

}
