package app.planner.presentation.profile

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
import app.planner.presentation.common.ScreenHeader
import app.planner.presentation.common.ScreenPadding
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.components.Metric
import app.planner.presentation.design.components.PlannerCard
import app.planner.presentation.design.components.PlannerIcon
import app.planner.presentation.design.components.SectionHeader
import app.planner.presentation.design.illustrations.Landscape
import app.planner.presentation.design.theme.Palette
import app.planner.presentation.navigation.PlannerRoutes
import app.planner.presentation.navigation.ScreenNavigation
import app.planner.presentation.profile.components.ProfileLink
import java.time.*

@Composable internal fun ProfileScreen(state:ProfileUiState,actions:ProfileActions,nav:ScreenNavigation) {
    val profile = state.profile
    val collections = state.collections
    val metadata = state.metadata
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("profile-list"),contentPadding=ScreenPadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {ScreenHeader("Profile",nav,"Your thoughts, in one place.")}
        item {PlannerCard(color=MaterialTheme.colorScheme.primaryContainer) {Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically) {Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceContainerLow,CircleShape),contentAlignment=Alignment.Center) {PlannerIcon(Glyph.User,Modifier.size(27.dp))};Column(Modifier.padding(start=14.dp)) {Text(if(metadata["owner"]==null) "Local device profile" else "Connected workspace",style=MaterialTheme.typography.titleMedium);Text(metadata["owner"]?.let {"Workspace ${it.take(8)}"} ?: "Private by default · no account needed",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
        item {Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {Metric("Tasks completed",profile.completed,Modifier.weight(1f),false);Metric("Ideas saved",profile.ideas,Modifier.weight(1f),true)}}
        item {Text(if(profile.notes==0) "Your space starts with a thought. Write anything below." else "${profile.notes} original notes · ${profile.tasks} unfinished tasks",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(collections.isNotEmpty()) item {SectionHeader("Your collections",Glyph.Tag);for(collection in collections) Row(Modifier.fillMaxWidth().padding(vertical=8.dp)) {Text(collection.name,Modifier.weight(1f));Text("${collection.count}")}}
        item {SectionHeader("Your space",Glyph.Leaf)}
        item {ProfileLink("Journal","Original notes, always preserved",Glyph.Book) {nav.navigate(PlannerRoutes.Journal)}}
        item {ProfileLink("Review","A few decisions, at your pace",Glyph.Sun) {nav.navigate(PlannerRoutes.Review)}}
        item {ProfileLink("Settings","Privacy, capture and connection",Glyph.Palette) {nav.navigate(PlannerRoutes.Settings)}}
        item {Landscape(Modifier.fillMaxWidth().height(85.dp))}
    }
}
