package app.planner.presentation.today.components

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
import app.planner.domain.model.Record
import app.planner.presentation.common.TaskRow
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.components.PlannerCard
import app.planner.presentation.design.components.PlannerIcon
import app.planner.presentation.design.illustrations.Landscape
import java.time.*

import app.planner.presentation.today.*

internal fun LazyListScope.doneTodaySection(completed: List<Record>, showDone: Boolean, onShowDoneChange: (Boolean) -> Unit, complete: (Record) -> Unit, edit: (String) -> Unit) {
    item {
        Spacer(Modifier.height(18.dp))
        PlannerCard(color=MaterialTheme.colorScheme.primaryContainer,onClick={onShowDoneChange(!showDone)}) {
            Box(Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Landscape(Modifier.align(Alignment.BottomEnd).width(150.dp).height(44.dp))
                Row(Modifier.padding(horizontal=10.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary,CircleShape),contentAlignment=Alignment.Center) {PlannerIcon(Glyph.Check,Modifier.size(20.dp),MaterialTheme.colorScheme.onPrimary)}
                    Text("Done today: ${completed.size}",Modifier.padding(start=12.dp),style=MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    if(showDone) items(completed,key={"done:${it.id}"}) {TaskRow(it,complete) {edit(it.id)}}

}
