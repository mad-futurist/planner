package app.planner.presentation.common.editor

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.*
import com.google.gson.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun TimeControl(label: String, value: String?, zone: String, changed: (String?) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton({
            val current = value?.let { Instant.parse(it).atZone(ZoneId.of(zone)) } ?: ZonedDateTime.now(ZoneId.of(zone))
            DatePickerDialog(context, { _, y, m, d ->
                TimePickerDialog(context, { _, hour, minute ->
                    val local = LocalDateTime.of(y, m + 1, d, hour, minute)
                    val timezone = ZoneId.of(zone)
                    val offsets = timezone.rules.getValidOffsets(local)
                    when (offsets.size) {
                        0 -> android.app.AlertDialog.Builder(context).setMessage("This time doesn't exist because the clocks change. Choose another time.").setPositiveButton("OK", null).show()
                        1 -> changed(local.atOffset(offsets[0]).toInstant().toString())
                        else -> android.app.AlertDialog.Builder(context).setTitle("This time occurs twice").setItems(arrayOf("First occurrence", "Second occurrence")) { _, which -> changed(local.atOffset(offsets[which]).toInstant().toString()) }.show()
                    }
                }, current.hour, current.minute, true).show()
            }, current.year, current.monthValue - 1, current.dayOfMonth).show()
        }, Modifier.weight(1f)) { Text("$label: ${value?.let { Instant.parse(it).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("MMM d · HH:mm")) } ?: "Not set"}") }
        if (value != null) TextButton({ changed(null) }) { Text("×") }
    }
}
