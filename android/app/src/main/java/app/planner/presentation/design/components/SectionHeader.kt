package app.planner.presentation.design.components

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
import java.time.*

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
