package app.planner.infrastructure.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object OverlayManager {
    private val visible = MutableStateFlow(false)
    val appVisible = visible.asStateFlow()
    fun setAppVisible(value: Boolean) { visible.value = value }
    fun start(context: Context): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        return try { ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java)); true } catch (_: RuntimeException) { false }
    }
    fun stop(context: Context) { context.stopService(Intent(context, OverlayService::class.java)) }
    fun reconcile(context: Context, preferences: Map<String, String>) {
        if (preferences["overlay"] == "true" && Settings.canDrawOverlays(context)) start(context) else stop(context)
        CaptureNotification.update(context, preferences["notification"] == "true")
    }
}
