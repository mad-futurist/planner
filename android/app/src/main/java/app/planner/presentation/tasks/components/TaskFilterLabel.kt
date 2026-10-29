package app.planner.presentation.tasks.components

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

@Composable internal fun TaskFilterLabel(label:String,count:Int,selected:Boolean) {
    val badge: @Composable () -> Unit = {Surface(shape=CircleShape,color=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer) {Text("$count",Modifier.padding(horizontal=6.dp,vertical=3.dp),style=MaterialTheme.typography.labelSmall,color=if(selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)}}
    if(LocalDensity.current.fontScale>1.2f) Column(Modifier.heightIn(min=48.dp).padding(horizontal=4.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(label,style=MaterialTheme.typography.labelMedium,maxLines=1);badge()}
    else Row(Modifier.heightIn(min=48.dp).padding(horizontal=5.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp,Alignment.CenterHorizontally)) {Text(label,style=MaterialTheme.typography.labelMedium);badge()}
}
