package com.kiosk.browser

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.kiosk.browser.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private val handler = Handler(Looper.getMainLooper())

    private var dimTimeoutSec: Int = 10
    private var offTimeoutSec: Int = 60

    private enum class ScreenState {
        NORMAL, DIM, OFF
    }

    private var currentState = ScreenState.NORMAL

    private val runnableDim = Runnable {
        setScreenState(ScreenState.DIM)
    }

    private val runnableOff = Runnable {
        setScreenState(ScreenState.OFF)
    }

    companion object {
        private const val PREFS_NAME = "kiosk_prefs"
        private const val KEY_URL = "pref_url"
        private const val KEY_DIM_SEC = "pref_dim_sec"
        private const val KEY_OFF_SEC = "pref_off_sec"
        private const val KEY_FIRST_LAUNCH = "pref_first_launch"

        private const val DEFAULT_URL = "https://google.com"
        private const val DEFAULT_DIM_SEC = 10
        private const val DEFAULT_OFF_SEC = 60
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on continuously for kiosk operation & touch digitizer readiness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupWebView()
        setupDrawerAndSettings()
        loadSettings()
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
        binding.btnOpenDrawer.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.btnSave.setOnClickListener {
            saveAndApplySettings()
        }

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                binding.btnOpenDrawer.visibility = View.GONE
            }

            override fun onDrawerClosed(drawerView: View) {
                binding.btnOpenDrawer.visibility = View.VISIBLE
            }
        })
    }

    private fun loadSettings() {
        val url = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        dimTimeoutSec = prefs.getInt(KEY_DIM_SEC, DEFAULT_DIM_SEC)
        offTimeoutSec = prefs.getInt(KEY_OFF_SEC, DEFAULT_OFF_SEC)
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

        binding.etUrl.setText(url)
        binding.etDimTime.setText(dimTimeoutSec.toString())
        binding.etOffTime.setText(offTimeoutSec.toString())

        loadUrl(url)
        resetIdleTimers()

        if (isFirstLaunch) {
            binding.drawerLayout.openDrawer(GravityCompat.START)
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }
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

        prefs.edit()
            .putString(KEY_URL, formattedUrl)
            .putInt(KEY_DIM_SEC, dimTimeoutSec)
            .putInt(KEY_OFF_SEC, offTimeoutSec)
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
            }
            ScreenState.DIM -> {
                lp.screenBrightness = 0.05f
                binding.dimOverlay.visibility = View.VISIBLE
                binding.offOverlay.visibility = View.GONE
            }
            ScreenState.OFF -> {
                lp.screenBrightness = 0.00f
                binding.dimOverlay.visibility = View.GONE
                binding.offOverlay.visibility = View.VISIBLE
            }
        }
        window.attributes = lp
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
        wakeScreen()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(runnableDim)
        handler.removeCallbacks(runnableOff)
    }
}
