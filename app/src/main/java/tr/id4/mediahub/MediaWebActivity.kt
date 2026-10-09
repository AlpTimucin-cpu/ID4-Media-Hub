package tr.id4.mediahub

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import android.webkit.WebSettings
import android.widget.Toast
import android.net.Uri

/**
 * In-app HTTPS web media surface for a phone. This is NOT an Android Auto video
 * activity and does not embed other Android APKs, Netflix DRM, or bypass car locks.
 */
class MediaWebActivity : Activity() {
    companion object {
        const val EXTRA_URL = "web_media_url"
        const val YOUTUBE_URL = "https://m.youtube.com"
        const val VIDEO_DEMO_URL = "https://www.youtube.com/embed/aqz-KE-bpKQ"
    }
    private lateinit var browser: WebView
    private lateinit var urlInput: EditText
    private lateinit var progress: ProgressBar
    private lateinit var title: TextView
    private lateinit var mainLayout: LinearLayout
    private lateinit var fullViewLayer: FrameLayout
    private lateinit var toolbar: LinearLayout
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var oldOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(bg: Int) = GradientDrawable().apply {
        setColor(bg)
        cornerRadius = dp(12).toFloat()
    }
    private fun btn(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = shape(0xff244d6b.toInt())
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xff0a1323.toInt()
        window.navigationBarColor = 0xff0a1323.toInt()
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff0b1526.toInt())
        }
        fullViewLayer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        val frame = FrameLayout(this)
        setContentView(frame)
        frame.addView(mainLayout, FrameLayout.LayoutParams(-1, -1))
        frame.addView(fullViewLayer, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(8))
        }
        title = TextView(this).apply {
            text = "WEB MEDYA"
            textSize = 18f
            setTextColor(0xff78efe3.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(btn("YouTube") { openUrl(YOUTUBE_URL) },
            LinearLayout.LayoutParams(dp(101), dp(48)))
        header.addView(btn("TV'ye dön") { finish() },
            LinearLayout.LayoutParams(dp(108), dp(48)))
        mainLayout.addView(header)

        urlInput = EditText(this).apply {
            setSingleLine(true)
            hint = "https://... video bağlantısı"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(0xff91a7c0.toInt())
            background = shape(0xff1e344e.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnEditorActionListener { _, _, _ ->
                openUrl(text.toString())
                true
            }
        }
        val urlRow= LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8),0,dp(8),0)
        }
        urlRow.addView(urlInput, LinearLayout.LayoutParams(0, dp(50), 1f))
        urlRow.addView(btn("Aç") { openUrl(urlInput.text.toString()) },
            LinearLayout.LayoutParams(dp(63),dp(50)))
        mainLayout.addView(urlRow)

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        navigation.addView(btn("‹") { if (browser.canGoBack()) browser.goBack() },
            LinearLayout.LayoutParams(0,dp(44),1f))
        navigation.addView(btn("›") { if (browser.canGoForward()) browser.goForward() },
            LinearLayout.LayoutParams(0,dp(44),1f))
        navigation.addView(btn("⟳") { browser.reload() },
            LinearLayout.LayoutParams(0,dp(44),1f))
        navigation.addView(btn("⊞ Tam ekran") { toggleWebFullscreen() },
            LinearLayout.LayoutParams(0,dp(44),2f))
        mainLayout.addView(navigation)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        mainLayout.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        browser = WebView(this)
        val config = browser.settings
        config.javaScriptEnabled = true
        config.domStorageEnabled = true
        config.mediaPlaybackRequiresUserGesture = true
        config.allowFileAccess = false
        config.allowContentAccess = false
        config.javaScriptCanOpenWindowsAutomatically = false
        config.setSupportMultipleWindows(false)
        config.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        config.setSupportZoom(true)
        config.builtInZoomControls = true
        config.displayZoomControls = false
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            config.safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser,false)
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                return !allowed(request.url.toString())
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null && allowed(url)) urlInput.setText(url)
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                Toast.makeText(this@MediaWebActivity, "Sertifika hatası: sayfa engellendi", Toast.LENGTH_LONG).show()
            }
        }
        browser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.INVISIBLE else View.VISIBLE
            }
            override fun onReceivedTitle(view: WebView?, pageTitle: String?) {
                if (!pageTitle.isNullOrBlank()) title.text = pageTitle.take(26)
            }
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || callback == null) return
                if (fullscreenView != null) { callback.onCustomViewHidden(); return }
                fullscreenView = view
                fullscreenCallback = callback
                fullViewLayer.addView(view, FrameLayout.LayoutParams(-1, -1))
                fullViewLayer.visibility = View.VISIBLE
                mainLayout.visibility = View.GONE
                oldOrientation = requestedOrientation
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                fullViewLayer.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
            override fun onHideCustomView() {
                closeCustomFullscreen()
            }
        }
        mainLayout.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        val caption = TextView(this).apply {
            text = "Telefon oynatıcısı • Yalnızca HTTPS içerik • Araç ekranına video aktarımı yok"
            setTextColor(0xff96acc6.toInt())
            textSize = 11f
            setPadding(dp(12), dp(7), dp(12), dp(9))
        }
        mainLayout.addView(caption)
        val incoming = intent.getStringExtra(EXTRA_URL)?.takeIf { allowed(it) } ?: YOUTUBE_URL
        openUrl(incoming)
    }

    private fun allowed(raw: String): Boolean = try {
        val value = Uri.parse(raw.trim())
        value.scheme.equals("https", ignoreCase = true) &&
            !value.host.isNullOrBlank() &&
            value.userInfoSafe() &&
            value.host!!.length <= 250
    } catch (_: Exception) { false }

    private fun Uri.userInfoSafe(): Boolean {
        // Reject credentials in input to avoid displaying tokens in page navigation.
        return encodedAuthority?.contains("@") != true
    }

    private fun openUrl(value: String) {
        val address = value.trim()
        if (!allowed(address)) {
            AlertDialog.Builder(this).setTitle("HTTPS bağlantısı gerekli")
                .setMessage("Yalnızca doğrudan HTTPS bağlantıları açılır. Dosya ve özel uygulama bağlantıları desteklenmez.")
                .setPositiveButton("Tamam", null).show()
            return
        }
        if (!::browser.isInitialized) return
        browser.loadUrl(address)
    }

    private fun closeCustomFullscreen() {
        val custom = fullscreenView ?: return
        (custom.parent as? ViewGroup)?.removeView(custom)
        fullscreenView = null
        fullViewLayer.visibility = View.GONE
        mainLayout.visibility = View.VISIBLE
        requestedOrientation = oldOrientation
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
    }

    private fun toggleWebFullscreen() {
        val flags = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val decor = window.decorView
        decor.systemUiVisibility = if ((decor.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN) != 0) 0 else flags
    }

    @Deprecated("Use OnBackPressedDispatcher in a future AndroidX migration")
    override fun onBackPressed() {
        when {
            fullscreenView != null -> closeCustomFullscreen()
            browser.canGoBack() -> browser.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        closeCustomFullscreen()
        browser.stopLoading()
        browser.webChromeClient = null
        browser.webViewClient = WebViewClient()
        browser.destroy()
        super.onDestroy()
    }
}
