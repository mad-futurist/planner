package app.planner.presentation.capture

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.*
import androidx.navigation.compose.*
import app.planner.presentation.capture.components.CapturePreviewPanel
import app.planner.presentation.capture.components.DoubtTransformation
import app.planner.presentation.capture.components.clarificationTaps
import app.planner.presentation.design.components.CircleAction
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.components.PlannerIcon

@Composable internal fun CaptureBar(state:CaptureUiState, actions:CaptureActions, focus:FocusRequester) {
    val draft = state.draft
    val saving = state.saving
    val private = state.privateCapture
    val kind = state.captureKind
    var menu by remember { mutableStateOf(false) }
    val preview = state.capturePreview
    val hints = state.captureHints
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,shadowElevation=2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box { CircleAction(Glyph.Plus,"Choose capture type",{menu=true},primary=true)
                    DropdownMenu(menu,{menu=false}) {
                        for((label,value) in listOf((if(private) "Automatic · organize on device" else "Note · AI organizes") to null,"Original note only" to "raw_note","Task for today" to "task","Task · no date" to "task_unscheduled","Idea" to "idea","Event" to "event","Information" to "information")) DropdownMenuItem({Text(label)},{actions.selectKind(value);menu=false;focus.requestFocus()})
                    }
                }
                BasicTextField(draft,actions.setDraft,Modifier.weight(1f).heightIn(min=52.dp,max=120.dp).padding(horizontal=8.dp,vertical=15.dp).focusRequester(focus).testTag("capture").clarificationTaps({textLayout},preview,hints.clarifications) {actions.showClarification(it)},textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),maxLines=4,onTextLayout={textLayout=it},visualTransformation=DoubtTransformation(preview.doubts,hints.clarifications,MaterialTheme.colorScheme.secondary,MaterialTheme.colorScheme.primary),decorationBox={inner -> if(draft.isEmpty()) Text("Write anything…",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant); inner()})
                FilledIconButton({actions.save(kind, false)},enabled=draft.isNotBlank() && !saving,modifier=Modifier.size(48.dp).testTag("send").semantics {contentDescription="Save note"},colors=IconButtonDefaults.filledIconButtonColors(disabledContainerColor=MaterialTheme.colorScheme.primary,disabledContentColor=MaterialTheme.colorScheme.onPrimary)) { if(saving) Text("…") else PlannerIcon(Glyph.ArrowUp,Modifier.size(24.dp),MaterialTheme.colorScheme.onPrimary) }
            }
            CapturePreviewPanel(state, actions, preview)
            Row(Modifier.fillMaxWidth().heightIn(min=32.dp).clickable {actions.privacy(!private)}.testTag("privacy").padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                PlannerIcon(if(private) Glyph.Shield else Glyph.Sparkles,Modifier.size(13.dp))
                Text(if(private) "Private" else "Cloud AI",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(kind!=null) Text(when(kind) {"task"->"Task · Today";"task_unscheduled"->"Task · No date";"task_deadline"->"Task · Deadline";"raw_note"->"Original only";else->kind!!.replaceFirstChar {it.uppercase()}},style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                PlannerIcon(Glyph.Down,Modifier.size(12.dp))
            }
        }
    }
}
