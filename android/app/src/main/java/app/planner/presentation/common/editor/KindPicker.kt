package app.planner.presentation.common.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.compose.*
import com.google.gson.*
import java.time.*

@Composable internal fun KindPicker(kind: String, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { TextButton({ expanded = true }) { Text("Kind: ${kind.replaceFirstChar { it.uppercase() }}") }; DropdownMenu(expanded, { expanded = false }) { for (value in listOf("task", "event", "idea", "information")) DropdownMenuItem({ Text(value.replaceFirstChar { it.uppercase() }) }, { select(value); expanded = false }) } }
}
