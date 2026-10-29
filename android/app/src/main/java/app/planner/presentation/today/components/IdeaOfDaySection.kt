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
import app.planner.presentation.common.LocalQueryHint
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.components.IdeaCard
import app.planner.presentation.design.components.SectionHeader
import java.time.*

import app.planner.presentation.today.*

internal fun LazyListScope.ideaOfDaySection(idea: Record?, reads: Map<String,String>, retry: () -> Unit, edit: (String) -> Unit, develop: (String) -> Unit, later: (Record) -> Unit) {
    item {SectionHeader("Idea of the day",Glyph.Bulb,violet=true)}
    val savedIdea=idea
    if(savedIdea==null) item {LocalQueryHint(reads,"idea","Space for a thought to revisit.",retry)} else item {
        Box(Modifier.padding(start=44.dp)) {IdeaCard(savedIdea,{edit(savedIdea.id)},{develop(savedIdea.id)},{later(savedIdea)})}
    }

}
