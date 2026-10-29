package app.planner.presentation.design.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import java.time.*

@Composable fun CircleAction(glyph: Glyph, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    IconButton(onClick, modifier.size(48.dp).semantics { contentDescription = label }) {
        Box(Modifier.size(38.dp).background(if(primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            PlannerIcon(glyph, Modifier.size(21.dp), if(primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
        }
    }
}
