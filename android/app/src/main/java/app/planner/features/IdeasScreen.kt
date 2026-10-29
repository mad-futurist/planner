package app.planner.features

import app.planner.navigation.ScreenNavigation

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

data class IdeasActions(val convert: (Record) -> Unit, val search: (String) -> Unit, val page: (Int) -> Unit, val retry: () -> Unit)

@Composable internal fun IdeasScreen(state:IdeasUiState,actions:IdeasActions,nav:ScreenNavigation,status:String,edit:(String)->Unit,develop:(String)->Unit) {
    val ideas = state.ideas
    val query = state.query
    val page = state.page
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("ideas-list"),contentPadding=ScreenPadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {ScreenHeader("Ideas",nav,"A little room for what could be.",status)}
        item {OutlinedTextField(query,{actions.search(it)},Modifier.fillMaxWidth().testTag("idea-search"),placeholder={Text("Find a saved idea…")},singleLine=true,shape=RoundedCornerShape(18.dp))}
        item {PlannerCard(color=MaterialTheme.colorScheme.primaryContainer) {Box(Modifier.fillMaxWidth().heightIn(min=72.dp)) {Landscape(Modifier.align(Alignment.BottomEnd).width(130.dp).height(70.dp));Column(Modifier.padding(16.dp).fillMaxWidth(.65f)) {Text("Keep the possibility",style=MaterialTheme.typography.titleMedium);Text("No deadline. Come back when it feels right.",style=MaterialTheme.typography.bodySmall)}}}}
        if(ideas.isEmpty()) item {LocalQueryHint(state.reads,"ideas","Capture an idea below, or organize an existing note from Journal.",actions.retry)}
        items(ideas,key={it.id}) {idea->IdeaCard(idea,{edit(idea.id)},{develop(idea.id)},convert={actions.convert(idea)})}
        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {TextButton({actions.page((page-1).coerceAtLeast(0))},enabled=page>0) {Text("Previous")};TextButton({actions.page(page+1)},enabled=ideas.size==50) {Text("More ideas")}}}
    }
}
