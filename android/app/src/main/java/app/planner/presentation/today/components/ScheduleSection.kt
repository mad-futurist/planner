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
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.components.ScheduleCard
import app.planner.presentation.design.components.SectionHeader
import java.time.*

import app.planner.presentation.today.*

internal fun LazyListScope.scheduleSection(records: List<Record>, date: LocalDate, reads: Map<String,String>, retry: () -> Unit, edit: (String) -> Unit) {
    val scheduled=records.filter {isScheduledOnDay(it,date,ZoneId.systemDefault())}.sortedBy {it.startAt}
    item {SectionHeader("Schedule",Glyph.Calendar)}
    if(scheduled.isEmpty()) item {LocalQueryHint(reads,"today","No timed commitments today.",retry)}
    items(scheduled,key={"schedule:${it.id}"}) {ScheduleCard(it,date) {edit(it.id)}}

}
