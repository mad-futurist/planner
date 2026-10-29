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

@Composable fun ExpandRow(label: String, modifier: Modifier=Modifier, expanded: Boolean=false, onClick: () -> Unit) {
    Surface(onClick=onClick,color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.7f),shape=CircleShape,modifier=modifier.fillMaxWidth().heightIn(min=40.dp)) {
        Row(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSecondaryContainer); PlannerIcon(if(expanded) Glyph.Up else Glyph.Down,Modifier.size(16.dp),MaterialTheme.colorScheme.onSecondaryContainer) }
    }
}
