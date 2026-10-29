package app.planner.presentation.design.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.planner.domain.model.Record
import app.planner.presentation.design.format.recordDomain
import app.planner.presentation.design.format.taskDateLabel
import java.time.*

@Composable fun TaskCard(record: Record, complete: () -> Unit, open: () -> Unit, compact: Boolean=false) {
    val color=if(record.pinned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    PlannerCard(Modifier.padding(bottom=6.dp).testTag("task-${record.id}"),color=color,endColor=if(record.pinned) color.copy(alpha=.75f) else color,onClick=open) {
        Row(Modifier.fillMaxWidth().heightIn(min=if(compact) 56.dp else 66.dp).padding(start=2.dp,end=2.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(complete,Modifier.size(48.dp).semantics { contentDescription="${if(record.state=="completed") "Restore" else "Complete"} ${record.title}" }) {
                if(record.pinned && compact && record.state!="completed") PlannerIcon(Glyph.Star,Modifier.size(22.dp),Color(0xFFC08A0B),filled=true)
                else Box(Modifier.size(22.dp),contentAlignment=Alignment.Center) {
                    val tint=MaterialTheme.colorScheme.onSurface
                    Canvas(Modifier.fillMaxSize()) { drawCircle(tint,style=Stroke(1.2.dp.toPx())) }
                    if(record.state=="completed") PlannerIcon(Glyph.Check,Modifier.size(15.dp))
                }
            }
            Column(Modifier.weight(1f).padding(vertical=10.dp)) {
                Text(record.title,style=MaterialTheme.typography.bodyLarge,fontWeight=if(compact) FontWeight.SemiBold else FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(!compact) {
                    val domain=recordDomain(record)
                    Text(if(domain.isNotBlank()) domain else taskDateLabel(record),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if(!compact && record.plannedFor!=null && recordDomain(record).isNotBlank()) Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=CircleShape) { Text(taskDateLabel(record),Modifier.padding(horizontal=9.dp,vertical=7.dp),style=MaterialTheme.typography.labelSmall) }
            IconButton(open,Modifier.size(48.dp).semantics { contentDescription="Details ${record.title}" }) { PlannerIcon(Glyph.More,Modifier.size(17.dp),if(record.pinned) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
