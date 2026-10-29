package app.planner.infrastructure.overlay

import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser
import androidx.core.graphics.withTranslation
import app.planner.presentation.design.components.Glyph

/** Shares the original vector icon geometry with Compose without adding a Compose window. */
class OverlayGlyphDrawable(glyph: Glyph, color: Int) : Drawable() {
    private val path = PathParser.createPathFromPathData(glyph.path)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; style=Paint.Style.STROKE; strokeWidth=1.7f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    override fun draw(canvas: Canvas) { canvas.withTranslation(bounds.left.toFloat(),bounds.top.toFloat()) { scale(bounds.width()/24f,bounds.height()/24f); drawPath(path,paint) } }
    override fun setAlpha(alpha:Int) { paint.alpha=alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter:ColorFilter?) { paint.colorFilter=colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity():Int = PixelFormat.TRANSLUCENT
}
