package com.evcockpit.presentation.media

import androidx.compose.ui.res.stringResource
import com.evcockpit.R

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
    private val mutableState = MutableStateFlow<MediaPageUiState>(MediaPageUiState.Empty)
    val uiState: StateFlow<MediaPageUiState> = mutableState.asStateFlow()
    fun start() { mutableState.value = MediaPageUiState.Loading }
    fun ready() { if (mutableState.value == MediaPageUiState.Loading) mutableState.value = MediaPageUiState.Ready }
    fun error(message: String) { mutableState.value = MediaPageUiState.Error(message.take(160)) }
    fun clear() { mutableState.value = MediaPageUiState.Empty }
}

private class MediaBrowserHost(context: Context) : FrameLayout(context) {
    var releaseFullscreen: (() -> Unit)? = null
}

private fun WebView.pauseMedia() {
    // No JavaScript interface is exposed to websites. onPause alone does not pause HTML media.
    runCatching {
        evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause();});", null)
        onPause()
    }
    CookieManager.getInstance().flush()
}

/** One owned browser per selected site. The cockpit and existing MediaEngine service are untouched. */
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
            else -> onClose()
        }
    }

    fullscreenView?.let { customView ->
        FullscreenVideoDialog(
            videoView = customView,
            onDismiss = { exitFullscreen?.invoke() }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(service.title, color = Color.White)
            TextButton(onClick = onClose) { Text(stringResource(R.string.native_close)) }
        }
        if (!playbackAllowed) {
            Text(stringResource(R.string.native_video_is_unavailable_for_this_connection), modifier = Modifier.padding(16.dp))
            return@Column
        }
        when (val state = page) {
            MediaPageUiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is MediaPageUiState.Error -> Row(Modifier.padding(12.dp)) {
                Text(state.message, color = Color(0xFFFFB17C), modifier = Modifier.weight(1f))
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
                                        service, web.url, request.origin.toString()) &&
                                    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in request.resources) {
                                    request.grant(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
                                } else request.deny()
                            }
                            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                if (!allowed || custom != null) { callback.onCustomViewHidden(); return }
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
                                val callback = completion
                                completion = null
                                web.visibility = View.VISIBLE
                                if (fullscreenView === view) {
                                    fullscreenView = null
                                    exitFullscreen = null
                                }
                                callback?.onCustomViewHidden()
                            }
                        }
                        web.webChromeClient = chrome
                        host.releaseFullscreen = { chrome.onHideCustomView() }
                        web.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (!request.isForMainFrame) return false
                                if (allowed && MediaSitePolicy.allows(service, request.url.toString())) return false
                                model.error("This link cannot be opened within the site.")
                                return true
                            }
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                if (!allowed || !MediaSitePolicy.allows(service, url)) {
                                    view.stopLoading()
                                    model.error("Could not validate the page address.")
                                } else model.start()
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                if (MediaSitePolicy.allows(service, url)) {
                                    CookieManager.getInstance().flush()
                                    model.ready()
                                }
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) model.error("Could not load the page. Check your connection.")
                            }
                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                if (request.isForMainFrame && response.statusCode >= 400)
                                    model.error("The site returned HTTP ${response.statusCode}.")
                            }
                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                handler.cancel()
                                model.error("Could not verify the secure connection.")
                            }
                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                exitFullscreen?.invoke()
                                rendererLost = true
                                host.removeView(view)
                                view.destroy()
                                browser = null
                                model.error("Web content closed. You can try again.")
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
                    // Release the view owned by THIS host, never a replacement from a later generation.
                    val web = (0 until host.childCount).map { host.getChildAt(it) }
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
