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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
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
    private lateinit var homeContainer: LinearLayout
    private lateinit var tabPages: List<LinearLayout>
    private lateinit var navButtons: List<TextView>
    private lateinit var contentScroll: ScrollView
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 16, 28)
        window.navigationBarColor = Color.rgb(12, 22, 37)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 16, 28))
        }
        setContentView(root)
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(7))
            background = gradient(0xff172c48.toInt(), 0xff0b1423.toInt(), 0)
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brandRow.addView(label("ID⁴", 32, true).apply { setTextColor(0xff66dddb.toInt()) })
        val brandTitles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10),0,0,0)
        }
        brandTitles.addView(label("MEDIA HUB", 23, true))
        brandTitles.addView(label("TELEVİZYON  /  RADYO  /  MEDYA", 10, false))
        brandRow.addView(brandTitles, LinearLayout.LayoutParams(0,-2,1f))
        val add = textAction("＋") { selectTab(3) }
        brandRow.addView(add, LinearLayout.LayoutParams(dp(46),dp(48)))
        top.addView(brandRow)
        status = label("●   Medya bağlantısı hazırlanıyor", 12, false).apply {
            setTextColor(0xff8de1d3.toInt())
            setPadding(dp(3), dp(2), dp(3), dp(6))
            maxLines = 2
        }
        top.addView(status)
        root.addView(top)

        contentScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(6), 0, dp(8))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16),0,dp(16),0)
        }
        contentScroll.addView(content)
        root.addView(contentScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val home = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val television = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val radio = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val library = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabPages = listOf(home, television, radio, library)
        tabPages.forEach { content.addView(it) }
        homeContainer = home

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8),dp(7),dp(8),dp(9))
            setBackgroundColor(0xff102137.toInt())
        }
        navButtons = listOf("⌂\nAna Sayfa","▣\nTV","♫\nRadyo","☷\nKitaplık").mapIndexed { index, name ->
            textAction(name) { selectTab(index) }.apply {
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(6), dp(3), dp(6))
                minHeight = dp(50)
                nav.addView(this, LinearLayout.LayoutParams(0,dp(54),1f))
            }
        }
        root.addView(nav)
        selectTab(0)

        television.addView(section("CANLI TELEVİZYON", "Kanallar • kategoriler • favoriler"))
        television.addView(button("＋ Kanal listesi ekle", secondary = false) { selectTab(3) })
        searchVideos = searchInput("Kanal veya kategori ara")
        television.addView(searchVideos)
        searchVideos.addTextChangedListener(watch { videoPage=0;refreshVideo() })
        val tvControls = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val favoriteVideos = textAction("☆  Favoriler") {
            favoritesOnly = !favoritesOnly
            videoPage=0
            refreshVideo()
        }
        tvControls.addView(favoriteVideos, LinearLayout.LayoutParams(0,dp(46),1f))
        tvControls.addView(textAction("⟳  Yenile") { refreshVideo() },LinearLayout.LayoutParams(0,dp(46),1f))
        television.addView(tvControls)
        videoContainer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        television.addView(videoContainer)

        radio.addView(section("İNTERNET RADYOSU","Android Auto medya kitaplığı için ses yayını"))
        val control=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        control.addView(button("⏯ Oynat / Duraklat") {
            controller?.let { if(it.isPlaying) it.pause() else it.play() }
        },LinearLayout.LayoutParams(0,dp(54),1f))
        control.addView(button("■ Durdur") { controller?.stop() },LinearLayout.LayoutParams(0,dp(54),1f))
        radio.addView(control)
        searchRadios=searchInput("Radyo adı veya kategori ara")
        radio.addView(searchRadios)
        searchRadios.addTextChangedListener(watch { radioPage=0;refresh() })
        radio.addView(textAction("☆ Yalnızca favori radyolar") {
            radioFavoritesOnly = !radioFavoritesOnly
            radioPage=0
            refresh()
        })
        listContainer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        radio.addView(listContainer)

        library.addView(section("KÜTÜPHANEM","Kaynaklar, oynatma ve cihaz bağlantıları"))
        val importCard=panel()
        importCard.addView(label("M3U / M3U8 KANAL LİSTESİ",18,true))
        importCard.addView(label("Abonelik veya açık yayın bağlantınızı ekleyin. Kanallar TV ve Radyo sekmelerine ayrılır.",12,false))
        val playlistUrl=searchInput("https://.../kanallar.m3u")
        importCard.addView(playlistUrl)
        importCard.addView(button("Bağlantıdan kanalları yükle") { importFromHttps(playlistUrl.text.toString().trim()) })
        importCard.addView(button("Telefondan M3U dosyası seç",secondary=true) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type="*/*"
            }, PICK_PLAYLIST)
        })
        library.addView(importCard)

        val ready=panel()
        ready.addView(label("AÇIK YAYIN LİSTELERİ",18,true))
        TrustedSources.playlists.forEach { source ->
            ready.addView(textAction("＋  "+source.name) {
                importFromHttps(source.url)
            })
        }
        ready.addView(textAction("▶  TRT Haber kanalı") {
            if (VideoStore.add(this, "TRT Haber", TrustedSources.TRT_HABER, "Türkiye / Haber")) {
                refreshVideo(); renderHome(); toast("TRT Haber eklendi")
            } else toast("Kanal zaten var veya kaynak eklenemedi")
        })
        ready.addView(textAction("▶  Apple HLS test videosu") {
            if (VideoStore.add(this, "Apple HLS Test", TrustedSources.APPLE_HLS_TEST, "Test")) {
                refreshVideo(); renderHome(); toast("Test videosu eklendi")
            } else toast("Video zaten ekli")
        })
        library.addView(ready)

        val radioCard=panel()
        radioCard.addView(label("YENİ RADYO",18,true))
        val radioName=searchInput("Radyo adı")
        val radioUrl=searchInput("https://.../yayın.mp3")
        radioCard.addView(radioName)
        radioCard.addView(radioUrl)
        radioCard.addView(button("Radyo ekle") {
            if (StationStore.add(this, radioName.text.toString(),radioUrl.text.toString())) {
                radioName.setText("");radioUrl.setText("")
                refresh();renderHome();toast("Radyo eklendi")
            } else toast("Geçerli HTTPS adresi gir")
        })
        library.addView(radioCard)

        val external=panel()
        external.addView(label("DİĞER UYGULAMALAR",18,true))
        external.addView(textAction("♫  YouTube Music'i aç") {
            val launch=packageManager.getLaunchIntentForPackage("com.google.android.apps.youtube.music")
            try { startActivity(launch ?: Intent(Intent.ACTION_VIEW,Uri.parse("https://music.youtube.com"))) }
            catch (_: Exception) { toast("YouTube Music açılamadı") }
        })
        external.addView(textAction("▣  Netflix'i aç") {
            val launch=packageManager.getLaunchIntentForPackage("com.netflix.mediaclient")
            try { startActivity(launch ?: Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=com.netflix.mediaclient"))) }
            catch (_: Exception) { toast("Netflix açılamadı") }
        })
        external.addView(textAction("◉  SportzX'i aç / durumunu incele") { SportzXLauncher.openIfInstalled(this) })
        external.addView(textAction("↗  SportzX internet sitesi") { SportzXLauncher.openSite(this) })
        external.addView(label("Haricî uygulamalar bağımsız çalışır. SportzX'in kimliği ve yayın izinleri doğrulanmadı.",12,false))
        library.addView(external)

        val utilities=panel()
        utilities.addView(label("ARAÇLAR",18,true))
        utilities.addView(textAction("▤  M3U listemi yedekle") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type="application/x-mpegURL"
                putExtra(Intent.EXTRA_TITLE,"ID4_Media_Hub_Yedek.m3u")
            },EXPORT_PLAYLIST)
        })
        utilities.addView(textAction("▶  Telefon videosu aç") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE);type="video/*"
            },PICK_LOCAL_VIDEO)
        })
        utilities.addView(textAction("⚙  Android Auto ayarları") {
            try { startActivity(Intent("android.settings.APPLICATION_DETAILS_SETTINGS",Uri.parse("package:com.google.android.projection.gearhead"))) }
            catch (_: Exception) { toast("Android Auto ayarları açılamadı") }
        })
        utilities.addView(label("Android Auto ekranında yalnızca ses medya kataloğu hedeflenir. Araçta video oynatma desteklenmez.",12,false))
        library.addView(utilities)

        refresh()
        refreshVideo()
        renderHome()
        val token = SessionToken(this, ComponentName(this, AudioLibraryService::class.java))
        controllerFuture = MediaController.Builder(this,token).buildAsync().also { future ->
            future.addListener({
                try {
                    val connected=future.get()
                    if(isFinishing || isDestroyed)return@addListener
                    controller=connected
                    connected.addListener(object:Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            status.text="●   Radyo hatası: ${error.errorCodeName}"
                        }
                        override fun onMediaItemTransition(
                            mediaItem: androidx.media3.common.MediaItem?,reason:Int
                        ) {
                            if(mediaItem != null)status.text="♫   ${mediaItem.mediaMetadata.title ?: "Radyo"}"
                        }
                    })
                    status.text="●   Ses servisi hazır"
                }catch (_:Exception){
                    if(!isFinishing && !isDestroyed)status.text="●   Ses servisine bağlanılamadı"
                }
            },mainExecutor)
        }
    }

    private fun watch(action:()->Unit):TextWatcher=object:TextWatcher{
        override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
        override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){action()}
        override fun afterTextChanged(s:Editable?){}
    }

    private fun selectTab(index:Int){
        currentTab=index
        if(::tabPages.isInitialized){
            tabPages.forEachIndexed { i,p -> p.visibility=if(i==index)View.VISIBLE else View.GONE }
        }
        if(::navButtons.isInitialized){
            navButtons.forEachIndexed { i,v ->
                v.setTextColor(if(i==index)0xff68e1d5.toInt() else 0xffaabbd0.toInt())
                v.background=if(i==index)gradient(0xff214a62.toInt(),0xff14283d.toInt(),15)else null
            }
        }
        if(::contentScroll.isInitialized)contentScroll.post{contentScroll.scrollTo(0,0)}
    }

    private fun renderHome(){
        if(!::homeContainer.isInitialized)return
        homeContainer.removeAllViews()
        val tv=VideoStore.load(this)
        val radio=StationStore.load(this)
        val hero=panel().apply{
            background=gradient(0xff1b5763.toInt(),0xff13263f.toInt(),23)
            setPadding(dp(20),dp(19),dp(20),dp(20))
        }
        hero.addView(label("TEKRAR HOŞ GELDİN",11,false).apply{setTextColor(0xff82efe3.toInt())})
        hero.addView(label("Ne izlemek veya\ndinlemek istersin?",26,true))
        hero.addView(label("${tv.size} TV kanalı  •  ${radio.size} radyo",14,false))
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        actions.addView(button("▶ TV Kanalları") { selectTab(1) },LinearLayout.LayoutParams(0,dp(48),1f))
        actions.addView(button("♫ Radyo") { selectTab(2) },LinearLayout.LayoutParams(0,dp(48),1f))
        hero.addView(actions)
        homeContainer.addView(hero)
        homeContainer.addView(section("HIZLI ERİŞİM","Kaldığın yerden devam et"))
        val quick=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        quick.addView(shortcut("▣","TV","Kanal listesi"){selectTab(1)},LinearLayout.LayoutParams(0,dp(120),1f))
        quick.addView(shortcut("♫","RADYO","Müzik ve yayın"){selectTab(2)},LinearLayout.LayoutParams(0,dp(120),1f))
        quick.addView(shortcut("＋","LİSTELER","M3U ekle"){selectTab(3)},LinearLayout.LayoutParams(0,dp(120),1f))
        homeContainer.addView(quick)
        val history=RecentStore.ids(this)
        if(history.isNotEmpty()){
            homeContainer.addView(section("SON AÇILANLAR","Geçmişinden seç"))
            history.take(8).forEach { value ->
                val id=value.substringAfter(':',"")
                when(value.substringBefore(':')){
                    "tv"->tv.find{it.id==id}?.let{homeContainer.addView(videoRow(it,true))}
                    "radio"->radio.find{it.id==id}?.let{homeContainer.addView(radioRow(it,true))}
                }
            }
        }
        val favorites=tv.filter{it.favorite}.take(3)
        if(favorites.isNotEmpty()){
            homeContainer.addView(section("FAVORİ TV KANALLARIN","Tek dokunuşla aç"))
            favorites.forEach{homeContainer.addView(videoRow(it,true))}
        }
        if(tv.isEmpty()){
            homeContainer.addView(section("İLK ADIM","Bir kanal listesi ekle"))
            val onboarding=panel()
            onboarding.addView(label("IPTV kitaplığın henüz boş.",18,true))
            onboarding.addView(label("Kendi M3U bağlantını ekleyebilir veya açık yayın listelerini deneyebilirsin.",13,false))
            onboarding.addView(button("＋  Kanal listesi ekle"){selectTab(3)})
            homeContainer.addView(onboarding)
        }
    }

    private fun shortcut(icon:String,title:String,description:String,action:()->Unit):LinearLayout{
        val view=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
            setPadding(dp(5),dp(8),dp(5),dp(8))
            background=gradient(0xff203853.toInt(),0xff162a43.toInt(),17)
            val lp=LinearLayout.LayoutParams(-1,-1)
            lp.setMargins(dp(3),dp(3),dp(3),dp(3))
            layoutParams=lp
            setOnClickListener{action()}
        }
        view.addView(label(icon,26,true).apply{setTextColor(0xff80e6dd.toInt())})
        view.addView(label(title,12,true))
        view.addView(label(description,10,false))
        return view
    }

    private fun section(title:String,subtitle:String):LinearLayout=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(dp(4),dp(15),dp(4),dp(9))
        addView(label(title,19,true))
        addView(label(subtitle,12,false))
    }

    private fun searchInput(hintText:String)=EditText(this).apply{
        hint=hintText
        setSingleLine(true)
        textSize=15f
        setTextColor(Color.WHITE)
        setHintTextColor(0xff8298b2.toInt())
        background=gradient(0xff223750.toInt(),0xff1b2c42.toInt(),13)
        setPadding(dp(14),dp(13),dp(14),dp(13))
        val lp=LinearLayout.LayoutParams(-1,dp(50))
        lp.setMargins(0,dp(6),0,dp(12))
        layoutParams=lp
    }

    private fun textAction(value:String,action:()->Unit):TextView=label(value,14,true).apply{
        gravity=Gravity.CENTER_VERTICAL
        setPadding(dp(14),dp(12),dp(12),dp(12))
        setOnClickListener{action()}
    }

    private fun gradient(start:Int,end:Int,radius:Int):GradientDrawable=
        GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(start,end)).apply{
            cornerRadius=dp(radius).toFloat()
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
            val item = panel()
            item.addView(button("▶   " + station.name) { playStation(station) })
            item.addView(label(station.group, 12, false))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun action(title: String, work: () -> Unit) {
                actions.addView(button(title, work), LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            action("Kontrol") { checkStream(station.name, station.url) }
            action(if (station.favorite) "★" else "☆") {
                StationStore.toggleFavorite(this, station.id); refresh()
            }
            action("TV'ye taşı") {
                if (VideoStore.add(this, station.name, station.url, station.group)) {
                    StationStore.remove(this, station.id); refresh(); refreshVideo()
                } else toast("Bu kanal zaten mevcut veya eklenemedi.")
            }
            action("Sil") { StationStore.remove(this, station.id); refresh() }
            item.addView(actions)
            listContainer.addView(item)
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
            val item = panel()
            item.addView(button("▶   " + channel.name) {
                startActivity(Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, channel.url)
                    .putExtra(PlayerActivity.EXTRA_TITLE, channel.name))
            })
            item.addView(label(channel.group, 12, false))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun action(title: String, work: () -> Unit) {
                actions.addView(button(title, work), LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            action("Kontrol") { checkStream(channel.name, channel.url) }
            action(if (channel.favorite) "★" else "☆") {
                VideoStore.toggleFavorite(this, channel.id); refreshVideo()
            }
            action("♪ Radyo") {
                if (StationStore.add(this, channel.name, channel.url, channel.group)) {
                    VideoStore.remove(this, channel.id); refreshVideo(); refresh()
                } else toast("Radyo listesine eklenemedi.")
            }
            action("Sil") { VideoStore.remove(this, channel.id); refreshVideo() }
            item.addView(actions)
            videoContainer.addView(item)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.rgb(23, 35, 55))
            cornerRadius = dp(14).toFloat()
        }
        val p = LinearLayout.LayoutParams(-1, -2)
        p.setMargins(0, dp(6), 0, dp(6))
        layoutParams = p
    }
    private fun button(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        textSize = 13f
        setTextColor(Color.WHITE)
        setAllCaps(false)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.rgb(30, 80, 141))
            cornerRadius = dp(12).toFloat()
        }
        setPadding(dp(8), dp(8), dp(8), dp(8))
        minHeight = dp(44)
        setOnClickListener { action() }
    }
    private fun label(value: String, size: Int, strong: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(if (strong) Color.WHITE else Color.rgb(175, 197, 220))
        setPadding(dp(4), dp(10), dp(4), dp(10))
        if (strong) setTypeface(null, android.graphics.Typeface.BOLD)
    }
    private fun styleInputs(root: View) {
        if (root is EditText) {
            root.setTextColor(Color.WHITE)
            root.setHintTextColor(Color.rgb(156, 177, 202))
            root.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(78, 131, 190))
        }
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) styleInputs(root.getChildAt(i))
        }
    }
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
