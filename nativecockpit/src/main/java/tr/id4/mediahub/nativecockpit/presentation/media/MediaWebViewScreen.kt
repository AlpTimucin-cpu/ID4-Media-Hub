package tr.id4.mediahub.nativecockpit.presentation.media

import android.graphics.Bitmap
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MediaWebsite(val title: String, val homepage: String) {
    NETFLIX("Netflix", "https://www.netflix.com"),
    DISNEY("Disney+", "https://www.disneyplus.com"),
    PRIME("Prime Video", "https://www.primevideo.com"),
    YOUTUBE("YouTube", "https://m.youtube.com")
}

sealed interface MediaPageState {
    data object Loading : MediaPageState
    data object Success : MediaPageState
    data class Error(val message: String, val drmPossible: Boolean = false) : MediaPageState
}

class MediaBrowserViewModel : ViewModel() {
    private val mutable = MutableStateFlow<MediaPageState>(MediaPageState.Loading)
    val uiState: StateFlow<MediaPageState> = mutable.asStateFlow()
    fun started() { mutable.value = MediaPageState.Loading }
    fun finished() { mutable.value = MediaPageState.Success }
    fun failed(message: String, drm: Boolean = false) {
        mutable.value = MediaPageState.Error(message, drm)
    }
}

/**
 * An app-owned browser, not an embedded third-party Android application.
 * Only instantiate while a trusted vehicle Park state is available.
 * Widevine/DRM availability is controlled by the content provider, not this app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaWebViewScreen(
    site: MediaWebsite,
    viewModel: MediaBrowserViewModel,
    verifiedParked: Boolean,
    onFullScreenVideo: (Boolean) -> Unit,
    onExternalFallback: (MediaWebsite) -> Unit,
    onClose: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var fullscreen by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val ink = Color(0xFF0A0F1A)
    val white = Color.White

    fun exitFullScreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        fullscreen = null
        onFullScreenVideo(false)
    }

    BackHandler(fullscreen != null) { exitFullScreen() }
    DisposableEffect(webView) {
        onDispose {
            // Release renderer and Activity references on exit/configuration changes.
            webView?.stopLoading()
            webView?.onPause()
            webView?.webChromeClient = null
            webView?.webViewClient = WebViewClient()
            (webView?.parent as? ViewGroup)?.removeView(webView)
            webView?.destroy()
        }
    }

    Column(Modifier.fillMaxSize().background(ink)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(site.title, color = white, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onClose) { Text("Kapat", color = white) }
        }
        if (!verifiedParked) {
            Text("Video için doğrulanmış Park vitesi gerekli.",
                color = Color(0xFFFFCE73))
            return@Column
        }
        Text("DRM korumalı yayınlar WebView'de desteklenmeyebilir.",
            color = Color(0xFFABC5D5), style = MaterialTheme.typography.bodySmall)
        when (val current = state) {
            MediaPageState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is MediaPageState.Error -> {
                Text(current.message, color = Color(0xFFFFB17C))
                if (current.drmPossible) {
                    TextButton(onClick = { onExternalFallback(site) }) {
                        Text("Bu içerik için harici uygulama kullanın")
                    }
                }
            }
            MediaPageState.Success -> Unit
        }
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        // NEVER_ALLOW blocks HTTPS-to-HTTP injection.
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                                viewModel.started()
                            }
                            override fun onPageFinished(v: WebView, url: String?) {
                                if (viewModel.uiState.value is MediaPageState.Loading) {
                                    viewModel.finished()
                                }
                            }
                            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                                // Reject javascript:, intent:, file:, cleartext and custom scheme navigation.
                                return request.url.scheme != "https"
                            }
                            override fun onReceivedError(
                                v: WebView, request: WebResourceRequest, error: WebResourceError
                            ) {
                                if (request.isForMainFrame) viewModel.failed("Bağlantı Hatası: Sayfa açılamadı")
                            }
                            override fun onReceivedHttpError(
                                v: WebView, request: WebResourceRequest, response: WebResourceResponse
                            ) {
                                if (request.isForMainFrame && response.statusCode >= 400)
                                    viewModel.failed(
                                        "Servis sayfayı açamadı (HTTP ${response.statusCode})",
                                        response.statusCode in listOf(403, 451)
                                    )
                            }
                            override fun onReceivedSslError(v: WebView, handler: SslErrorHandler, error: SslError) {
                                handler.cancel() // Never bypass TLS failures.
                                viewModel.failed("Güvenli bağlantı doğrulanamadı")
                            }
                            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                                viewModel.failed("Medya tarayıcısı kapandı. Yeniden açın.")
                                v.post { (v.parent as? ViewGroup)?.removeView(v); v.destroy() }
                                return true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(
                                view: View, callback: CustomViewCallback
                            ) {
                                fullscreen = view
                                fullscreenCallback = callback
                                onFullScreenVideo(true)
                            }
                            override fun onHideCustomView() { exitFullScreen() }
                        }
                        webView = this
                        viewModel.started()
                        loadUrl(site.homepage)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            fullscreen?.let { video ->
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            (video.parent as? ViewGroup)?.removeView(video)
                            addView(video, FrameLayout.LayoutParams(-1, -1))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

