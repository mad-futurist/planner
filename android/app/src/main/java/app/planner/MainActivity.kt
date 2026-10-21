package app.planner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.planner.features.PlannerViewModel
import app.planner.features.PlannerUI
import app.planner.sync.SyncEngine
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import android.content.Intent
import app.planner.features.overlay.OverlayManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: PlannerViewModel by viewModels()
    @Inject lateinit var sync: SyncEngine
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PlannerUI(vm) }
        if (intent.getBooleanExtra("capture", false)) vm.focusCapture.value += 1
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) { sync.run(); vm.refreshDay(); delay(8000) }
            }
        }
    }
    override fun onStart() { super.onStart(); OverlayManager.appVisible.value = true }
    override fun onStop() { OverlayManager.appVisible.value = false; super.onStop() }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try { OverlayManager.reconcile(this@MainActivity, vm.preferences.values.first()) }
            catch (_: java.io.IOException) { vm.message.value = "Couldn't read capture preferences. In-app capture is available." }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("capture", false)) vm.focusCapture.value += 1
    }
}
