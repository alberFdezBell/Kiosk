package com.kiosk.browser

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.kiosk.browser.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private val handler = Handler(Looper.getMainLooper())

    private var dimTimeoutSec: Int = 10
    private var offTimeoutSec: Int = 60
    private var showClock: Boolean = true
    private var dvdMoveEnabled: Boolean = true
    private var debugEnabled: Boolean = false
    private var clockSizeSp: Int = 130

    private enum class ScreenState {
        NORMAL, DIM, OFF
    }

    private var currentState = ScreenState.NORMAL

    // Per-Minute Pixel Shift Tracking
    private var lastMinute: Int = -1

    private val runnableDim = Runnable {
        setScreenState(ScreenState.DIM)
    }

    private val runnableOff = Runnable {
        setScreenState(ScreenState.OFF)
    }

    private val runnableClockTick = object : Runnable {
        override fun run() {
            if (currentState == ScreenState.OFF && showClock) {
                val currentMin = Calendar.getInstance().get(Calendar.MINUTE)
                if (currentMin != lastMinute) {
                    lastMinute = currentMin
                    updateClockDisplay()
                    if (dvdMoveEnabled) {
                        shiftClockPositionPerMinute()
                    }
                } else {
                    updateClockDisplay()
                }
                updateDebugInfo()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "kiosk_prefs"
        private const val KEY_URL = "pref_url"
        private const val KEY_DIM_SEC = "pref_dim_sec"
        private const val KEY_OFF_SEC = "pref_off_sec"
        private const val KEY_SHOW_CLOCK = "pref_show_clock"
        private const val KEY_DVD_MOVE = "pref_dvd_move"
        private const val KEY_DEBUG = "pref_debug"
        private const val KEY_CLOCK_SIZE = "pref_clock_size"

        private const val DEFAULT_URL = "https://pueblo.aferbel.es"
        private const val DEFAULT_DIM_SEC = 10
        private const val DEFAULT_OFF_SEC = 60
        private const val DEFAULT_SHOW_CLOCK = true
        private const val DEFAULT_DVD_MOVE = true
        private const val DEFAULT_DEBUG = false
        private const val DEFAULT_CLOCK_SIZE = 130
        private const val MIN_CLOCK_SIZE = 40
        private const val MAX_CLOCK_SIZE = 220
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on continuously for kiosk operation & touch digitizer readiness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUI()

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupWebView()
        setupDrawerAndSettings()
        loadSettings()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            webViewClient = object : WebViewClient() {}
            webChromeClient = WebChromeClient()
        }
    }

    private fun setupDrawerAndSettings() {
        binding.btnSave.setOnClickListener {
            saveAndApplySettings()
        }

        binding.btnClockSizeMinus.setOnClickListener {
            adjustClockSize(-10)
        }

        binding.btnClockSizePlus.setOnClickListener {
            adjustClockSize(10)
        }
    }

    private fun adjustClockSize(delta: Int) {
        val newSize = (clockSizeSp + delta).coerceIn(MIN_CLOCK_SIZE, MAX_CLOCK_SIZE)
        
        // Protect from overflowing screen bounds
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val estimatedClockWidthDp = newSize * 3.5f // Approximate character width factor

        if (delta > 0 && estimatedClockWidthDp > (screenWidthDp - 20)) {
            Toast.makeText(this, "Límite máximo alcanzado para esta pantalla", Toast.LENGTH_SHORT).show()
            return
        }

        clockSizeSp = newSize
        updateClockSizeUI()
        showLiveClockPreview()
    }

    private fun updateClockSizeUI() {
        binding.tvClockSizeValue.text = "$clockSizeSp sp"
        binding.tvClockTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, clockSizeSp.toFloat())
        
        val dateSizeSp = (clockSizeSp * 0.23f).coerceAtLeast(14f)
        binding.tvClockDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, dateSizeSp)
    }

    private fun showLiveClockPreview() {
        updateClockDisplay()
        binding.clockContainer.visibility = View.VISIBLE
        binding.offOverlay.alpha = 0.90f
        binding.offOverlay.visibility = View.VISIBLE

        binding.offOverlay.post {
            initClockPosition()
        }
    }

    private fun loadSettings() {
        val url = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        dimTimeoutSec = prefs.getInt(KEY_DIM_SEC, DEFAULT_DIM_SEC)
        offTimeoutSec = prefs.getInt(KEY_OFF_SEC, DEFAULT_OFF_SEC)
        showClock = prefs.getBoolean(KEY_SHOW_CLOCK, DEFAULT_SHOW_CLOCK)
        dvdMoveEnabled = prefs.getBoolean(KEY_DVD_MOVE, DEFAULT_DVD_MOVE)
        debugEnabled = prefs.getBoolean(KEY_DEBUG, DEFAULT_DEBUG)
        clockSizeSp = prefs.getInt(KEY_CLOCK_SIZE, DEFAULT_CLOCK_SIZE)

        binding.etUrl.setText(url)
        binding.etDimTime.setText(dimTimeoutSec.toString())
        binding.etOffTime.setText(offTimeoutSec.toString())
        binding.cbShowClock.isChecked = showClock
        binding.cbDvdMove.isChecked = dvdMoveEnabled
        binding.cbDebug.isChecked = debugEnabled

        binding.tvDebugOverlay.visibility = if (debugEnabled) View.VISIBLE else View.GONE
        updateClockSizeUI()

        loadUrl(url)
        resetIdleTimers()
    }

    private fun saveAndApplySettings() {
        val urlInput = binding.etUrl.text.toString().trim()
        val dimInput = binding.etDimTime.text.toString().toIntOrNull()
        val offInput = binding.etOffTime.text.toString().toIntOrNull()

        if (urlInput.isEmpty()) {
            Toast.makeText(this, R.string.msg_invalid_url, Toast.LENGTH_SHORT).show()
            return
        }

        if (dimInput == null || dimInput < 0 || offInput == null || offInput < 0) {
            Toast.makeText(this, R.string.msg_invalid_time, Toast.LENGTH_SHORT).show()
            return
        }

        var formattedUrl = urlInput
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "https://$formattedUrl"
        }

        dimTimeoutSec = dimInput
        offTimeoutSec = offInput
        showClock = binding.cbShowClock.isChecked
        dvdMoveEnabled = binding.cbDvdMove.isChecked
        debugEnabled = binding.cbDebug.isChecked

        binding.tvDebugOverlay.visibility = if (debugEnabled) View.VISIBLE else View.GONE

        prefs.edit()
            .putString(KEY_URL, formattedUrl)
            .putInt(KEY_DIM_SEC, dimTimeoutSec)
            .putInt(KEY_OFF_SEC, offTimeoutSec)
            .putBoolean(KEY_SHOW_CLOCK, showClock)
            .putBoolean(KEY_DVD_MOVE, dvdMoveEnabled)
            .putBoolean(KEY_DEBUG, debugEnabled)
            .putInt(KEY_CLOCK_SIZE, clockSizeSp)
            .apply()

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        Toast.makeText(this, R.string.msg_saved, Toast.LENGTH_SHORT).show()

        loadUrl(formattedUrl)
        wakeScreen()
    }

    private fun loadUrl(url: String) {
        binding.webView.loadUrl(url)
    }

    private fun resetIdleTimers() {
        handler.removeCallbacks(runnableDim)
        handler.removeCallbacks(runnableOff)
        handler.removeCallbacks(runnableClockTick)

        if (dimTimeoutSec > 0) {
            handler.postDelayed(runnableDim, dimTimeoutSec * 1000L)
        }

        if (offTimeoutSec > 0) {
            handler.postDelayed(runnableOff, offTimeoutSec * 1000L)
        }
    }

    private fun setScreenState(state: ScreenState) {
        currentState = state
        val lp = window.attributes

        when (state) {
            ScreenState.NORMAL -> {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                binding.dimOverlay.visibility = View.GONE
                binding.offOverlay.visibility = View.GONE
                handler.removeCallbacks(runnableClockTick)
            }
            ScreenState.DIM -> {
                lp.screenBrightness = 0.05f
                binding.dimOverlay.visibility = View.VISIBLE
                binding.offOverlay.visibility = View.GONE
                handler.removeCallbacks(runnableClockTick)
            }
            ScreenState.OFF -> {
                binding.dimOverlay.visibility = View.GONE

                if (showClock) {
                    // Low hardware brightness (2%) to eliminate backlight bleed on LCDs while keeping clock legible
                    lp.screenBrightness = 0.02f
                    binding.clockContainer.visibility = View.VISIBLE
                    updateClockDisplay()

                    binding.offOverlay.post {
                        initClockPosition()
                    }

                    lastMinute = Calendar.getInstance().get(Calendar.MINUTE)
                    handler.post(runnableClockTick)
                } else {
                    lp.screenBrightness = 0.00f
                    binding.clockContainer.visibility = View.GONE
                }

                binding.offOverlay.alpha = 0f
                binding.offOverlay.visibility = View.VISIBLE
                binding.offOverlay.animate().alpha(1f).setDuration(400L).start()
            }
        }
        window.attributes = lp
        updateDebugInfo()
    }

    private fun updateClockDisplay() {
        val locale = Locale("es", "ES")
        val timeStr = SimpleDateFormat("HH:mm", locale).format(Date())
        
        val dayOfWeek = SimpleDateFormat("EEEE", locale).format(Date()).replaceFirstChar { it.uppercase() }
        val dayOfMonth = SimpleDateFormat("d", locale).format(Date())
        val month = SimpleDateFormat("MMMM", locale).format(Date()).replaceFirstChar { it.uppercase() }
        val dateStr = "$dayOfWeek, $dayOfMonth De $month"

        binding.tvClockTime.text = timeStr
        binding.tvClockDate.text = dateStr
    }

    private fun initClockPosition() {
        val parentW = binding.offOverlay.width
        val parentH = binding.offOverlay.height
        val clockW = binding.clockContainer.width
        val clockH = binding.clockContainer.height

        if (parentW > 0 && parentH > 0 && clockW > 0 && clockH > 0) {
            val params = binding.clockContainer.layoutParams as FrameLayout.LayoutParams
            params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            binding.clockContainer.layoutParams = params

            val centerX = ((parentW - clockW) / 2).toFloat().coerceAtLeast(0f)
            val centerY = ((parentH - clockH) / 2).toFloat().coerceAtLeast(0f)

            binding.clockContainer.x = centerX
            binding.clockContainer.y = centerY
            updateDebugInfo()
        }
    }

    private fun shiftClockPositionPerMinute() {
        val parentW = binding.offOverlay.width
        val parentH = binding.offOverlay.height
        val clockW = binding.clockContainer.width
        val clockH = binding.clockContainer.height

        if (parentW <= 0 || parentH <= 0 || clockW <= 0 || clockH <= 0) return

        val centerX = ((parentW - clockW) / 2).toFloat()
        val centerY = ((parentH - clockH) / 2).toFloat()

        // Shift by a subtle random offset (between -40px and +40px) around center
        val maxOffset = 40f
        val maxX = (parentW - clockW).toFloat().coerceAtLeast(0f)
        val maxY = (parentH - clockH).toFloat().coerceAtLeast(0f)

        val targetX = (centerX + Random.nextFloat() * (maxOffset * 2) - maxOffset).coerceIn(0f, maxX)
        val targetY = (centerY + Random.nextFloat() * (maxOffset * 2) - maxOffset).coerceIn(0f, maxY)

        binding.clockContainer.animate()
            .x(targetX)
            .y(targetY)
            .setDuration(600L)
            .start()

        updateDebugInfo()
    }

    private fun updateDebugInfo() {
        if (debugEnabled) {
            val posX = binding.clockContainer.x.toInt()
            val posY = binding.clockContainer.y.toInt()
            val stateName = currentState.name
            binding.tvDebugOverlay.text = "[DEBUG]\nEstado: $stateName\nTamaño: ${clockSizeSp}sp\nReloj X: ${posX}px, Y: ${posY}px\nMinuto: $lastMinute"
        }
    }

    private fun wakeScreen() {
        setScreenState(ScreenState.NORMAL)
        resetIdleTimers()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            if (currentState != ScreenState.NORMAL) {
                if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_DOWN) {
                    wakeScreen()
                    // Consume touch so tap only wakes screen without clicking web elements
                    return true
                }
            } else {
                resetIdleTimers()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        wakeScreen()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(runnableDim)
        handler.removeCallbacks(runnableOff)
        handler.removeCallbacks(runnableClockTick)
    }
}
