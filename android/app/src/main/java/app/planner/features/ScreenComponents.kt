package app.planner.features

import app.planner.navigation.PlannerRoutes

import app.planner.navigation.ScreenNavigation

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
import app.planner.core.designsystem.*
import app.planner.domain.model.*

@Composable internal fun ScreenHeader(title:String,nav:ScreenNavigation,subtitle:String?=null,status:String?=null) {
    val backAction: (() -> Unit)? = if(title in setOf("Settings","Journal","Review")) { {nav.back()} } else null
    EditorialHeader(title,subtitle,status,{nav.navigate(PlannerRoutes.Settings)},{nav.navigate(PlannerRoutes.Profile)},backAction)
}

@Composable internal fun LocalQueryHint(reads:Map<String,String>,key:String,emptyText:String,retry:()->Unit) {
    when(reads[key]) {
        "ready"->EmptyState(emptyText)
        "error"->{EmptyState("Couldn't read saved data. Your input is still here.");TextButton(retry) {Text("Try again")}}
        else->EmptyState("Reading saved data…")
    }
}

@Composable internal fun TaskRow(record:Record,complete:(Record)->Unit,compact:Boolean=false,open:()->Unit) {
    TaskCard(record,{complete(record)},open,compact)
}

@Composable internal fun ModalStatusDialog(title: String, error: String?, retry: ()->Unit, close: ()->Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        if (error == null) CircularProgressIndicator() else Text(error)
    }, confirmButton = { if (error != null) TextButton(retry) { Text("Retry") } }, dismissButton = { TextButton(close) { Text("Close") } })
}
