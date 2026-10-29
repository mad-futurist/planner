package app.planner.presentation.design.illustrations

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp

/** Original vector landscapes inspired by the supplied composition. */
@Composable fun Landscape(modifier: Modifier, sunrise: Boolean = false, violet: Boolean = false) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        if (sunrise) drawCircle(Brush.radialGradient(listOf(Color(0xFFFFCA78), Color(0xFFFFA438)), center = Offset(w*.63f,h*.30f), radius = h*.35f), h*.30f, Offset(w*.63f,h*.30f))
        val back = Path().apply { moveTo(0f,h*.88f); cubicTo(w*.30f,h*.85f,w*.47f,h*.20f,w*.77f,h*.73f); cubicTo(w*.89f,h*.88f,w,h*.75f,w,h); lineTo(0f,h); close() }
        drawPath(back, if (violet) Color(0xFFBBC0FF) else if (sunrise) Color(0xFFFFE1AC) else Color(0xFFBCE0D1))
        val front = Path().apply { moveTo(0f,h); cubicTo(w*.28f,h*.50f,w*.58f,h*.70f,w,h); close() }
        drawPath(front, if (violet) Color(0xFFA1ACF5) else if (sunrise) Color(0xFFFFD598) else Color(0xFF9DD2BA))
        if (!sunrise) {
            val x=w*.76f; val y=h*.95f
            val stem=if(violet) Color(0xFF6876DC) else Color(0xFF4D9E88)
            drawLine(stem, Offset(x,y), Offset(x-w*.02f,y-h*.56f), 1.5.dp.toPx())
            for (i in 0..2) {
                val py=y-h*(.20f+i*.14f)
                val leaf=Path().apply { moveTo(x,py); cubicTo(x-w*.16f,py-h*.02f,x-w*.12f,py-h*.25f,x,py); cubicTo(x+w*.16f,py-h*.15f,x+w*.12f,py-h*.30f,x,py); close() }
                drawPath(leaf, if(violet) Color(0xFF6777D6) else Color(0xFF69B59D))
            }
        }
    }
}

@Composable fun IdeaStairs(modifier: Modifier) {
    Canvas(modifier) {
        val w=size.width; val h=size.height
        drawCircle(Color.White.copy(alpha=.8f), w*.20f, Offset(w*.72f,h*.18f))
        val arch=Path().apply { moveTo(0f,h); lineTo(0f,h*.05f); cubicTo(w*.55f,-h*.06f,w*.68f,h*.32f,w*.68f,h*.62f); lineTo(w,h); close() }
        drawPath(arch,Brush.linearGradient(listOf(Color(0xFF858CFD),Color(0xFFCCD0FF))))
        for(i in 0..6) { val x=w*i*.10f; val y=h*(.51f+i*.067f); drawRect(Color(0xFF4353F4).copy(alpha=.65f+i*.045f), Offset(0f,y), Size(x+w*.32f,h*.07f)) }
        drawLine(Color(0xFF293AB3),Offset(w*.18f,h*.47f),Offset(w*.18f,h*.26f),2.dp.toPx())
        drawOval(Color(0xFF3447CC), Offset(w*.03f,h*.26f),Size(w*.16f,h*.12f)); drawOval(Color(0xFF5061DE),Offset(w*.18f,h*.20f),Size(w*.17f,h*.13f))
    }
}
