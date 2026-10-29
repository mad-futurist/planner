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
import app.planner.domain.model.Record
import app.planner.presentation.design.components.TaskCard

@Composable internal fun TaskRow(record:Record,complete:(Record)->Unit,compact:Boolean=false,open:()->Unit) {
    TaskCard(record,{complete(record)},open,compact)
}
