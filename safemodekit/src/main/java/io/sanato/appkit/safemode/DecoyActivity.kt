package io.sanato.appkit.safemode

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * 安全模式伪装页：加载 [SafeModeConfig.decoyUrl]。暗号见 [HoldToUnlockDetector]，通过后再验证身份。
 *
 * 宿主 manifest 的伪装入口 alias 用全名 `io.sanato.appkit.safemode.DecoyActivity` 指向这里；
 * 除此之外宿主不应直接启动或继承它。
 */
class DecoyActivity : AppCompatActivity() {
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var web: WebView
    private lateinit var detector: HoldToUnlockDetector
    private var scheduledHoldStart: Long? = null
    private var authenticating = false

    private val hapticRunnable =
        Runnable {
            if (detector.isHoldSatisfied(SystemClock.uptimeMillis())) {
                val feedback =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.LONG_PRESS
                    }
                refresh.performHapticFeedback(feedback)
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = SafeModeGate.config
        if (config == null) {
            // 宿主漏调 SafeMode.install()：没有首页可去，也没有网址可加载，直接退出而不是崩溃
            finish()
            return
        }
        if (!SafeModePrefs.isEnabled(this)) {
            openHome(config)
            return
        }

        web = WebView(this)
        refresh =
            SwipeRefreshLayout(this).apply {
                setBackgroundColor(Color.WHITE)
                addView(
                    web,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        setContentView(refresh)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(refresh) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // SwipeRefreshLayout 默认刷新触发点是指示器移动 64dp，手指位移 = 64dp / DRAG_RATE(0.5) = 128dp
        detector = HoldToUnlockDetector(128f * resources.displayMetrics.density, SafeModePrefs.holdMs(this))

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    // baiduboxapp:// intent:// 等唤起 App 的链接静默拦截
                    return scheme != "http" && scheme != "https"
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String?,
                ) {
                    refresh.isRefreshing = false
                }
            }
        refresh.setOnRefreshListener { web.reload() }
        refresh.setOnChildScrollUpCallback { _, _ -> web.canScrollVertically(-1) }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (web.canGoBack()) {
                        web.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )

        if (savedInstanceState == null) web.loadUrl(config.decoyUrl) else web.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::web.isInitialized) web.saveState(outState)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::detector.isInitialized) trackGesture(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun trackGesture(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                detector.onDown(!web.canScrollVertically(-1), ev.y, ev.eventTime)
            }

            MotionEvent.ACTION_MOVE -> {
                detector.onMove(ev.y, ev.eventTime)
            }

            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                detector.onCancel()
            }

            MotionEvent.ACTION_UP -> {
                if (detector.onUp(ev.eventTime)) requestUnlock()
            }
        }
        syncHapticTimer()
    }

    private fun syncHapticTimer() {
        val start = detector.holdStartTime
        if (start == scheduledHoldStart) return
        refresh.removeCallbacks(hapticRunnable)
        scheduledHoldStart = start
        if (start != null) {
            refresh.postDelayed(hapticRunnable, start + detector.holdMs - SystemClock.uptimeMillis())
        }
    }

    private fun requestUnlock() {
        if (authenticating) return
        if (!SafeModeAuthenticator.canAuthenticate(this)) {
            // 用户删掉了所有生物识别和锁屏密码：只凭暗号放行，避免锁死
            onUnlocked()
            return
        }
        authenticating = true
        SafeModeAuthenticator.authenticate(
            this,
            onSuccess = {
                authenticating = false
                onUnlocked()
            },
            onFailOrCancel = { authenticating = false },
        )
    }

    private fun onUnlocked() {
        SafeModeGate.unlock()
        SafeModeGate.config?.let(::openHome)
    }

    private fun openHome(config: SafeModeConfig) {
        startActivity(Intent(this, config.homeActivity))
        finish()
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            refresh.removeCallbacks(hapticRunnable)
            web.destroy()
        }
        super.onDestroy()
    }
}
