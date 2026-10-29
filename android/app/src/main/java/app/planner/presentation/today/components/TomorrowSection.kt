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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.planner.domain.model.Record
import app.planner.domain.policy.isScheduledOnDay
import app.planner.presentation.design.components.CircleAction
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.components.PlannerCard
import app.planner.presentation.design.illustrations.Landscape
import app.planner.presentation.design.format.recordClock
import java.time.*

import app.planner.presentation.today.*

internal fun LazyListScope.tomorrowSection(tomorrow: List<Record>, date: LocalDate, openTomorrow: () -> Unit) {
    val next=tomorrow.filter {isScheduledOnDay(it,date.plusDays(1),ZoneId.systemDefault())}.minByOrNull {it.startAt!!}
    if(next!=null) item {
        PlannerCard(color=MaterialTheme.colorScheme.secondaryContainer,endColor=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.65f),onClick={openTomorrow()}) {
            Box(Modifier.fillMaxWidth().heightIn(min=64.dp)) {
                Landscape(Modifier.align(Alignment.BottomStart).width(86.dp).height(58.dp),sunrise=true)
                Row(Modifier.padding(start=91.dp,end=6.dp,top=10.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Text("Tomorrow · ${next.title} ${recordClock(next.startAt)}",style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.secondary,maxLines=2,overflow=TextOverflow.Ellipsis);if(tomorrow.size>1) Text("+${tomorrow.size-1} more",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)}
                    CircleAction(Glyph.Right,"Open tomorrow",{openTomorrow()})
                }
            }
        }
    }

}
