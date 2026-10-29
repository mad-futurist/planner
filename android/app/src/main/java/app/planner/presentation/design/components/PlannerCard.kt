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

@Composable fun PlannerCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerLow, endColor: Color = color, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape=RoundedCornerShape(18.dp)
    val click = if(onClick != null) Modifier.clickable(onClick=onClick) else Modifier
    Column(modifier.fillMaxWidth().shadow(3.dp,shape,ambientColor=Color(0x101D4B36),spotColor=Color(0x141D4B36)).clip(shape).background(Brush.linearGradient(listOf(color,endColor))).border(.6.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f),shape).then(click), content=content)
}
