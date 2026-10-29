package app.planner.core.designsystem

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.planner.domain.model.Record
import com.google.gson.JsonParser
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun CircleAction(glyph: Glyph, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    IconButton(onClick, modifier.size(48.dp).semantics { contentDescription = label }) {
        Box(Modifier.size(38.dp).background(if(primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            PlannerIcon(glyph, Modifier.size(21.dp), if(primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable fun PlannerCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerLow, endColor: Color = color, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape=RoundedCornerShape(18.dp)
    val click = if(onClick != null) Modifier.clickable(onClick=onClick) else Modifier
    Column(modifier.fillMaxWidth().shadow(3.dp,shape,ambientColor=Color(0x101D4B36),spotColor=Color(0x141D4B36)).clip(shape).background(Brush.linearGradient(listOf(color,endColor))).border(.6.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f),shape).then(click), content=content)
}

@Composable fun EditorialHeader(title: String, subtitle: String?, status: String?, onStatus: () -> Unit, onProfile: () -> Unit, back: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom=12.dp)) {
        if(back != null) Row(Modifier.fillMaxWidth()) { IconButton(back, Modifier.semantics { contentDescription="Back" }) { PlannerIcon(Glyph.Left,Modifier.size(24.dp)) } }
        BoxWithConstraints {
            val size=(if(title.length>7) 42.sp else 52.sp) * (if(maxWidth<340.dp) .88f else 1f)
            val compact=maxWidth<350.dp || LocalDensity.current.fontScale>1.2f
            Column {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.displayLarge.copy(fontSize=size),maxLines=if(compact) 2 else 1)
                if(status!=null && !compact) {
                    Surface(onClick=onStatus,color=MaterialTheme.colorScheme.primaryContainer,shape=CircleShape,modifier=Modifier.widthIn(max=122.dp).heightIn(min=36.dp)) {
                        Row(Modifier.padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            PlannerIcon(Glyph.Sparkles,Modifier.size(14.dp))
                            Text(status,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
                if(title!="Profile" && back==null) CircleAction(Glyph.User,"Open profile",onProfile,Modifier.padding(start=4.dp))
            }
            if(status!=null && compact) Text(status,Modifier.padding(top=3.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        subtitle?.let { Text(it,Modifier.padding(top=1.dp,start=2.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable fun SectionHeader(title: String, glyph: Glyph, count: Int? = null, warm: Boolean=false, violet: Boolean=false, expanded: Boolean?=null, action: (() -> Unit)?=null) {
    val container=if(violet) MaterialTheme.colorScheme.tertiaryContainer else if(warm) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val accent=if(violet) MaterialTheme.colorScheme.tertiary else if(warm) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=8.dp).heightIn(min=40.dp).then(if(action!=null) Modifier.clickable(onClick=action) else Modifier),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(container,CircleShape),contentAlignment=Alignment.Center) { PlannerIcon(glyph,Modifier.size(21.dp),accent) }
        Text(title,Modifier.padding(start=12.dp).weight(1f),style=MaterialTheme.typography.titleMedium)
        count?.let { Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=CircleShape) { Text("$it",Modifier.padding(horizontal=9.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium) } }
        if(expanded!=null) PlannerIcon(if(expanded) Glyph.Up else Glyph.Down,Modifier.padding(start=10.dp).size(19.dp))
    }
}

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

@Composable fun ExpandRow(label: String, modifier: Modifier=Modifier, expanded: Boolean=false, onClick: () -> Unit) {
    Surface(onClick=onClick,color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.7f),shape=CircleShape,modifier=modifier.fillMaxWidth().heightIn(min=40.dp)) {
        Row(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSecondaryContainer); PlannerIcon(if(expanded) Glyph.Up else Glyph.Down,Modifier.size(16.dp),MaterialTheme.colorScheme.onSecondaryContainer) }
    }
}

@Composable fun IdeaCard(record: Record, open: () -> Unit, develop: () -> Unit, later: (() -> Unit)?=null, convert: (() -> Unit)?=null) {
    PlannerCard(modifier=Modifier.testTag("idea-${record.id}"),color=MaterialTheme.colorScheme.tertiaryContainer,onClick=open) {
        Box(Modifier.fillMaxWidth().heightIn(min=148.dp)) {
            IdeaStairs(Modifier.align(Alignment.BottomStart).width(76.dp).height(146.dp))
            Column(Modifier.padding(start=87.dp,end=14.dp,top=16.dp,bottom=14.dp)) {
                Text(record.title,style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                val details=recordDetails(record)
                if(details.isNotBlank()) Text(details,Modifier.padding(top=7.dp),style=MaterialTheme.typography.bodyMedium,maxLines=4,overflow=TextOverflow.Ellipsis)
                Row(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically) {
                    Button(develop,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.tertiary),contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp)) { PlannerIcon(Glyph.Sparkles,Modifier.size(15.dp),MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(5.dp)); Text("Develop",style=MaterialTheme.typography.labelMedium) }
                    if(later!=null) TextButton(later,contentPadding=PaddingValues(horizontal=9.dp)) { Text("Later",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onTertiaryContainer) }
                }
                if(convert!=null) TextButton(convert,modifier=Modifier.testTag("convert-${record.id}"),contentPadding=PaddingValues(0.dp)) { Text("Make a task",style=MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable fun EmptyState(text: String) { Text(text,Modifier.padding(start=48.dp,top=6.dp,bottom=14.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
