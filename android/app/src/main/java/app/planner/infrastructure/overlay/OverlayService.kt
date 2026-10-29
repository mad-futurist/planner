package app.planner.infrastructure.overlay

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.Service
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.text.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import app.planner.domain.policy.CapturePrivacy
import app.planner.infrastructure.preferences.Preferences
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.theme.Palette
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class OverlayService : Service() {
    private class CaptureBubble(context: Context) : ImageButton(context) {
        override fun performClick(): Boolean = super.performClick()
    }
    @Inject lateinit var captureUseCase: app.planner.application.usecase.CaptureUseCase
    @Inject lateinit var preferences: Preferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ ->
        Toast.makeText(this, "Floating capture couldn't continue. Reopen Planner to try again.", Toast.LENGTH_LONG).show()
        stopSelf()
    })
    private lateinit var windows: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var editor: EditText? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var positionX = 1f
    private var positionY = 0.04f
    private var closing = false
    private var overlayAppearance = "system"
    private fun darkPanel() = overlayAppearance == "dark" || (overlayAppearance != "light" && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
    private fun paper() = if(darkPanel()) Color.rgb(23,36,31) else Palette.Ivory.toArgb()
    private fun ink() = if(darkPanel()) Color.rgb(234,243,237) else Palette.Charcoal.toArgb()
    private fun mint() = if(darkPanel()) Color.rgb(36,67,55) else Palette.Mint.toArgb()
    private fun secondaryInk() = if(darkPanel()) Color.rgb(177,196,187) else Palette.Secondary.toArgb()
    private fun accent() = if(darkPanel()) Color.rgb(168,216,195) else Palette.Teal.toArgb()
    private var backDispatcher: android.window.OnBackInvokedDispatcher? = null
    private var backCallback: android.window.OnBackInvokedCallback? = null
    private val screenEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) { persistDraft(); removeWindows() }
            if (intent?.action == Intent.ACTION_USER_PRESENT && Settings.canDrawOverlays(this@OverlayService)) showBubble()
        }
    }
    private val permissionListener = AppOpsManager.OnOpChangedListener { _, pkg ->
        if (pkg == packageName && !Settings.canDrawOverlays(this)) Handler(mainLooper).post { stopSelf() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun unlocked() = !getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private fun bounds(): Triple<Int, Int, Int> {
        val metrics = resources.displayMetrics
        val width: Int; val height: Int
        if (Build.VERSION.SDK_INT >= 30) { val area = windows.currentWindowMetrics.bounds; width = area.width(); height = area.height() }
        else { val real = android.util.DisplayMetrics(); @Suppress("DEPRECATION") windows.defaultDisplay.getRealMetrics(real); width = real.widthPixels; height = real.heightPixels }
        val top = if (Build.VERSION.SDK_INT >= 30) windows.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top else 0
        return Triple(width, height, top + dp(8))
    }
    private fun rounded(color: Int, radius: Int = 24) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun panelButton(label: String, primary: Boolean = false) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f; minWidth = 0; minHeight = 0
        setTextColor(if (primary) Color.WHITE else accent())
        background = rounded(if (primary) Palette.Teal.toArgb() else mint(), 24)
        setPadding(dp(12), 0, dp(12), 0)
    }

    override fun onCreate() {
        super.onCreate()
        windows = getSystemService(WindowManager::class.java)
        val notification = CaptureNotification.notification(this)
        if (Build.VERSION.SDK_INT >= 34) startForeground(100, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(100, notification)
        ContextCompat.registerReceiver(this, screenEvents, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        getSystemService(AppOpsManager::class.java).startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, permissionListener)
        scope.launch {
            val saved = preferences.values.first()
            overlayAppearance = saved["appearance"] ?: "system"
            positionX = saved["overlayX"]?.toFloatOrNull() ?: 1f; positionY = saved["overlayY"]?.toFloatOrNull() ?: 0.04f
            if (saved["overlay"] == "true" && Settings.canDrawOverlays(this@OverlayService)) {
                mutableRunning.value = true; getSystemService(android.app.NotificationManager::class.java).cancel(101); if (unlocked()) showBubble()
            } else stopSelf()
        }
        scope.launch {
            OverlayManager.appVisible.collect { visible ->
                if (visible) { persistDraft(); removeWindows() } else if (mutableRunning.value && unlocked()) { overlayAppearance = preferences.values.first()["appearance"] ?: "system"; showBubble() }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) stopSelf()
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun showBubble() {
        if (bubble != null || panel != null || OverlayManager.appVisible.value || !unlocked() || !Settings.canDrawOverlays(this)) return
        val (width, height, top) = bounds()
        val size = dp(56)
        val params = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            gravity = Gravity.TOP or Gravity.START
            x = if (positionX < 0.5f) dp(8) else width - size - dp(8)
            y = (top + positionY * (height - top - size - dp(32))).toInt().coerceIn(top, (height - size - dp(32)).coerceAtLeast(top))
        }
        val view = CaptureBubble(this).apply {
            setImageDrawable(OverlayGlyphDrawable(Glyph.Sparkles,Color.WHITE)); setPadding(dp(16),dp(16),dp(16),dp(16))
            background = rounded(Palette.Teal.toArgb(), 28); elevation = dp(6).toFloat(); contentDescription = "Quick capture"
            setOnClickListener { openPanel() }
        }
        var startRawX = 0f; var startRawY = 0f; var initialX = 0; var initialY = 0; var moved = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startRawX = event.rawX; startRawY = event.rawY; initialX = params.x; initialY = params.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - startRawX) > slop || abs(event.rawY - startRawY) > slop) moved = true
                    if (moved) {
                        params.x = (initialX + event.rawX - startRawX).toInt().coerceIn(dp(8), width - size - dp(8))
                        params.y = (initialY + event.rawY - startRawY).toInt().coerceIn(top, (height - size - dp(32)).coerceAtLeast(top))
                        try { windows.updateViewLayout(view, params) } catch (_: RuntimeException) { stopSelf() }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        params.x = if (params.x + size / 2 < width / 2) dp(8) else width - size - dp(8)
                        positionX = if (params.x < width / 2) 0f else 1f
                        positionY = ((params.y - top).toFloat() / (height - top - size - dp(32)).coerceAtLeast(1)).coerceIn(0f, 1f)
                        try { windows.updateViewLayout(view, params) } catch (_: RuntimeException) { stopSelf() }
                        scope.launch { preferences.set("overlayX", positionX.toString()); preferences.set("overlayY", positionY.toString()) }
                    } else target.performClick()
                }
            }
            true
        }
        try { windows.addView(view, params); bubble = view; bubbleParams = params } catch (_: RuntimeException) { stopSelf() }
    }

    private fun openPanel() {
        if (panel != null || !unlocked() || !Settings.canDrawOverlays(this)) return
        bubble?.let { try { windows.removeViewImmediate(it) } catch (_: RuntimeException) {} }; bubble = null
        val (width, height, top) = bounds()
        val panelWidth = minOf(width - dp(24), dp(360))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(12)); background = rounded(paper(),28); elevation = dp(10).toFloat() }
        root.addView(View(this).apply { background=rounded(Color.rgb(199,204,200),4) },LinearLayout.LayoutParams(dp(42),dp(3)).apply { gravity=Gravity.CENTER_HORIZONTAL; bottomMargin=dp(8) })
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply {setImageDrawable(OverlayGlyphDrawable(Glyph.Sparkles,Palette.Teal.toArgb()));setPadding(dp(10),dp(10),dp(10),dp(10));background=rounded(mint(),22)},LinearLayout.LayoutParams(dp(40),dp(40)))
        header.addView(LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(5),0,0)
            addView(TextView(this@OverlayService).apply {setText(app.planner.R.string.quick_capture_title);textSize=19f;setTypeface(resources.getFont(app.planner.R.font.dm_sans),android.graphics.Typeface.BOLD);setTextColor(ink())})
            addView(TextView(this@OverlayService).apply {setText(app.planner.R.string.quick_capture_subtitle);textSize=12f;setTextColor(secondaryInk())})
        },LinearLayout.LayoutParams(0,dp(58),1f))
        header.addView(panelButton("×").apply { contentDescription = "Close quick capture"; setOnClickListener { closePanel() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)
        var saving = false
        var sendControl: Button? = null
        val input = object : EditText(this) {
            override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK) { if (event.action == KeyEvent.ACTION_UP) closePanel(); return true }
                return super.onKeyPreIme(keyCode, event)
            }
        }.apply {
            hint = "Write anything…"; contentDescription = "Quick capture note"; textSize = 18f; minLines = 3; maxLines = 6; gravity = Gravity.TOP
            setTextColor(ink()); setHintTextColor(secondaryInk())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            typeface=resources.getFont(app.planner.R.font.dm_sans)
            filters = arrayOf(InputFilter.LengthFilter(20_000)); setPadding(dp(16), dp(16), dp(16), dp(16)); background = rounded(mint(), 18)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { preferences.saveOverlayDraft(s?.toString().orEmpty()); sendControl?.isEnabled = !saving && !s.isNullOrBlank() }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160)))
        val privateChoice = CheckBox(this).apply { setText(app.planner.R.string.quick_capture_private); contentDescription = "Keep quick capture private"; isChecked = true; setTextColor(ink()); buttonTintList = android.content.res.ColorStateList.valueOf(accent()) }
        root.addView(privateChoice, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(TextView(this).apply { setText(app.planner.R.string.quick_capture_trust); textSize = 13f; setTextColor(secondaryInk()) }, LinearLayout.LayoutParams(0, dp(56), 1f))
        val send = panelButton("↑", primary = true).apply {textSize=28f; contentDescription = "Save quick capture"; isEnabled = false }.also { sendControl = it }
        send.setOnClickListener {
            val capturedText = input.text.toString()
            if (capturedText.isBlank()) return@setOnClickListener
            val private = privateChoice.isChecked
            saving = true
            send.isEnabled = false
            scope.launch {
                try {
                    captureUseCase.capture(capturedText, private, source = "OVERLAY")
                    Toast.makeText(this@OverlayService, "Saved on this device", Toast.LENGTH_SHORT).show()
                    if (input.text.toString() == capturedText) { input.setText(""); preferences.set("overlayDraft", ""); closePanel() }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { Toast.makeText(this@OverlayService, "Couldn't save. Your draft is still here.", Toast.LENGTH_LONG).show() } finally { saving = false; send.isEnabled = input.text.isNotBlank() }
            }
        }
        footer.addView(send, LinearLayout.LayoutParams(dp(52), dp(52))); root.addView(footer)
        val scroll = ScrollView(this).apply { addView(root); isFillViewport = false }
        val params = WindowManager.LayoutParams(panelWidth, minOf(dp(380), height - top - dp(32)), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            gravity = Gravity.TOP or Gravity.START; x = (width - panelWidth) / 2; y = top + dp(8); softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        }
        try {
            windows.addView(scroll, params); panel = scroll; editor = input; panelParams = params
            if (Build.VERSION.SDK_INT >= 33) scroll.post {
                backDispatcher = scroll.findOnBackInvokedDispatcher()
                backCallback = android.window.OnBackInvokedCallback { closePanel() }
                backCallback?.let { backDispatcher?.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, it) }
            }
            scope.launch {
                val saved = preferences.values.first()
                input.setText(saved["overlayDraft"].orEmpty()); input.setSelection(input.length()); privateChoice.isChecked = CapturePrivacy.isPrivate(saved)
                privateChoice.setOnCheckedChangeListener { _, checked ->
                    if(!checked && saved["cloudConsent"]!=CapturePrivacy.CONSENT_VERSION) {
                        privateChoice.isChecked=true
                        Toast.makeText(this@OverlayService,"Allow cloud AI from Planner's capture privacy control first. This note stays private.",Toast.LENGTH_LONG).show()
                    } else scope.launch { preferences.capturePrivacy(checked) }
                }
                input.requestFocus(); input.postDelayed({ if (Build.VERSION.SDK_INT >= 30) input.windowInsetsController?.show(WindowInsets.Type.ime()) else (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0) }, 150)
            }
        } catch (_: RuntimeException) { stopSelf() }
    }

    private fun persistDraft() { val text = editor?.text?.toString() ?: return; preferences.saveOverlayDraft(text) }
    private fun closePanel() {
        if (closing || panel == null) return
        closing = true
        val text = editor?.text?.toString().orEmpty()
        scope.launch {
            try { preferences.set("overlayDraft", text) } catch (e: CancellationException) { throw e } catch (_: java.io.IOException) {
                Toast.makeText(this@OverlayService, "Draft kept for this session; device storage is unavailable.", Toast.LENGTH_LONG).show()
            } finally {
                clearBackCallback()
                panel?.let { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0); try { windows.removeViewImmediate(it) } catch (_: RuntimeException) {} }
                panel = null; editor = null; panelParams = null; closing = false; showBubble()
            }
        }
    }
    private fun removeWindows() {
        clearBackCallback()
        for (view in listOfNotNull(panel, bubble)) try { windows.removeViewImmediate(view) } catch (_: RuntimeException) {}
        panel = null; editor = null; bubble = null; panelParams = null; bubbleParams = null
    }
    private fun clearBackCallback() {
        if (Build.VERSION.SDK_INT >= 33) backCallback?.let { backDispatcher?.unregisterOnBackInvokedCallback(it) }
        backCallback = null; backDispatcher = null
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        persistDraft(); removeWindows(); if (unlocked()) showBubble()
    }
    override fun onDestroy() {
        persistDraft(); removeWindows(); mutableRunning.value = false
        getSystemService(AppOpsManager::class.java).stopWatchingMode(permissionListener)
        try { unregisterReceiver(screenEvents) } catch (_: IllegalArgumentException) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        val appContext = applicationContext
        preferences.withLatest { saved -> CaptureNotification.update(appContext, saved["notification"] == "true") }
        scope.cancel(); super.onDestroy()
    }
    companion object { private val mutableRunning = MutableStateFlow(false); val running = mutableRunning.asStateFlow() }
}
