package tr.id4.mediahub

import android.app.Activity
import android.app.AlertDialog
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
import android.view.WindowInsets
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
    private lateinit var tvPlayer: TvPlayback
    private lateinit var radioDashboard: RadioDashboard
    private var radioDirectoryLoading = false
    private var currentRadioId: String? = null
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
        // Android 15 enforces edge-to-edge drawing: keep the bottom nav above OS controls.
        root.setOnApplyWindowInsetsListener { _, insets ->
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            root.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(2), dp(16), dp(2))
            background = gradient(0xff172c48.toInt(), 0xff0b1423.toInt(), 0)
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brandRow.addView(label("ID⁴", 26, true).apply { setTextColor(0xff66dddb.toInt()) })
        val brandTitles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10),0,0,0)
        }
        brandTitles.addView(label("MEDIA HUB", 20, true))
        brandTitles.addView(label("TV  ·  RADYO  ·  KİTAPLIK", 10, false))
        brandRow.addView(brandTitles, LinearLayout.LayoutParams(0,-2,1f))
        val add = textAction("＋") { selectTab(3) }
        brandRow.addView(add, LinearLayout.LayoutParams(dp(46),dp(48)))
        top.addView(brandRow)
        status = label("● Bağlantı hazırlanıyor", 11, false).apply {
            setTextColor(0xff8de1d3.toInt())
            setPadding(dp(3), 0, dp(3), dp(2))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
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

        // Video is embedded in a single persistent player instead of a long vertical channel list.
        tvPlayer = TvPlayback(
            this,
            { VideoStore.load(this) },
            { selectTab(3) },
            { channel ->
                RecentStore.remember(this, channel.id, "tv")
                renderHome()
            }
        )
        val sportzTv = panel().apply {
            background = gradient(0xff173d58.toInt(),0xff182941.toInt(),16)
            setPadding(dp(12),dp(9),dp(12),dp(9))
        }
        val sportzRow = LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val sportzTitle = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        sportzTitle.addView(label("SPORTZX",17,true))
        sportzTitle.addView(label("Yüklü Android uygulamasını doğrudan aç",11,false))
        sportzRow.addView(sportzTitle,LinearLayout.LayoutParams(0,-2,1f))
        sportzRow.addView(button("Aç  ↗") { SportzXLauncher.openIfInstalled(this) },
            LinearLayout.LayoutParams(dp(96),dp(47)))
        sportzTv.addView(sportzRow)
        val mediaApps = panel().apply {
            background=gradient(0xff162c48.toInt(),0xff172c3c.toInt(),16)
        }
        mediaApps.addView(label("WEB MEDYA • UYGULAMA İÇİNDE",16,true))
        mediaApps.addView(label("Uygulama içi web oynatıcı: YouTube ve desteklenen HTTPS videoları. Netflix DRM ayrı uygulamada çalışır.",12,false))
        val appActions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        appActions.addView(button("▶ YouTube Web") { openWebMedia(MediaWebActivity.YOUTUBE_URL) },
            LinearLayout.LayoutParams(0,dp(50),1f))
        appActions.addView(button("▣ Netflix Web") { openWebMedia(MediaWebActivity.NETFLIX_URL) },
            LinearLayout.LayoutParams(0,dp(50),1f))
        mediaApps.addView(appActions)
        mediaApps.addView(button("🌐 Web Medya • HTTPS video bağlantısı aç") {
            openWebMedia(MediaWebActivity.YOUTUBE_URL)
        })
        television.addView(mediaApps)
        television.addView(sportzTv)
        television.addView(tvPlayer.view())
        television.addView(label("Kanal listesi ve yayın adresleri Kitaplığım bölümünde saklanır.", 12, false))

        radioDashboard = RadioDashboard(
            this,
            { StationStore.load(this) },
            { station -> playStation(station) },
            { toggleRadioPlayback() },
            { direction -> skipRadio(direction) },
            { syncRadioCatalogue(true) },
            { id ->
                StationStore.toggleFavorite(this, id)
                refresh()
                renderHome()
            }
        )
        radio.addView(radioDashboard.view())

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
        ready.addView(button("🌍 Dünya kanalları • Ülke seç") { showWorldPicker(true) })
        ready.addView(button("▦ Kategoriye göre kanal ara") { showWorldPicker(false) })
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
        external.addView(textAction("▶  YouTube'u uygulama içinde aç") {
            openWebMedia(MediaWebActivity.YOUTUBE_URL)
        })
        external.addView(textAction("▣  Netflix Web • Media Hub içinde") {
            openWebMedia(MediaWebActivity.NETFLIX_URL)
        })
        external.addView(textAction("▣  Netflix uygulamasına geç") {
            InstalledMediaApps.openNetflix(this)
        })
        external.addView(textAction("◉  SportzX uygulamasını aç") { SportzXLauncher.openIfInstalled(this) })
        external.addView(label("SportzX yüklüyse doğrudan açılır. Tarayıcıya yönlendirme yapılmaz.",12,false))
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
        utilities.addView(textAction("🚗 ID.4 / Android Auto bağlantı raporu") {
            CarCompatibility.show(this)
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
                    connected.addListener(object:Player.Listener{
                        override fun onIsPlayingChanged(isPlaying:Boolean) {
                            val selected = StationStore.load(this@MainActivity)
                                .firstOrNull{it.id==connected.currentMediaItem?.mediaId}
                            if(selected!=null)currentRadioId=selected.id
                            if(::radioDashboard.isInitialized)
                                radioDashboard.setPlaying(selected,isPlaying)
                        }
                    })
                    status.text="●   Ses servisi hazır"
                }catch (_:Exception){
                    if(!isFinishing && !isDestroyed)status.text="●   Ses servisine bağlanılamadı"
                }
            },mainExecutor)
        }
        syncRadioCatalogue(false)
    }

    /** Community-listed playlists are user-selected, not bundled broadcasts. */
    private fun showWorldPicker(byCountry: Boolean) {
        val sources = if (byCountry) WorldCatalog.countries() else WorldCatalog.genres
        val visible = sources.toMutableList()
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(6))
        }
        val input = searchInput(if (byCountry) "Ülke ara" else "Kategori ara")
        wrapper.addView(input)
        val adapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_list_item_1, visible.map { it.name }.toMutableList()
        )
        val list = android.widget.ListView(this).apply { this.adapter = adapter }
        wrapper.addView(list, LinearLayout.LayoutParams(-1, dp(340)))
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (byCountry) "Ülkeye göre IPTV" else "Kategoriye göre IPTV")
            .setView(wrapper)
            .setNegativeButton("Kapat", null)
            .create()
        input.addTextChangedListener(watch {
            val filter = input.text.toString().trim()
            visible.clear()
            visible.addAll(sources.filter { it.name.contains(filter, ignoreCase = true) })
            adapter.clear()
            adapter.addAll(visible.map { it.name })
            adapter.notifyDataSetChanged()
        })
        list.setOnItemClickListener { _, _, position, _ ->
            val chosen = visible.getOrNull(position) ?: return@setOnItemClickListener
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle(chosen.name)
                .setMessage("İptv-org topluluk dizininden liste içe aktarılacak. Yayınların " +
                    "çalışması, kullanım hakkı ve bölgesel erişimi garanti değildir.")
                .setNegativeButton("İptal", null)
                .setPositiveButton("Listeyi yükle") { _, _ -> importFromHttps(chosen.url) }
                .show()
        }
        dialog.show()
    }

    private fun openWebMedia(url: String) {
        try {
            startActivity(Intent(this, MediaWebActivity::class.java)
                .putExtra(MediaWebActivity.EXTRA_URL, url))
        } catch (_: Exception) {
            toast("Web Medya başlatılamadı; APK kurulumunu kontrol edin.")
        }
    }

    private fun toggleRadioPlayback() {
        val ctrl=controller ?: return toast("Radyo servisi henüz hazır değil")
        if(ctrl.isPlaying) { ctrl.pause();return }
        if(ctrl.currentMediaItem!=null){ctrl.play();return}
        val first=StationStore.load(this).firstOrNull()
        if(first!=null)playStation(first) else syncRadioCatalogue(true)
    }

    private fun skipRadio(direction:Int) {
        val stations=StationStore.load(this)
        if(stations.isEmpty())return
        val index=stations.indexOfFirst{it.id==currentRadioId}
        val next=if(index<0)0 else (index+direction+stations.size)%stations.size
        playStation(stations[next])
    }

    private fun syncRadioCatalogue(force:Boolean) {
        if (radioDirectoryLoading) return
        if (!force && !RadioDirectory.shouldRefresh(this)) {
            if(::radioDashboard.isInitialized)
                radioDashboard.message("Kayıtlı Türkiye kataloğu hazır • Güncelle ile yenileyebilirsin")
            return
        }
        radioDirectoryLoading=true
        if(::radioDashboard.isInitialized)
            radioDashboard.message("Türkiye radyoları indiriliyor…")
        val context=applicationContext
        Thread {
            val result=try{
                RadioDirectory.loadAndMerge(context)
            }catch (_: Exception){
                -1
            }
            runOnUiThread {
                radioDirectoryLoading=false
                if(!isFinishing && !isDestroyed){
                    if(::radioDashboard.isInitialized){
                        radioDashboard.message(
                            if(result>=0)"Katalog güncellendi • "+result+" yeni radyo eklendi"
                            else "İnternet kataloğuna ulaşılamadı • Kayıtlı radyolar kullanılabilir"
                        )
                        radioDashboard.refresh()
                    }
                    renderHome()
                }
            }
        }.start()
    }

    private fun watch(action:()->Unit):TextWatcher=object:TextWatcher{
        override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
        override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){action()}
        override fun afterTextChanged(s:Editable?){}
    }

    private fun selectTab(index:Int){
        if (index != 1 && ::tvPlayer.isInitialized) tvPlayer.pause()
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
        val sportzHome=panel().apply{
            background=gradient(0xff20344f.toInt(),0xff102d45.toInt(),16)
        }
        sportzHome.addView(label("SPOR & DİĞER UYGULAMALAR",13,true))
        sportzHome.addView(button("◉  SportzX'i doğrudan aç") {
            SportzXLauncher.openIfInstalled(this)
        })
        val appsRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        appsRow.addView(button("▶ YouTube Web") { openWebMedia(MediaWebActivity.YOUTUBE_URL) },
            LinearLayout.LayoutParams(0,dp(49),1f))
        appsRow.addView(button("▣ Netflix Web") { openWebMedia(MediaWebActivity.NETFLIX_URL) },
            LinearLayout.LayoutParams(0,dp(49),1f))
        sportzHome.addView(appsRow)
        sportzHome.addView(button("🌐 Web Medya • Sekmeler ve favoriler") {
            openWebMedia(MediaWebActivity.YOUTUBE_URL)
        })
        homeContainer.addView(sportzHome)
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
        currentRadioId = station.id
        if (::radioDashboard.isInitialized) radioDashboard.setPlaying(station, true)
        RecentStore.remember(this,station.id,"radio")
        renderHome()
        status.text = "♫  ${station.name}"
    }

    private fun radioRow(station:Station,compact:Boolean=false):LinearLayout {
        return mediaCard(station.name, station.group,station.logo,station.favorite,false,{
            playStation(station)
        },{
            StationStore.toggleFavorite(this,station.id);refresh();renderHome()
        },{
            val menu=PopupMenu(this,it)
            menu.menu.add("Bağlantıyı kontrol et").setOnMenuItemClickListener{
                checkStream(station.name,station.url);true
            }
            menu.menu.add("TV listesine taşı").setOnMenuItemClickListener{
                if(VideoStore.add(this,station.name,station.url,station.group)){
                    StationStore.remove(this,station.id);refresh();refreshVideo();renderHome()
                }else toast("Kanal taşınamadı")
                true
            }
            menu.menu.add("Listeden kaldır").setOnMenuItemClickListener{
                StationStore.remove(this,station.id);refresh();renderHome();true
            }
            menu.show()
        },compact)
    }

    private fun videoRow(channel:VideoChannel,compact:Boolean=false):LinearLayout =
        mediaCard(channel.name,channel.group,channel.logo,channel.favorite,true,{
            if (::tvPlayer.isInitialized) {
                selectTab(1)
                tvPlayer.play(channel)
            } else toast("TV oynatıcı hazırlanıyor")
        },{
            VideoStore.toggleFavorite(this,channel.id);refreshVideo();renderHome()
        },{
            val menu=PopupMenu(this,it)
            menu.menu.add("Bağlantıyı kontrol et").setOnMenuItemClickListener{
                checkStream(channel.name,channel.url);true
            }
            menu.menu.add("Radyo listesine taşı").setOnMenuItemClickListener{
                if(StationStore.add(this,channel.name,channel.url,channel.group)){
                    VideoStore.remove(this,channel.id);refreshVideo();refresh();renderHome()
                }else toast("Kanal taşınamadı")
                true
            }
            menu.menu.add("Listeden kaldır").setOnMenuItemClickListener{
                VideoStore.remove(this,channel.id);refreshVideo();renderHome();true
            }
            menu.show()
        },compact)

    private fun mediaCard(
        title:String,category:String,logo:String,favorite:Boolean,video:Boolean,
        open:()->Unit,toggle:()->Unit,menu:(View)->Unit,compact:Boolean=false
    ):LinearLayout {
        val card=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(10),dp(8),dp(6),dp(8))
            background=gradient(0xff1b304c.toInt(),0xff15263b.toInt(),15)
            val lp=LinearLayout.LayoutParams(-1,dp(if(compact)76 else 88))
            lp.setMargins(0,dp(4),0,dp(4))
            layoutParams=lp
            setOnClickListener{open()}
        }
        val mark=FrameLayout(this).apply{
            background=gradient(0xff326381.toInt(),0xff29405b.toInt(),12)
        }
        val initials=title.split(' ').filter{it.isNotBlank()}.take(2).joinToString(""){it.take(1)}.uppercase().take(2)
        mark.addView(label(initials.ifBlank{if(video)"TV" else "♫"},18,true).apply{
            gravity=Gravity.CENTER
            setTextColor(0xffc3fcf0.toInt())
            setPadding(0,0,0,0)
        },FrameLayout.LayoutParams(-1,-1))
        if(logo.isNotBlank()){
            val image=ImageView(this).apply{
                scaleType=ImageView.ScaleType.FIT_CENTER
                alpha=0f
                setPadding(dp(4),dp(4),dp(4),dp(4))
            }
            mark.addView(image,FrameLayout.LayoutParams(-1,-1))
            ChannelLogoLoader.into(logo,image,this)
        }
        card.addView(mark,LinearLayout.LayoutParams(dp(56),dp(56)))
        val texts=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(12),0,dp(4),0)
        }
        texts.addView(label(title,15,true).apply{
            maxLines=2
            ellipsize=android.text.TextUtils.TruncateAt.END
            setPadding(0,0,0,0)
        })
        texts.addView(label((if(video)"●  CANLI TV  •  " else "♫  RADYO  •  ")+category,11,false).apply{
            maxLines=1
            ellipsize=android.text.TextUtils.TruncateAt.END
            setPadding(0,dp(5),0,0)
        })
        card.addView(texts,LinearLayout.LayoutParams(0,-1,1f))
        val star=textAction(if(favorite)"★" else "☆"){toggle()}.apply{
            gravity=Gravity.CENTER
            textSize=22f
            setTextColor(if(favorite)0xffffd17c.toInt() else 0xff9fb3c9.toInt())
        }
        card.addView(star,LinearLayout.LayoutParams(dp(40),dp(50)))
        val more=textAction("⋮"){}.apply{
            gravity=Gravity.CENTER
            textSize=26f
            setTextColor(0xffb8c9df.toInt())
            setOnClickListener{menu(this)}
        }
        card.addView(more,LinearLayout.LayoutParams(dp(26),dp(50)))
        return card
    }

    private fun refresh() {
        if (::radioDashboard.isInitialized) radioDashboard.refresh()
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
        renderHome()
    }

    private fun refreshVideo() {
        if (::tvPlayer.isInitialized) tvPlayer.refresh()
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
    private fun button(title: String, secondary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = title
        textSize = 13f
        setTextColor(Color.WHITE)
        setAllCaps(false)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if(secondary)0xff29435e.toInt() else 0xff126b91.toInt())
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
        if (::tvPlayer.isInitialized) tvPlayer.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
