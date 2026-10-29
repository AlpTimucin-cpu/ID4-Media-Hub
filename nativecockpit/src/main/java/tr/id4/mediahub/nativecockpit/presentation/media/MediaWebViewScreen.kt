package tr.id4.mediahub.nativecockpit.presentation.media

import android.annotation.SuppressLint
import android.graphics.Bitmap
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
    data object Loading : MediaPageUiState
    data object Ready : MediaPageUiState
    data class Error(val message: String) : MediaPageUiState
}

private fun safePage(url: String?): Boolean = runCatching {
    val uri = Uri.parse(url)
    uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank()
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
    onClose: () -> Unit
) {
    var view by remember(service) { mutableStateOf<WebView?>(null) }
    var page by remember(service) { mutableStateOf<MediaPageUiState>(MediaPageUiState.Loading) }
    BackHandler(enabled = verifiedParked) {
        val current = view
        if (current?.canGoBack() == true) current.goBack() else onClose()
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
            MediaPageUiState.Ready -> Unit
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
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
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            webView: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            if (!request.isForMainFrame) return false
                            if (safePage(request.url.toString())) return false
                            page = MediaPageUiState.Error("Güvensiz bağlantı engellendi")
                            return true
                        }

                        override fun onPageStarted(webView: WebView, url: String?, favicon: Bitmap?) {
                            page = MediaPageUiState.Loading
                        }

                        override fun onPageFinished(webView: WebView, url: String?) {
                            if (page !is MediaPageUiState.Error)
                                page = MediaPageUiState.Ready
                        }

                        override fun onReceivedError(
                            webView: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                page = MediaPageUiState.Error("Sayfa yüklenemedi. Bağlantıyı kontrol edin.")
                            }
                        }

                        override fun onReceivedSslError(
                            webView: WebView, handler: SslErrorHandler, error: SslError
                        ) {
                            handler.cancel() // Never bypass certificate errors.
                            page = MediaPageUiState.Error("Güvenli bağlantı doğrulanamadı")
                        }

                        override fun onReceivedHttpError(
                            webView: WebView,
                            request: WebResourceRequest,
                            response: android.webkit.WebResourceResponse
                        ) {
                            if (request.isForMainFrame && response.statusCode >= 400)
                                page = MediaPageUiState.Error("Site HTTP ${response.statusCode} hatası verdi")
                        }
                    }
                    view = this
                    if (safePage(service.url)) loadUrl(service.url)
                }
            },
            update = { webView ->
                // Do not reload on Compose recomposition.
                if (view !== webView) view = webView
            }
        )
        DisposableEffect(service) {
            val current = view
            onDispose {
                current?.stopLoading()
                current?.loadUrl("about:blank")
                current?.removeAllViews()
                current?.destroy()
                view = null
            }
        }
    }
}
