package app.planner.presentation.design.components

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
import java.time.*

@Composable internal fun Metric(label:String,count:Int,modifier:Modifier,violet:Boolean) {PlannerCard(modifier,color=if(violet) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer) {Column(Modifier.padding(16.dp)) {Text("$count",style=MaterialTheme.typography.headlineLarge);Text(label,style=MaterialTheme.typography.bodySmall)}}}
