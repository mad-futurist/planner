package app.planner.presentation.design.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.planner.domain.model.Record
import app.planner.presentation.design.format.recordDetails
import app.planner.presentation.design.illustrations.IdeaStairs
import java.time.*

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
