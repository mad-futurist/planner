package app.planner.infrastructure.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import app.planner.MainActivity
import app.planner.R

object CaptureNotification {
    private const val CHANNEL = "quick_capture"
    fun notification(context: Context): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Quick capture", NotificationManager.IMPORTANCE_LOW))
        val intent = Intent(context, MainActivity::class.java).putExtra("capture", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_capture).setContentTitle("Planner quick capture").setContentText("Tap to write a thought. Saved locally first.").setContentIntent(pending).setVisibility(Notification.VISIBILITY_PRIVATE).addAction(Notification.Action.Builder(null, "Capture", pending).build()).build()
    }
    fun update(context: Context, enabled: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (enabled && manager.areNotificationsEnabled() && !OverlayService.running.value) manager.notify(101, notification(context)) else manager.cancel(101)
    }
}
