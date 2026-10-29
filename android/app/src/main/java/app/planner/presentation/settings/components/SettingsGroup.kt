package app.planner.presentation.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.components.PlannerCard
import app.planner.presentation.design.components.PlannerIcon

@Composable internal fun SettingsGroup(label:String,title:String,subtitle:String,glyph:Glyph,content:@Composable ColumnScope.()->Unit) {
    var expanded by rememberSaveable {mutableStateOf(false)}
    PlannerCard {
        Column(Modifier.fillMaxWidth().clickable {expanded=!expanded}.padding(14.dp)) {
            Text(label,style=MaterialTheme.typography.labelSmall,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Surface(color=if(glyph==Glyph.Sparkles || glyph==Glyph.Download) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,shape=CircleShape) {Box(Modifier.size(38.dp),contentAlignment=Alignment.Center) {PlannerIcon(glyph,Modifier.size(22.dp))}}
                Column(Modifier.weight(1f).padding(horizontal=12.dp)) {Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                PlannerIcon(if(expanded) Glyph.Up else Glyph.Right,Modifier.size(16.dp))
            }
        }
        if(expanded) Column(Modifier.padding(start=16.dp,end=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
