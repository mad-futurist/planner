package app.planner.presentation.common

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.navigation.compose.*

@Composable internal fun ModalStatusDialog(title: String, error: String?, retry: ()->Unit, close: ()->Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        if (error == null) CircularProgressIndicator() else Text(error)
    }, confirmButton = { if (error != null) TextButton(retry) { Text("Retry") } }, dismissButton = { TextButton(close) { Text("Close") } })
}
