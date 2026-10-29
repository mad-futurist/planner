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
import app.planner.presentation.design.components.EditorialHeader
import app.planner.presentation.navigation.PlannerRoutes
import app.planner.presentation.navigation.ScreenNavigation

@Composable internal fun ScreenHeader(title:String,nav:ScreenNavigation,subtitle:String?=null,status:String?=null) {
    val backAction: (() -> Unit)? = if(title in setOf("Settings","Journal","Review")) { {nav.back()} } else null
    EditorialHeader(title,subtitle,status,{nav.navigate(PlannerRoutes.Settings)},{nav.navigate(PlannerRoutes.Profile)},backAction)
}
