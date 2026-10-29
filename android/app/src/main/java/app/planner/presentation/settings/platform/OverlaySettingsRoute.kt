package app.planner.presentation.settings.platform

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.infrastructure.overlay.CaptureNotification
import app.planner.infrastructure.overlay.OverlayManager
import app.planner.infrastructure.overlay.OverlayService
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.components.PlannerCard
import app.planner.presentation.design.components.PlannerIcon

@Composable fun OverlaySettingsRoute(values: Map<String, String>, setPreference: (String, String)->Unit, report: (String)->Unit) {
    val context = LocalContext.current
    val running by OverlayService.running.collectAsStateWithLifecycle()
    var explanation by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        run {
            val granted = Settings.canDrawOverlays(context)
            setPreference("overlay", granted.toString())
            if (granted && !OverlayManager.start(context)) report("Floating capture isn't available on this device. Use notification capture instead.")
            if (!granted) report("Overlay permission wasn't granted. In-app capture is still available.")
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        run { setPreference("notification", granted.toString()); CaptureNotification.update(context, granted); if (!granted) report("Notification permission wasn't granted.") }
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        PlannerCard(color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.55f)) {
            Column(Modifier.padding(14.dp)) {
                Text("QUICK CAPTURE",style=MaterialTheme.typography.labelSmall,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=CircleShape) {Box(Modifier.size(38.dp),contentAlignment=Alignment.Center) {PlannerIcon(Glyph.Sparkles,Modifier.size(22.dp),MaterialTheme.colorScheme.secondary)}}
                    Column(Modifier.weight(1f).padding(horizontal=12.dp)) {Text("Floating quick capture",style=MaterialTheme.typography.titleMedium);Text(if(running) "Available over other apps while unlocked" else "A small button for thoughts from anywhere.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Switch(values["overlay"]=="true" && Settings.canDrawOverlays(context),{enabled->
                        if(!enabled) {OverlayManager.stop(context);run {setPreference("overlay","false");CaptureNotification.update(context,values["notification"]=="true")}}
                        else if(Settings.canDrawOverlays(context)) run {setPreference("overlay","true");if(!OverlayManager.start(context)) report("Floating capture couldn't start. Try notification capture.")}
                        else explanation=true
                    })
                }
            }
        }
        PlannerCard {
            Column(Modifier.padding(14.dp)) {
                Text("NOTIFICATIONS",style=MaterialTheme.typography.labelSmall,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=CircleShape) {Box(Modifier.size(38.dp),contentAlignment=Alignment.Center) {PlannerIcon(Glyph.Bell,Modifier.size(22.dp))}}
                    Column(Modifier.weight(1f).padding(horizontal=12.dp)) {Text("Notification capture",style=MaterialTheme.typography.titleMedium);Text("A shortcut to capture when the bubble isn't running.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Switch(values["notification"]=="true",{enabled->if(enabled && Build.VERSION.SDK_INT>=33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else run {setPreference("notification",enabled.toString());CaptureNotification.update(context,enabled)}})
                }
            }
        }
    }
    if (explanation) AlertDialog(onDismissRequest = { explanation = false }, title = { Text("Capture over other apps") }, text = { Text("Android needs permission to display the small capture button above other apps. It only saves text you enter and doesn't read the screen. You can turn it off here anytime.") }, confirmButton = { TextButton({ explanation = false; OverlayManager.stop(context); permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }) { Text("Open Android settings") } }, dismissButton = { TextButton({ explanation = false }) { Text("Cancel") } })
}
