package app.planner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.planner.application.port.CloudGateway
import app.planner.infrastructure.overlay.OverlayManager
import app.planner.presentation.capture.CaptureViewModel
import app.planner.presentation.shell.PlannerUI
import app.planner.presentation.shell.PlannerViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: PlannerViewModel by viewModels()
    @Inject lateinit var sync: CloudGateway
    private val capture: CaptureViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PlannerUI(vm, capture) }
        if (intent.getBooleanExtra("capture", false)) capture.focus()
        acceptAuthIntent(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) { sync.sync(); vm.refreshDay(); delay(8000) }
            }
        }
    }
    override fun onStart() { super.onStart(); OverlayManager.setAppVisible(true) }
    override fun onStop() { OverlayManager.setAppVisible(false); super.onStop() }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try { OverlayManager.reconcile(this@MainActivity, vm.capturePreferences()) }
            catch (_: java.io.IOException) { vm.report("Couldn't read capture preferences. In-app capture is available.") }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("capture", false)) capture.focus()
        acceptAuthIntent(intent)
    }
    private fun acceptAuthIntent(intent: Intent) {
        intent.data?.takeIf { it.scheme == "app.planner" && it.host == "auth" }?.let { uri -> intent.data = null; vm.finishGoogle(uri.toString()) }
    }
}
