package app.planner.presentation.design.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.planner.domain.model.Record
import app.planner.presentation.design.format.recordClock
import app.planner.presentation.design.format.recordDetails
import java.time.*

@Composable fun ScheduleCard(record: Record, date: LocalDate, open: () -> Unit) {
    val ongoing=record.startAt?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate()<date }==true
    val line=MaterialTheme.colorScheme.primaryContainer
    Row(Modifier.fillMaxWidth().testTag("schedule-${record.id}").padding(start=17.dp,bottom=6.dp).drawBehind { drawLine(line,androidx.compose.ui.geometry.Offset(0f,0f),androidx.compose.ui.geometry.Offset(0f,size.height+8.dp.toPx()),1.dp.toPx()) },verticalAlignment=Alignment.CenterVertically) {
        Text(if(ongoing) "Ongoing" else recordClock(record.startAt),Modifier.width(66.dp).padding(start=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        PlannerCard(Modifier.weight(1f),onClick=open) {
            Row(Modifier.fillMaxWidth().heightIn(min=66.dp),verticalAlignment=Alignment.CenterVertically) {
                Spacer(Modifier.width(5.dp).height(66.dp).background(MaterialTheme.colorScheme.primaryContainer))
                Column(Modifier.weight(1f).padding(horizontal=12.dp,vertical=12.dp)) {
                    Text(record.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    val details=recordDetails(record)
                    if(details.isNotBlank()) Text(details,Modifier.padding(top=4.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
                Box(Modifier.padding(end=12.dp).size(34.dp).background(MaterialTheme.colorScheme.primaryContainer,CircleShape),contentAlignment=Alignment.Center) { PlannerIcon(if(record.title.contains("call",true)) Glyph.Phone else Glyph.Calendar,Modifier.size(19.dp)) }
            }
        }
    }
}
