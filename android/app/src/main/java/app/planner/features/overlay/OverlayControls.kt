package app.planner.features.overlay

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import app.planner.core.designsystem.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.features.PlannerViewModel

@Composable fun OverlayControls(vm: PlannerViewModel) {
    val context = LocalContext.current
    val values by vm.settings.collectAsStateWithLifecycle()
    val running by OverlayService.running.collectAsStateWithLifecycle()
    var explanation by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.action {
            val granted = Settings.canDrawOverlays(context)
            vm.preferences.set("overlay", granted.toString())
            if (granted && !OverlayManager.start(context)) vm.message.value = "Floating capture isn't available on this device. Use notification capture instead."
            if (!granted) vm.message.value = "Overlay permission wasn't granted. In-app capture is still available."
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.action { vm.preferences.set("notification", granted.toString()); CaptureNotification.update(context, granted); if (!granted) vm.message.value = "Notification permission wasn't granted." }
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        PlannerCard(color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.55f)) {
            Column(Modifier.padding(14.dp)) {
                Text("QUICK CAPTURE",style=MaterialTheme.typography.labelSmall,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=CircleShape) {Box(Modifier.size(38.dp),contentAlignment=Alignment.Center) {PlannerIcon(Glyph.Sparkles,Modifier.size(22.dp),MaterialTheme.colorScheme.secondary)}}
                    Column(Modifier.weight(1f).padding(horizontal=12.dp)) {Text("Floating quick capture",style=MaterialTheme.typography.titleMedium);Text(if(running) "Available over other apps while unlocked" else "A small button for thoughts from anywhere.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Switch(values["overlay"]=="true" && Settings.canDrawOverlays(context),{enabled->
                        if(!enabled) {OverlayManager.stop(context);vm.action {vm.preferences.set("overlay","false");CaptureNotification.update(context,values["notification"]=="true")}}
                        else if(Settings.canDrawOverlays(context)) vm.action {vm.preferences.set("overlay","true");if(!OverlayManager.start(context)) vm.message.value="Floating capture couldn't start. Try notification capture."}
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
                    Switch(values["notification"]=="true",{enabled->if(enabled && Build.VERSION.SDK_INT>=33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.action {vm.preferences.set("notification",enabled.toString());CaptureNotification.update(context,enabled)}})
                }
            }
        }
    }
    if (explanation) AlertDialog(onDismissRequest = { explanation = false }, title = { Text("Capture over other apps") }, text = { Text("Android needs permission to display the small capture button above other apps. It only saves text you enter and doesn't read the screen. You can turn it off here anytime.") }, confirmButton = { TextButton({ explanation = false; OverlayManager.stop(context); permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }) { Text("Open Android settings") } }, dismissButton = { TextButton({ explanation = false }) { Text("Cancel") } })
}
