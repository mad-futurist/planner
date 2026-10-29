package app.planner.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.components.PlannerIcon

data class Destination(val route: String,val label: String,val icon: Glyph)

val PrimaryDestinations=listOf(Destination(PlannerRoutes.Today,"Today",Glyph.Home),Destination(PlannerRoutes.Calendar,"Calendar",Glyph.Calendar),Destination(PlannerRoutes.Tasks,"Tasks",Glyph.Tasks),Destination(PlannerRoutes.Ideas,"Ideas",Glyph.Bulb),Destination(PlannerRoutes.Profile,"Profile",Glyph.User))

@Composable fun PlannerBottomNavigation(route: String?, navigate: (String)->Unit) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(24.dp),shadowElevation=1.dp,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(5.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
            PrimaryDestinations.forEach { destination ->
                val selected=route==destination.route
                Column(Modifier.weight(1f).clip(RoundedCornerShape(19.dp)).background(if(selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).selectable(selected=selected,role=Role.Tab,onClick={navigate(destination.route)}).heightIn(min=58.dp).padding(vertical=8.dp).semantics { contentDescription="Navigate ${destination.label}" },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    PlannerIcon(destination.icon,Modifier.size(22.dp),if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(destination.label,style=MaterialTheme.typography.labelSmall,color=if(selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                }
            }
        }
    }
}
