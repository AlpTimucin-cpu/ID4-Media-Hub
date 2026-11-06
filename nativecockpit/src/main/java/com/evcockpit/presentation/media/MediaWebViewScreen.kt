package com.evcockpit.presentation.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evcockpit.R
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface MediaPageUiState {
    data object Empty : MediaPageUiState
    data object Loading : MediaPageUiState
    data object Ready : MediaPageUiState
    data class Error(val message: String) : MediaPageUiState
}

class MediaWebViewViewModel : ViewModel() {
    private val state = MutableStateFlow<MediaPageUiState>(MediaPageUiState.Empty)
    val uiState: StateFlow<MediaPageUiState> = state.asStateFlow()
    fun start() { state.value = MediaPageUiState.Loading }
    fun ready() { if (state.value == MediaPageUiState.Loading) state.value = MediaPageUiState.Ready }
    fun error(message: String) { state.value = MediaPageUiState.Error(message.take(160)) }
    fun clear() { state.value = MediaPageUiState.Empty }
}

private class MediaBrowserHost(context: Context) : FrameLayout(context) {
    var releaseFullscreen: (() -> Unit)? = null
}

/** Only explicitly identified ad endpoints; never block googlevideo.com or general youtubei. */
private object YoutubeAdFilter {
    private val blockedDomains = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "2mdn.net", "adservice.google.com"
    )

    fun shouldBlock(url: Uri, isMainFrame: Boolean, method: String): Boolean {
        if (isMainFrame || !method.equals("GET", ignoreCase = true) ||
            url.scheme != "https") return false
        val host = url.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        if (blockedDomains.any { host == it || host.endsWith(".$it") }) return true
        val youtube = host == "youtube.com" || host.endsWith(".youtube.com")
        if (!youtube) return false
        val path = url.encodedPath.orEmpty().lowercase(Locale.ROOT)
        return path.startsWith("/pagead/") ||
            path.startsWith("/api/stats/ads") ||
            path.startsWith("/get_midroll_info")
    }

    fun emptyResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain", "UTF-8", 204, "No Content",
        mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0))
    )

    // Install on both page start and page finish. YouTube changes DOM via SPA routing.
    // Fast-forward is attempted ONLY while YouTube marks the player as showing an ad.
    val pageScript = """
        (function () {
          if (!document.documentElement) return;
          if (window.__evCockpitCleaner) return;
          window.__evCockpitCleaner = true;
          var css = [
            '.video-ads', '.ytp-ad-module', '.ytp-ad-overlay-container',
            '#player-ads', '#masthead-ad', 'ytd-display-ad-renderer',
            'ytd-promoted-sparkles-web-renderer', 'ytd-ad-slot-renderer',
            'ytd-in-feed-ad-layout-renderer', 'ytm-promoted-sparkles-web-renderer',
            'ytm-companion-ad-renderer', 'ytm-ad-slot-renderer'
          ];
          var style = document.createElement('style');
          style.textContent = css.join(',') + '{display:none !important}';
          (document.head || document.documentElement).appendChild(style);
          var prior = new WeakMap();
          var activeVideo = null;
          var scheduled = false;
          function clean() {
            scheduled = false;
            if (document.hidden) return;
            document.querySelectorAll(
              '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, ' +
              '.ytp-skip-ad-button, .ytp-ad-skip-button-slot button'
            ).forEach(function (button) {
              if (!button.disabled && button.getClientRects().length) {
                try { button.click(); } catch (e) {}
              }
            });
            var player = document.querySelector('#movie_player, .html5-video-player');
            var isAd = !!(player && player.classList.contains('ad-showing'));
            var video = player && player.querySelector('video.html5-main-video');
            if (activeVideo && (activeVideo !== video || !isAd)) {
              var old = prior.get(activeVideo);
              if (old) {
                try {
                  activeVideo.playbackRate = old.rate;
                  activeVideo.muted = old.muted;
                } catch (e) {}
              }
              prior.delete(activeVideo);
              activeVideo = null;
            }
            if (isAd && video) {
              if (!prior.has(video)) prior.set(video, {
                rate: video.playbackRate,
                muted: video.muted
              });
              activeVideo = video;
              try {
                video.muted = true;
                video.playbackRate = 16.0;
              } catch (e) {}
            }
          }
          function schedule() {
            if (scheduled) return;
            scheduled = true;
            requestAnimationFrame(clean);
          }
          new MutationObserver(schedule).observe(document.documentElement, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['class']
          });
          document.addEventListener('visibilitychange', schedule);
          document.addEventListener('yt-navigate-finish', schedule);
          setInterval(schedule, 1250);
          schedule();
        })();
    """.trimIndent()

}

