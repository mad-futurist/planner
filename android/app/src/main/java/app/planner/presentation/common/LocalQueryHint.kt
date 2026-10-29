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
import app.planner.presentation.design.components.EmptyState

@Composable internal fun LocalQueryHint(reads:Map<String,String>,key:String,emptyText:String,retry:()->Unit) {
    when(reads[key]) {
        "ready"->EmptyState(emptyText)
        "error"->{EmptyState("Couldn't read saved data. Your input is still here.");TextButton(retry) {Text("Try again")}}
        else->EmptyState("Reading saved data…")
    }
}
