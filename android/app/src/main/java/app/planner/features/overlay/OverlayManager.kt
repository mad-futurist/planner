package app.planner.features.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

object OverlayManager {
    val appVisible = MutableStateFlow(false)
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
