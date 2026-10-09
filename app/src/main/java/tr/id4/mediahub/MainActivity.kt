package tr.id4.mediahub

import android.app.Activity
import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/** Phone-side setup UI. Android Auto renders its own driver-safe library view. */
class MainActivity : Activity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private lateinit var listContainer: LinearLayout
    private lateinit var status: TextView
    private lateinit var videoContainer: LinearLayout
    private lateinit var searchVideos: EditText
    private lateinit var searchRadios: EditText
    private var favoritesOnly: Boolean = false
    private var radioFavoritesOnly = false
    private var radioPage = 0
    private var videoPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val parent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 36)
            setBackgroundColor(Color.rgb(246, 250, 252))
        }
        scroll.addView(parent)
        setContentView(scroll)
        parent.addView(label("ID.4 Media Hub", 25, true))
        parent.addView(label("Android Auto: ses/radyo • Telefon: M3U/IPTV video", 14, false))
        status = label("Medya hizmetine bağlanılıyor…", 13, false)
        parent.addView(status)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("⏯ Oynat/Duraklat") {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("⏹ Durdur") { controller?.stop() }, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row)
        parent.addView(label("Radyo / ses yayınları", 19, true))
        searchRadios = EditText(this).apply { hint = "Radyo adı veya grup ara"; setSingleLine(true) }
        parent.addView(searchRadios)
        searchRadios.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                radioPage = 0
                refresh()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        val radioFavoriteButton = button("Radyo favorileri: Kapalı") { }
        radioFavoriteButton.setOnClickListener {
            radioFavoritesOnly = !radioFavoritesOnly
            radioPage = 0
            radioFavoriteButton.text = if (radioFavoritesOnly) "Radyo favorileri: Açık" else "Radyo favorileri: Kapalı"
            refresh()
        }
        parent.addView(radioFavoriteButton)
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(listContainer)
        refresh()

        val name = EditText(this).apply { hint = "Yayın adı"; setSingleLine(true) }
        val url = EditText(this).apply {
            hint = "https://... MP3 / AAC yayın bağlantısı"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        parent.addView(label("Yeni yayın ekle", 19, true))
        parent.addView(name)
        parent.addView(url)
        parent.addView(button("+ Yayın ekle") {
            if (StationStore.add(this, name.text.toString(), url.text.toString())) {
                name.setText(""); url.setText(""); refresh()
                toast("Yayın eklendi. Aracın medya listesi için yeniden bağlanmak gerekebilir.")
            } else toast("Geçerli bir HTTPS yayın adresi ve benzersiz ad gir.")
        })
        parent.addView(button("YouTube Music uygulamasını aç") {
            val launch = packageManager.getLaunchIntentForPackage("com.google.android.apps.youtube.music")
            if (launch != null) startActivity(launch)
            else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com")))
        })
        parent.addView(label(
            "YouTube Music ayrı uygulamada açılır; Premium kilidi aşılmaz. " +
                "Sürüşte yalnızca Android Auto'nun izin verdiği ses arayüzü kullanılır.", 12, false))

        parent.addView(button("Netflix'i telefonda aç (park halinde)") {
            // Netflix içerikleri DRM korumalıdır; harici, resmî uygulama açılır.
            // Android Auto/araç ekranına video yansıtılmaz.
            val netflixPackage = "com.netflix.mediaclient"
            val launch = packageManager.getLaunchIntentForPackage(netflixPackage)
            if (launch != null) {
                startActivity(launch)
            } else {
                val marketUrl = Uri.parse("market://details?id=$netflixPackage")
                val storeUrl = Uri.parse("https://play.google.com/store/apps/details?id=$netflixPackage")
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, marketUrl))
                } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW, storeUrl))
                }
            }
        })
        parent.addView(label(
            "Netflix yalnızca telefondaki resmî uygulamada açılır. " +
                "ID.4 ekranında Netflix videosu oynatılmaz; sürüş kilitleri değiştirilmez.", 12, false))

        parent.addView(label("SportzX (harici uygulama)", 19, true))
        parent.addView(button("SportzX'i telefonda aç") {
            SportzXLauncher.openIfInstalled(this)
        })
        parent.addView(button("SportzX sitesini tarayıcıda incele") {
            SportzXLauncher.openSite(this)
        })
        parent.addView(label(
            "SportzX ayrı uygulamadır. Verdiğiniz web adresini istek üzerine açabiliriz; " +
                "APK güvenliği ve yayın lisansları doğrulanmadı. ID.4 ön ekranına video aktarılmaz.", 12, false))

        parent.addView(label("APTV benzeri IPTV / M3U (telefonda)", 19, true))
        parent.addView(label(
            "Kendi yasal M3U listenizi ekleyin. Radyo olarak işaretli kanallar Android Auto ses listesine, " +
                "diğer kanallar yalnızca telefon video listesine alınır. HTTPS gereklidir.", 13, false
        ))
        val playlistUrl = EditText(this).apply {
            hint = "https://.../liste.m3u"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        parent.addView(playlistUrl)
        parent.addView(label("Hazır açık yayın kaynakları", 17, true))
        TrustedSources.playlists.forEach { source ->
            parent.addView(button("Listeyi ekle: ${source.name}") {
                playlistUrl.setText(source.url)
                importFromHttps(source.url)
            })
            parent.addView(label(source.description, 11, false))
        }
        parent.addView(button("TRT Haber canlı yayını ekle (telefonda)") {
            if (VideoStore.add(this, "TRT Haber", TrustedSources.TRT_HABER, "Türkiye / Haber")) {
                toast("TRT Haber eklendi; erişimi telefon üzerinden dene.")
                refreshVideo()
            } else toast("TRT Haber zaten listede veya eklenemedi.")
        })
        parent.addView(button("Apple örnek HLS videosunu ekle (telefonda test)") {
            if (VideoStore.add(this, "Apple HLS test videosu", TrustedSources.APPLE_HLS_TEST, "Test")) {
                toast("Test videosu listeye eklendi.")
                refreshVideo()
            } else toast("Test videosu zaten listede veya eklenemedi.")
        })
        parent.addView(button("M3U adresinden içe aktar") {
            val address = playlistUrl.text.toString().trim()
            importFromHttps(address)
        })
        parent.addView(button("Telefondan M3U dosyası seç") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, PICK_PLAYLIST)
        })
        parent.addView(button("M3U listelerimi yedekle (.m3u)") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/x-mpegURL"
                putExtra(Intent.EXTRA_TITLE, "ID4_Media_Hub_Yedek.m3u")
            }, EXPORT_PLAYLIST)
        })
        parent.addView(button("Telefondaki video dosyasını aç") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
            }, PICK_LOCAL_VIDEO)
        })
        parent.addView(label("IPTV kanallarım (telefon ekranı)", 19, true))
        searchVideos = EditText(this).apply {
            hint = "Kanal veya grup ara"
            setSingleLine(true)
        }
        parent.addView(searchVideos)
        searchVideos.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                videoPage = 0
                refreshVideo()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        val favoritesButton = button("Yalnızca favoriler: Kapalı") { }
        favoritesButton.setOnClickListener {
            favoritesOnly = !favoritesOnly
            videoPage = 0
            favoritesButton.text = if (favoritesOnly) "Yalnızca favoriler: Açık" else "Yalnızca favoriler: Kapalı"
            refreshVideo()
        }
        parent.addView(favoritesButton)
        videoContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(videoContainer)
        refreshVideo()

        val token = SessionToken(this, ComponentName(this, AudioLibraryService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({
                try {
                    val connectedController = future.get()
                    if (isFinishing || isDestroyed) return@addListener
                    controller = connectedController.also { connected ->
                        connected.addListener(object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                status.text = "Oynatma hatası: ${error.errorCodeName}. Yayın URL'si, internet ve formatı kontrol et."
                            }
                            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                                if (mediaItem != null) status.text = "Seçilen radyo: ${mediaItem.mediaMetadata.title ?: "Yayın"}"
                            }
                        })
                    }
                    status.text = "Medya hizmeti bağlandı • Android Auto media"
                } catch (_: Exception) {
                    if (!isFinishing && !isDestroyed)
                        status.text = "Medya hizmeti bağlanamadı. Uygulamayı yeniden açıp deneyin."
                }
            }, mainExecutor)
        }
    }

    private fun importFromHttps(address: String) {
        if (!M3uParser.isHttpsUrl(address)) {
            toast("HTTPS ile başlayan geçerli bir M3U bağlantısı gir.")
            return
        }
        status.text = "M3U listesi alınıyor…"
        Thread {
            try {
                val text = PlaylistLoader.fromHttps(address)
                runOnUiThread { if (!isFinishing && !isDestroyed) importPlaylist(text) }
            } catch (_: Exception) {
                // Network exceptions can contain M3U links with subscriber credentials.
                runOnUiThread { if (!isFinishing && !isDestroyed)
                    status.text = "M3U listesi alınamadı. Adresi, interneti veya sunucunun erişim izinlerini kontrol et."
                }
            }
        }.start()
    }

    private fun playStation(station: Station) {
        val ctrl = controller ?: return toast("Medya hizmeti henüz hazır değil")
        // Send only the selected media ID across Binder, not the entire M3U library.
        // MediaLibrarySession resolves it and builds a bounded next/previous queue.
        ctrl.setMediaItem(station.toMediaItem())
        ctrl.prepare()
        ctrl.play()
        status.text = "Çalıyor: ${station.name}"
    }

    private fun refresh() {
        if (!::listContainer.isInitialized || !::searchRadios.isInitialized) return
        listContainer.removeAllViews()
        val q = searchRadios.text.toString().trim()
        val stations = StationStore.load(this).filter { station ->
            (!radioFavoritesOnly || station.favorite) && (q.isEmpty() ||
                station.name.contains(q, ignoreCase = true) || station.group.contains(q, ignoreCase = true))
        }
        val maxPage = ((stations.size - 1).coerceAtLeast(0)) / PAGE_SIZE
        radioPage = radioPage.coerceIn(0, maxPage)
        listContainer.addView(label("${stations.size} radyo • sayfa ${radioPage + 1}/${maxPage + 1}", 12, false))
        for (station in stations.drop(radioPage * PAGE_SIZE).take(PAGE_SIZE)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(button("▶ ${station.name}") { playStation(station) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button("Kontrol") { checkStream(station.name, station.url) })
            row.addView(button(if (station.favorite) "★" else "☆") {
                StationStore.toggleFavorite(this, station.id)
                refresh()
            })
            row.addView(button("TV") {
                if (VideoStore.add(this, station.name, station.url, station.group)) {
                    StationStore.remove(this, station.id)
                    refresh(); refreshVideo()
                    toast("Kanal telefon video listesine taşındı.")
                } else toast("Video listesine eklenemedi veya zaten mevcut.")
            })
            row.addView(button("×") {
                StationStore.remove(this, station.id)
                refresh()
            })
            listContainer.addView(row)
        }
        addPageButtons(listContainer, radioPage, maxPage) { radioPage = it; refresh() }
    }

    private fun importPlaylist(source: String) {
        val entries = M3uParser.parse(source)
        if (entries.isEmpty()) {
            status.text = "Bu dosyada geçerli HTTPS M3U kanalı bulunamadı."
            return
        }
        val audio = StationStore.addAll(this, entries)
        val video = VideoStore.addAll(this, entries)
        status.text = "M3U alındı: $audio yeni radyo, $video yeni video kanalı."
        refresh()
        refreshVideo()
    }

    private fun refreshVideo() {
        if (!::videoContainer.isInitialized || !::searchVideos.isInitialized) return
        videoContainer.removeAllViews()
        val query = searchVideos.text.toString().trim()
        val channels = VideoStore.load(this)
            .filter { (!favoritesOnly || it.favorite) &&
                (query.isEmpty() || it.name.contains(query, ignoreCase = true) ||
                    it.group.contains(query, ignoreCase = true)) }
        val maxPage = ((channels.size - 1).coerceAtLeast(0)) / PAGE_SIZE
        videoPage = videoPage.coerceIn(0, maxPage)
        videoContainer.addView(label("${channels.size} TV kanalı • sayfa ${videoPage + 1}/${maxPage + 1}", 12, false))
        channels.drop(videoPage * PAGE_SIZE).take(PAGE_SIZE).forEach { channel ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            line.addView(button("▶ ${channel.name} • ${channel.group}") {
                startActivity(Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, channel.url)
                    .putExtra(PlayerActivity.EXTRA_TITLE, channel.name))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(button("Kontrol") { checkStream(channel.name, channel.url) })
            line.addView(button(if (channel.favorite) "★" else "☆") {
                VideoStore.toggleFavorite(this, channel.id)
                refreshVideo()
            })
            line.addView(button("♪") {
                if (StationStore.add(this, channel.name, channel.url, channel.group)) {
                    VideoStore.remove(this, channel.id)
                    refreshVideo(); refresh()
                    toast("Kanal radyo/ses listesine taşındı.")
                } else toast("Radyo listesine eklenemedi veya zaten mevcut.")
            })
            line.addView(button("×") {
                VideoStore.remove(this, channel.id)
                refreshVideo()
            })
            videoContainer.addView(line)
        }
        addPageButtons(videoContainer, videoPage, maxPage) { videoPage = it; refreshVideo() }
    }

    /** Tests only HTTP reachability, off the UI thread. A successful HEAD is not playable-video proof. */
    private fun checkStream(name: String, url: String) {
        status.text = "${name.take(50)}: bağlantı kontrol ediliyor…"
        Thread {
            val result = StreamProbe.check(url)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    status.text = "${name.take(50)}: ${result.description()}"
                    toast(result.description())
                }
            }
        }.start()
    }

    private fun addPageButtons(container: LinearLayout, page: Int, maxPage: Int, go: (Int) -> Unit) {
        if (maxPage == 0) return
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val previous = button("◀ Önceki") { go(page - 1) }.apply { isEnabled = page > 0 }
        val next = button("Sonraki ▶") { go(page + 1) }.apply { isEnabled = page < maxPage }
        nav.addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(next, LinearLayout.LayoutParams(0, -2, 1f))
        container.addView(nav)
    }

    @Deprecated("Android framework Activity file-picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            PICK_PLAYLIST -> {
                status.text = "Dosya okunuyor…"
                Thread {
                    try {
                        val playlist = contentResolver.openInputStream(uri)?.use { PlaylistLoader.readLimited(it) }
                            ?: error("Dosya okunamadı")
                        runOnUiThread { if (!isFinishing && !isDestroyed) importPlaylist(playlist) }
                    } catch (_: Exception) {
                        runOnUiThread { if (!isFinishing && !isDestroyed)
                            status.text = "M3U dosyası okunamadı. Dosyayı ve biçimini kontrol et."
                        }
                    }
                }.start()
            }
            EXPORT_PLAYLIST -> {
                val entries = StationStore.load(this).map { PlaylistEntry(it.name, it.url, it.group, true) } +
                    VideoStore.load(this).map { PlaylistEntry(it.name, it.url, it.group, false) }
                val contents = M3uExporter.export(entries)
                Thread {
                    try {
                        contentResolver.openOutputStream(uri, "w")?.use {
                            it.write(contents.toByteArray(Charsets.UTF_8))
                        } ?: error("Yedek dosyası açılamadı")
                        runOnUiThread { if (!isFinishing && !isDestroyed) toast("M3U yedeği kaydedildi.") }
                    } catch (_: Exception) {
                        runOnUiThread { if (!isFinishing && !isDestroyed) toast("Yedekleme başarısız oldu.") }
                    }
                }.start()
            }
            PICK_LOCAL_VIDEO -> {
                startActivity(Intent(this, PlayerActivity::class.java).apply {
                    this.data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(PlayerActivity.EXTRA_TITLE, "Yerel video")
                })
            }
        }
    }

    companion object {
        private const val PICK_PLAYLIST = 701
        private const val PICK_LOCAL_VIDEO = 702
        private const val EXPORT_PLAYLIST = 703
        private const val PAGE_SIZE = 20
    }

    private fun button(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        setOnClickListener { action() }
        isAllCaps = false
    }
    private fun label(value: String, size: Int, strong: Boolean) = TextView(this).apply {
        text = value; textSize = size.toFloat()
        setTextColor(Color.rgb(21, 42, 62))
        setPadding(2, 12, 2, 12)
        if (strong) setTypeface(null, android.graphics.Typeface.BOLD)
    }
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