private fun WebView.pauseMedia() {
    runCatching {
        evaluateJavascript(
            "document.querySelectorAll('video,audio').forEach(function(m){m.pause();});", null
        )
        onPause()
    }
    CookieManager.getInstance().flush()
}

/** Phone-only WebView. DRM and external apps keep their own separate owners. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MediaWebViewScreen(
    service: MediaSite,
    playbackAllowed: Boolean,
    onClose: () -> Unit,
    model: MediaWebViewViewModel = viewModel(key = "web-" + service.name)
) {
    var fullscreenView by remember(service) { mutableStateOf<View?>(null) }
    var browser by remember(service) { mutableStateOf<WebView?>(null) }
    var exitFullscreen by remember(service) { mutableStateOf<(() -> Unit)?>(null) }
    var generation by remember(service) { mutableIntStateOf(0) }
    var rendererLost by remember(service) { mutableStateOf(false) }
    val allowed by rememberUpdatedState(playbackAllowed)
    val page by model.uiState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current

    DisposableEffect(browser, owner) {
        val web = browser
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exitFullscreen?.invoke()
                    web?.pauseMedia()
                }
                Lifecycle.Event.ON_RESUME -> if (allowed && !rendererLost) web?.onResume()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(playbackAllowed) {
        if (!playbackAllowed) { exitFullscreen?.invoke(); browser?.pauseMedia() }
    }
    BackHandler {
        when {
            exitFullscreen != null -> exitFullscreen?.invoke()
            !rendererLost && browser?.canGoBack() == true -> browser?.goBack()
            else -> { browser?.pauseMedia(); onClose() }
        }
    }
    fullscreenView?.let { view ->
        FullscreenVideoDialog(videoView = view, onDismiss = { exitFullscreen?.invoke() })
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(service.title, color = Color.White)
            TextButton(onClick = { exitFullscreen?.invoke(); browser?.pauseMedia(); onClose() }) {
                Text(stringResource(R.string.native_close))
            }
        }
        if (!playbackAllowed) {
            Text(
                stringResource(R.string.native_video_is_unavailable_for_this_connection),
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }
        when (val state = page) {
            MediaPageUiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is MediaPageUiState.Error -> Row(Modifier.padding(12.dp)) {
                Text(
                    state.message, color = Color(0xFFFFB17C),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    exitFullscreen?.invoke()
                    rendererLost = false
                    model.start()
                    generation++
                }) { Text(stringResource(R.string.native_try_again)) }
            }
            else -> Unit
        }
        key(service, generation) {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { context ->
                    MediaBrowserHost(context).apply {
                        val host = this
                        val web = WebView(context)
                        with(web.settings) {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            mediaPlaybackRequiresUserGesture = false
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(false)
                            safeBrowsingEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            userAgentString = MediaSitePolicy.userAgent(service, userAgentString)
                        }
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(web, service != MediaSite.YOUTUBE)
                        }
                        val chrome = object : WebChromeClient() {
                            private var custom: View? = null
                            private var completion: CustomViewCallback? = null
                            override fun onPermissionRequest(request: PermissionRequest) {
                                if (allowed && MediaSitePolicy.grantsProtectedMedia(
                                        service, web.url, request.origin.toString()
                                    ) && PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in request.resources
                                ) {
                                    request.grant(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
                                } else request.deny()
                            }
                            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                if (!allowed || custom != null) {
                                    callback.onCustomViewHidden()
                                    return
                                }
                                (view.parent as? ViewGroup)?.removeView(view)
                                custom = view
                                completion = callback
                                fullscreenView = view
                                web.visibility = View.GONE
                                exitFullscreen = { onHideCustomView() }
                            }
                            override fun onHideCustomView() {
                                val view = custom ?: return
                                (view.parent as? ViewGroup)?.removeView(view)
                                custom = null
                                val cb = completion
                                completion = null
                                web.visibility = View.VISIBLE
                                if (fullscreenView === view) {
                                    fullscreenView = null
                                    exitFullscreen = null
                                }
                                runCatching { cb?.onCustomViewHidden() }
                            }
                        }
                        web.webChromeClient = chrome
                        host.releaseFullscreen = { chrome.onHideCustomView() }
                        web.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView, request: WebResourceRequest
                            ): Boolean {
                                if (!request.isForMainFrame) return false
                                if (allowed && MediaSitePolicy.allows(service, request.url.toString())) return false
                                model.error("Bu bağlantı site dışında olduğu için engellendi.")
                                return true
                            }
                            override fun shouldInterceptRequest(
                                view: WebView, request: WebResourceRequest
                            ): WebResourceResponse? {
                                // Called off the main thread: do not access Compose mutable state here.
                                if (service != MediaSite.YOUTUBE) return null
                                return if (YoutubeAdFilter.shouldBlock(
                                        request.url, request.isForMainFrame, request.method
                                    )) YoutubeAdFilter.emptyResponse() else null
                            }
                            override fun onPageStarted(
                                view: WebView, url: String?, favicon: Bitmap?
                            ) {
                                if (!allowed || !MediaSitePolicy.allows(service, url)) {
                                    view.stopLoading()
                                    model.error("Güvenli sayfa adresi doğrulanamadı.")
                                } else {
                                    model.start()
                                    // May run before the DOM is ready; onPageFinished retries.
                                    if (service == MediaSite.YOUTUBE) runCatching {
                                        view.evaluateJavascript(YoutubeAdFilter.pageScript, null)
                                    }
                                }
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                if (MediaSitePolicy.allows(service, url)) {
                                    if (service == MediaSite.YOUTUBE && allowed) {
                                        runCatching {
                                            view.evaluateJavascript(YoutubeAdFilter.pageScript, null)
                                        }.onFailure {
                                            Log.w("EV-Cockpit-AdBlock", "JS injection skipped", it)
                                        }
                                    }
                                    CookieManager.getInstance().flush()
                                    model.ready()
                                }
                            }
                            override fun onReceivedError(
                                view: WebView, request: WebResourceRequest, error: WebResourceError
                            ) {
                                if (request.isForMainFrame) model.error("Sayfa yüklenemedi. Bağlantıyı kontrol edin.")
                            }
                            override fun onReceivedHttpError(
                                view: WebView, request: WebResourceRequest,
                                response: WebResourceResponse
                            ) {
                                if (request.isForMainFrame && response.statusCode >= 400) {
                                    model.error("Sunucu HTTP ${response.statusCode} döndürdü.")
                                }
                            }
                            override fun onReceivedSslError(
                                view: WebView, handler: SslErrorHandler, error: SslError
                            ) {
                                handler.cancel()
                                model.error("Güvenli bağlantı doğrulanamadı.")
                            }
                            override fun onRenderProcessGone(
                                view: WebView, detail: RenderProcessGoneDetail
                            ): Boolean {
                                exitFullscreen?.invoke()
                                rendererLost = true
                                host.removeView(view)
                                view.destroy()
                                browser = null
                                model.error("WebView kapandı. Tekrar deneyin.")
                                return true
                            }
                        }
                        host.addView(web, FrameLayout.LayoutParams(-1, -1))
                        browser = web
                        web.loadUrl(service.url)
                    }
                },
                onRelease = { host ->
                    host.releaseFullscreen?.invoke()
                    host.releaseFullscreen = null
                    val web = (0 until host.childCount)
                        .map { host.getChildAt(it) }
                        .filterIsInstance<WebView>().firstOrNull()
                    web?.let {
                        it.pauseMedia()
                        it.stopLoading()
                        it.webChromeClient = null
                        it.webViewClient = WebViewClient()
                        host.removeView(it)
                        it.removeAllViews()
                        it.destroy()
                        if (browser === it) browser = null
                    }
                    host.removeAllViews()
                }
            )
        }
    }
}