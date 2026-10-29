package app.planner.features

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
import app.planner.core.designsystem.*
import app.planner.domain.model.*
import java.time.*

@Composable internal fun ProfileLink(title:String,subtitle:String,glyph:Glyph,click:()->Unit) {PlannerCard(onClick=click) {Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {PlannerIcon(glyph,Modifier.size(23.dp));Column(Modifier.weight(1f).padding(horizontal=14.dp)) {Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};PlannerIcon(Glyph.Right,Modifier.size(17.dp))}}}
