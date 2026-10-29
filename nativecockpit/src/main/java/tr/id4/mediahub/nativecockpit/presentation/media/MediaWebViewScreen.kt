package tr.id4.mediahub.nativecockpit.presentation.media

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebChromeClient
import android.util.Log
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

/** Web browsing is NOT a DRM bypass. Only verified parked playback is permitted. */
enum class MediaSite(val title: String, val url: String) {
    NETFLIX("Netflix", "https://www.netflix.com"),
    DISNEY("Disney+", "https://www.disneyplus.com"),
    PRIME("Prime Video", "https://www.primevideo.com"),
    YOUTUBE("YouTube", "https://m.youtube.com")
}

sealed interface MediaPageUiState {
    data object Empty : MediaPageUiState
    data object Loading : MediaPageUiState
    data object Ready : MediaPageUiState
    data class Error(val message: String) : MediaPageUiState
}

class MediaWebViewViewModel : ViewModel() {
    private val _state = MutableStateFlow<MediaPageUiState>(MediaPageUiState.Empty)
    val uiState: StateFlow<MediaPageUiState> = _state.asStateFlow()
    fun start() { _state.value = MediaPageUiState.Loading }
    fun ready() { if (_state.value == MediaPageUiState.Loading) _state.value = MediaPageUiState.Ready }
    fun error(message: String) { _state.value = MediaPageUiState.Error(message.take(160)) }
    fun clear() { _state.value = MediaPageUiState.Empty }
}

private fun safePage(url: String?, site: MediaSite): Boolean = runCatching {
    val uri = Uri.parse(url)
    val host = uri.host?.lowercase().orEmpty()
    // Explicit first-party login hosts; never allow external intent schemes.
    val permitted = when (site) {
        MediaSite.NETFLIX -> setOf("netflix.com")
        MediaSite.DISNEY -> setOf("disneyplus.com", "disney.com")
        MediaSite.PRIME -> setOf(
            "primevideo.com", "amazon.com", "amazon.de",
            "amazon.co.uk", "amazon.com.tr", "amazonvideo.com"
        )
        MediaSite.YOUTUBE -> setOf("youtube.com", "google.com")
    }
    uri.scheme.equals("https", ignoreCase = true) &&
        permitted.any { host == it || host.endsWith(".$it") }
}.getOrDefault(false)

/**
 * Existing dark cockpit remains untouched. This embedded web surface is
 * activated only after an authoritative parked signal; zero GPS speed is
 * never treated as parked.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MediaWebViewScreen(
    service: MediaSite,
    verifiedParked: Boolean,
    onClose: () -> Unit,
    model: MediaWebViewViewModel = viewModel(key = "web-" + service.name)
) {
    var view by remember(service) { mutableStateOf<WebView?>(null) }
    var exitFullscreen by remember(service) { mutableStateOf<(() -> Unit)?>(null) }
    val page by model.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(view, lifecycleOwner, verifiedParked) {
        val target = view
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> target?.onPause()
                Lifecycle.Event.ON_RESUME -> if (verifiedParked) target?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            target?.onPause() // Never retain background video playback.
        }
    }
    BackHandler(enabled = verifiedParked) {
        val current = view
        when {
            exitFullscreen != null -> exitFullscreen?.invoke()
            current?.canGoBack() == true -> current.goBack()
            else -> onClose()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(service.title, color = Color.White)
            TextButton(onClick = onClose) { Text("Kapat") }
        }

        if (!verifiedParked) {
            Text("Video yalnızca Park konumu güvenilir şekilde doğrulandığında açılır.",
                color = Color(0xFFFFBD78), modifier = Modifier.padding(16.dp))
            return@Column
        }

        if (service != MediaSite.YOUTUBE) {
            Text("DRM nedeniyle oynatma desteklenmeyebilir. Siyah ekran varsa resmi uygulamayı kullanın.",
                color = Color(0xFFFFBD78),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp))
        }

        when (val state = page) {
            MediaPageUiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is MediaPageUiState.Error -> Text(state.message, color = Color(0xFFFFB17C),
                modifier = Modifier.padding(12.dp))
            MediaPageUiState.Ready, MediaPageUiState.Empty -> Unit
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                FrameLayout(context).apply {
                    val host = this
                    val browser = WebView(context).apply {
                    val web = this
                    // No JavaScript bridge or WebChrome debugger is exposed.
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    // Autoplay is only enabled within the verified parked gate.
                    settings.mediaPlaybackRequiresUserGesture = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        settings.safeBrowsingEnabled = true
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webChromeClient = object : WebChromeClient() {
                        private var custom: View? = null
                        private var completion: CustomViewCallback? = null

                        override fun onShowCustomView(customView: View, callback: CustomViewCallback) {
                            if (!verifiedParked || custom != null) {
                                callback.onCustomViewHidden()
                                return
                            }
                            custom = customView
                            completion = callback
                            host.addView(customView, FrameLayout.LayoutParams(-1, -1))
                            web.visibility = View.GONE
                            exitFullscreen = { onHideCustomView() }
                            Log.i("EYA-MEDIA-UAT", "WebView fullscreen entered")
                        }

                        override fun onHideCustomView() {
                            custom?.let { host.removeView(it) }
                            custom = null
                            completion?.onCustomViewHidden()
                            completion = null
                            web.visibility = View.VISIBLE
                            exitFullscreen = null
                            Log.i("EYA-MEDIA-UAT", "WebView fullscreen exited")
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            webView: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            if (!request.isForMainFrame) return false
                            if (safePage(request.url.toString(), service)) return false
                            model.error("Güvensiz veya harici bağlantı engellendi")
                            return true
                        }

                        override fun onPageStarted(webView: WebView, url: String?, favicon: Bitmap?) {
                            model.start()
                            Log.d("EYA-MEDIA-UAT", "pageStarted service=" + service.name)
                        }

                        override fun onPageFinished(webView: WebView, url: String?) {
                            model.ready()
                            Log.d("EYA-MEDIA-UAT", "pageFinished service=" + service.name)
                        }

                        override fun onReceivedError(
                            webView: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                model.error("Sayfa yüklenemedi. Bağlantıyı kontrol edin.")
                                Log.e("EYA-MEDIA-UAT", "main-frame error code=" + error.errorCode)
                            }
                        }

                        override fun onReceivedSslError(
                            webView: WebView, handler: SslErrorHandler, error: SslError
                        ) {
                            handler.cancel() // Never bypass certificate errors.
                            model.error("Güvenli bağlantı doğrulanamadı")
                        }

                        override fun onReceivedHttpError(
                            webView: WebView,
                            request: WebResourceRequest,
                            response: android.webkit.WebResourceResponse
                        ) {
                            if (request.isForMainFrame && response.statusCode >= 400)
                                model.error("Site HTTP ${response.statusCode} hatası verdi")
                        }
                    }
                    view = this
                    if (safePage(service.url, service)) loadUrl(service.url)
                    }
                    host.addView(browser, FrameLayout.LayoutParams(-1, -1))
                }
            },
            update = { _ -> Unit }, // Do not reload on recomposition.
            onRelease = { host ->
                exitFullscreen?.invoke()
                exitFullscreen = null
                view?.let { released ->
                    released.stopLoading()
                    released.loadUrl("about:blank")
                    released.webChromeClient = null
                    released.webViewClient = WebViewClient()
                    (released.parent as? ViewGroup)?.removeView(released)
                    released.removeAllViews()
                    released.destroy()
                }
                host.removeAllViews()
                view = null
                model.clear()
                Log.d("EYA-MEDIA-UAT", "WebView released")
            }
        )
    }
}
