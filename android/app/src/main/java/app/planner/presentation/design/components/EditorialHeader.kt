package app.planner.presentation.design.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import java.time.*

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
