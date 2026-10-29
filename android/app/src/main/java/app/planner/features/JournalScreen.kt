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
import java.time.format.DateTimeFormatter

data class JournalActions(val reprocess: (RawNote) -> Unit, val page: (Int) -> Unit, val retry: () -> Unit)

@Composable internal fun JournalScreen(state:JournalUiState,actions:JournalActions,nav:ScreenNavigation,edit:(String)->Unit,review:(String)->Unit) {
    val notes = state.entries
    val page = state.page
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("journal-list"),contentPadding=ScreenPadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {ScreenHeader("Journal",nav,"Original thoughts, always here.")}
        if(notes.isEmpty()) item {LocalQueryHint(state.reads,"journal","No notes on this page. Write anything below.",actions.retry)}
        items(notes,key={it.note.id}) {entry->
            val note = entry.note
            val records = entry.records
            PlannerCard {Column(Modifier.padding(16.dp)) {
                Text(note.originalContent,style=MaterialTheme.typography.bodyLarge)
                if(note.content!=note.originalContent) Text("Updated: ${note.content}",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium)
                Text(Instant.parse(note.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d · HH:mm")),Modifier.padding(top=12.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(note.privacyMode=="LOCAL_ONLY") "Private · on this device" else when(note.processingStatus) {"organized"->"Organized";"review"->"Needs review";"unconfigured"->"AI is not configured";"failed"->"AI processing failed · note saved";"rate_limited"->"AI daily limit reached · resumes later, note saved";else->"Saved · interpretation pending"},style=MaterialTheme.typography.bodySmall)
                for(record in records) TextButton({edit(record.id)}) {Text("${record.baseKind.replaceFirstChar {it.uppercase()}} · ${record.title}${if(record.state=="archived") " · Archived" else ""}",maxLines=2)}
                Row {TextButton({review(note.id)}) {Text(if(records.isEmpty()) "Organize / review" else "Add interpretation")};if(note.privacyMode!="LOCAL_ONLY") TextButton({actions.reprocess(note)}) {Text("Retry AI")}}
            }}
        }
        item {Row {TextButton({actions.page((page-1).coerceAtLeast(0))},enabled=page>0) {Text("Newer")};TextButton({actions.page(page+1)},enabled=notes.size==50) {Text("Older")}}}
    }
}
