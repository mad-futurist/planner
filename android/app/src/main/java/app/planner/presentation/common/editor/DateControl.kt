package app.planner.presentation.common.editor

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.compose.*
import com.google.gson.*
import java.time.*

@Composable internal fun DateControl(label: String, value: String?, changed: (String?) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton({ val date = value?.let(LocalDate::parse) ?: LocalDate.now(); DatePickerDialog(context, { _, y, m, d -> changed(LocalDate.of(y, m + 1, d).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, Modifier.weight(1f)) { Text("$label: ${value ?: "Not set"}") }
        if (value != null) TextButton({ changed(null) }, Modifier.semantics { contentDescription = "Clear $label" }) { Text("×") }
    }
}
