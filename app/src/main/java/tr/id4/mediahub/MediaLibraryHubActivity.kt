package tr.id4.mediahub

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Independent Mola-inspired film, series and sports library on the phone.
 * Works exclusively with sources imported or entered by the user.
 */
class MediaLibraryHubActivity : Activity() {
    private val mint=0xff7ce6cd.toInt()
    private val muted=0xffa7b9bb.toInt()
    private val black=0xff0b1316.toInt()
    private var selected=VideoLibraryKind.MOVIE
    private var favoritesOnly=false
    private var activeCategory:String?=null
    private lateinit var categoryChips:LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private lateinit var counter: TextView
    private lateinit var favorites: Button
    private lateinit var tabs: LinearLayout
    private var dynamicOpenMovies: List<OfficialMediaCard> = emptyList()

    private fun dp(n:Int)=(resources.displayMetrics.density*n).toInt()
    private fun background(a:Int,b:Int,r:Int=16)=
        GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(a,b)).apply{
            cornerRadius=dp(r).toFloat()
        }
    private fun text(v:String,size:Float=14f,color:Int=Color.WHITE,strong:Boolean=false)=
        TextView(this).apply{
            text=v; textSize=size;setTextColor(color)
            if(strong)setTypeface(typeface,Typeface.BOLD)
            maxLines=3
        }
    private fun button(v:String,action:()->Unit)=Button(this).apply{
        text=v;textSize=13f;isAllCaps=false;setTextColor(Color.WHITE)
        background=background(0xff32474a.toInt(),0xff1a292c.toInt())
        minHeight=dp(48);setOnClickListener{action()}
    }
    private fun space(v:Int)=View(this).apply{
        layoutParams=LinearLayout.LayoutParams(1,dp(v))
    }

    private lateinit var sessionHeader: MolaSessionHeader
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        selected=VideoLibraryKind.values().firstOrNull{
            it.name==intent.getStringExtra(EXTRA_KIND)
        }?.takeUnless{it==VideoLibraryKind.OTHER} ?: VideoLibraryKind.MOVIE
        window.statusBarColor=black;window.navigationBarColor=black
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(black)
            setPadding(dp(14),dp(12),dp(14),dp(10))
        }
        setContentView(root)
        sessionHeader=MolaSessionHeader(this)
        root.addView(sessionHeader.view)
        if(android.os.Build.VERSION.SDK_INT>=30){
            root.setOnApplyWindowInsetsListener{view,insets->
                val b=insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(dp(14),b.top+dp(8),dp(14),b.bottom+dp(8))
                insets
            }
        }
        val header=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        }
        val labelGroup=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        labelGroup.addView(text("ID.4 YOL ARKADAŞIM",12f,mint,true))
        labelGroup.addView(text("Film • Dizi • Maç",25f,Color.WHITE,true))
        header.addView(labelGroup,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("⌂ Ana sayfa"){finish()},
            LinearLayout.LayoutParams(dp(111),dp(48)))
        root.addView(header)
        root.addView(space(16))
        tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        root.addView(tabs)
        val categoryScroll=HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled=false
            isFillViewport=false
        }
        categoryChips=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding(0,dp(6),0,dp(6))
        }
        categoryScroll.addView(categoryChips)
        root.addView(categoryScroll)
        root.addView(space(7))
        root.addView(button("◈ TMDB film / dizi keşfet (afiş ve bilgiler)") {
            startActivity(Intent(this,TmdbCatalogActivity::class.java).apply {
                putExtra(TmdbCatalogActivity.EXTRA_KIND,
                    if (selected == VideoLibraryKind.SERIES)
                        TmdbKind.SERIES.name else TmdbKind.MOVIE.name)
            })
        },LinearLayout.LayoutParams(-1,dp(47)))
        root.addView(space(11))
        search=EditText(this).apply{
            hint="Film, dizi, maç veya kategori ara"
            setSingleLine(true);textSize=14f
            setTextColor(Color.WHITE);setHintTextColor(muted)
            background=background(0xff1b2b30.toInt(),0xff172327.toInt())
            setPadding(dp(14),dp(8),dp(14),dp(8))
            addTextChangedListener(object:TextWatcher{
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                    if(::content.isInitialized)refresh()
                }
                override fun afterTextChanged(s:Editable?){}
            })
        }
        root.addView(search,LinearLayout.LayoutParams(-1,dp(50)))
        root.addView(space(10))
        val controls=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        }
        counter=text("Kaynaklar yükleniyor",12f,muted)
        controls.addView(counter,LinearLayout.LayoutParams(0,-2,1f))
        favorites=button("☆ Favoriler"){favoritesOnly=!favoritesOnly;refresh()}
        controls.addView(favorites,LinearLayout.LayoutParams(dp(113),dp(47)))
        root.addView(controls)
        root.addView(space(9))
        val scroll=ScrollView(this).apply{isFillViewport=true}
        content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        scroll.addView(content)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val footer=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        footer.addView(button("＋ Kaynak ekle"){addSource()},
            LinearLayout.LayoutParams(0,dp(52),1f))
        footer.addView(button("⟳ Yenile"){refresh()},
            LinearLayout.LayoutParams(0,dp(52),.6f).apply{
                leftMargin=dp(8)
            })
        root.addView(space(8));root.addView(footer)
        refresh()
        BlenderMovieFeed.load{list->
            if(!isFinishing && !isDestroyed){
                dynamicOpenMovies=list
                refresh()
            }
        }
    }
    override fun onResume(){
        super.onResume()
        if(::content.isInitialized)refresh()
    }

    private fun updateTabs(){
        tabs.removeAllViews()
        listOf(VideoLibraryKind.MOVIE,VideoLibraryKind.SERIES,VideoLibraryKind.SPORT)
            .forEach{kind->
                val b=button(kind.title){selected=kind;activeCategory=null;refresh()}
                b.background=if(kind==selected)
                    background(0xff25796b.toInt(),0xff155a54.toInt())
                    else background(0xff1f3033.toInt(),0xff142124.toInt())
                b.setTextColor(if(kind==selected)Color.WHITE else muted)
                tabs.addView(b,LinearLayout.LayoutParams(0,dp(51),1f).apply{
                    rightMargin=dp(5)
                })
            }
    }

    private fun updateCategories(groups:List<MediaLibraryGroup>){
        categoryChips.removeAllViews()
        if(groups.isEmpty())return
        val names=listOf<String?>(null)+groups.take(30).map{it.name}
        names.forEach{category->
            val active=category==activeCategory
            val title=if(category==null)"Tümü" else
                category.take(22)+(if(category.length>22)"…" else "")
            val name=if(category==null)title else
                title+" ("+groups.first{it.name==category}.count+")"
            val chip=button(name){
                activeCategory=category
                refresh()
            }.apply {
                textSize=12f
                contentDescription=category ?: "Tüm kategoriler"
                background=if(active)
                    background(0xff247868.toInt(),0xff155a54.toInt())
                else background(0xff1c3437.toInt(),0xff15262a.toInt())
            }
            categoryChips.addView(chip,LinearLayout.LayoutParams(
                -2,dp(43)).apply{rightMargin=dp(7)})
        }
    }

    private fun refresh(){
        updateTabs()
        val imported=VideoStore.load(this)
        val recentIds=RecentStore.ids(this).mapNotNull {
            if(it.startsWith("tv:"))it.removePrefix("tv:") else null
        }
        // Only title IDs are persisted. No private playlist or URL in recent history.
        val resumable=if(selected==VideoLibraryKind.SPORT)emptySet()
            else recentIds.filter{VodProgressStore.position(this,it)>0L}.toSet()
        val library=MediaLibraryBrowsePlanner.plan(
            imported,selected,search.text.toString(),favoritesOnly,
            activeCategory,recentIds,resumable
        )
        updateCategories(library.categories)
        val official=(OfficialMediaDirectory.list(selected,search.text.toString()) +
            dynamicOpenMovies.filter{it.kind==selected &&
                (search.text.isBlank() || it.title.contains(search.text.toString(),true))})
            .distinctBy{if(it.mode==OfficialMediaMode.OPEN_MOVIE)it.target else it.id}
            .filter{!favoritesOnly || OfficialMediaFavorites.contains(this,it.id)}
        counter.text=selected.title+" · "+library.total+" kendi yayın • "+
            official.size+" resmî seçenek"
        favorites.text=if(favoritesOnly)"★ Favoriler" else "☆ Favoriler"
        content.removeAllViews()
        if(library.continueWatching.isNotEmpty()){
            content.addView(text("↻ KALDIĞIN YERDEN DEVAM ET",12f,mint,true).apply{
                setPadding(dp(7),dp(11),0,dp(10))
            })
            library.continueWatching.forEach{ch->
                content.addView(itemCard(ch),LinearLayout.LayoutParams(-1,-2).apply{
                    bottomMargin=dp(9)
                })
            }
            content.addView(space(6))
        }
        if(official.isNotEmpty()){
            content.addView(text("KEŞFET • RESMÎ SAĞLAYICILAR",12f,mint,true).apply{
                setPadding(dp(7),dp(11),0,dp(10))
            })
            official.forEach{source->
                content.addView(officialCard(source),LinearLayout.LayoutParams(-1,-2).apply{
                    bottomMargin=dp(9)
                })
            }
            content.addView(space(8))
        }
        content.addView(text("KENDİ KÜTÜPHANEM • "+library.total+" KAYIT",
            12f,mint,true).apply{setPadding(dp(7),dp(8),0,dp(8))})
        val channels=library.entries
        if(channels.isEmpty() && library.continueWatching.isEmpty()){
            content.addView(text(if(favoritesOnly)"Henüz favori içerik yok."
                else "Yukarıdaki resmî seçenekleri kullanabilir veya erişim hakkın olan "+
                    "HTTPS/M3U kaynağını kendi kütüphanene ekleyebilirsin.",
                15f,muted).apply{setPadding(dp(12),dp(30),dp(12),dp(15))})
        }else{
            channels.take(500).forEach{ch->
                content.addView(itemCard(ch),LinearLayout.LayoutParams(-1,-2).apply{
                    bottomMargin=dp(12)
                })
            }
            if(channels.size>500)content.addView(text(
                "İlk 500 kayıt gösteriliyor. Arama veya kategoriyle daralt.",12f,muted))
        }
    }

    private fun officialCard(src:OfficialMediaCard):LinearLayout{
        val accent=when(src.kind){
            VideoLibraryKind.MOVIE->0xffc5444b.toInt()
            VideoLibraryKind.SERIES->0xff725cc1.toInt()
            VideoLibraryKind.SPORT->0xff328f79.toInt()
            else->mint
        }
        val tile=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(12),dp(11),dp(12),dp(11))
            background=background(0xff202b31.toInt(),0xff131c24.toInt(),16)
            isClickable=true;isFocusable=true
            contentDescription=src.title+" · "+src.provider
            setOnClickListener{openOfficial(src)}
        }
        val poster=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
            background=background(accent,0xff19212e.toInt(),13)
        }
        if(src.imageUrl.isNotBlank()){
            val cover=ImageView(this).apply{
                scaleType=ImageView.ScaleType.CENTER_CROP
                setImageResource(android.R.drawable.ic_media_play)
            }
            poster.addView(cover,LinearLayout.LayoutParams(-1,-1))
            ChannelLogoLoader.into(src.imageUrl,cover,this)
        }else{
            poster.addView(text(when(src.mode){
                OfficialMediaMode.OPEN_MOVIE->"▶"
                OfficialMediaMode.PROVIDER_APP->"▣"
                OfficialMediaMode.PROVIDER_WEB->"▦"
                OfficialMediaMode.TV_CHANNEL->"●"
            },31f,Color.WHITE,true))
            poster.addView(text(if(src.provider=="Blender Open Movies")"OPEN"
                else src.provider.take(7),10f,Color.WHITE,true))
        }
        tile.addView(poster,LinearLayout.LayoutParams(dp(84),dp(92)))
        val labels=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(12),0,dp(6),0)
        }
        labels.addView(text(src.title,16f,Color.WHITE,true))
        labels.addView(space(3))
        labels.addView(text(src.detail,12f,muted))
        labels.addView(space(5))
        labels.addView(text(when(src.mode){
            OfficialMediaMode.OPEN_MOVIE->if(VodProgressStore.position(this,src.id)>0L)
                "↻ "+(VodProgressStore.position(this,src.id)/60_000L)+". dakikadan devam"
                else "▶ Açık filmi oynat"
            OfficialMediaMode.PROVIDER_APP->"↗ Resmî uygulamayı aç"
            OfficialMediaMode.PROVIDER_WEB->"↗ Resmî yayın bilgisini aç"
            OfficialMediaMode.TV_CHANNEL->"▶ Canlı yayını dene"
        },12f,mint,true))
        tile.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        val isFav=OfficialMediaFavorites.contains(this,src.id)
        tile.addView(button(if(isFav)"★" else "☆"){
            OfficialMediaFavorites.toggle(this,src.id)
            refresh()
        }.apply{contentDescription=if(isFav)
            src.title+" favorilerden çıkar" else src.title+" favorilere ekle"},
            LinearLayout.LayoutParams(dp(47),dp(48)))
        tile.addView(text("›",28f,accent,true))
        return tile
    }

    private fun openOfficial(src:OfficialMediaCard){
        when(src.mode){
            OfficialMediaMode.PROVIDER_APP->
                InstalledMediaApps.open(this,src.provider,src.target)
            OfficialMediaMode.PROVIDER_WEB->{
                AlertDialog.Builder(this)
                    .setTitle(src.title)
                    .setMessage(src.rightNote+"\n\nResmî sağlayıcının sayfası açılacak.")
                    .setNegativeButton("İptal",null)
                    .setPositiveButton("Resmî siteyi aç"){_,_->
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse(src.target)))
                        } catch (_:Exception) {
                            Toast.makeText(this,"Site açılamadı",Toast.LENGTH_SHORT).show()
                        }
                    }.show()
            }
            OfficialMediaMode.OPEN_MOVIE-> {
                val dialog=AlertDialog.Builder(this)
                    .setTitle(src.title)
                    .setMessage("Blender'ın açık lisanslı videosunun oynatma adresi alınıyor…")
                    .setNegativeButton("İptal",null)
                    .create()
                dialog.show()
                BlenderOpenVideo.resolve(src.target){outcome->
                    dialog.dismiss()
                    if(isFinishing || isDestroyed)return@resolve
                    outcome.fold(onSuccess={url->
                        RecentStore.remember(this,src.id,"tv")
                        startActivity(Intent(this,PlayerActivity::class.java).apply{
                            putExtra(PlayerActivity.EXTRA_TITLE,src.title)
                            putExtra(PlayerActivity.EXTRA_URL,url)
                            putExtra(PlayerActivity.EXTRA_ID,src.id)
                            putExtra(PlayerActivity.EXTRA_IS_VOD,true)
                        })
                    },onFailure={
                        AlertDialog.Builder(this)
                            .setTitle(src.title)
                            .setMessage("Blender oynatma bağlantısı şu an alınamadı. "+
                                "Sunucu geçici olarak ulaşılamıyor olabilir. "+
                                "İstersen resmî Blender video sayfasını açabilirsin.")
                            .setNegativeButton("Kapat",null)
                            .setPositiveButton("Blender'da aç"){_,_->
                                BlenderOpenVideo.officialPage(src.target)?.let{url->
                                    try{
                                        startActivity(Intent(Intent.ACTION_VIEW,
                                            android.net.Uri.parse(url)))
                                    }catch(_:Exception){}
                                }
                            }.show()
                    })
                }
            }
            OfficialMediaMode.TV_CHANNEL-> {
                Toast.makeText(this,"Açık kanal bağlantısı için Kaynaklar'ı kullan",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun itemCard(ch:VideoChannel):LinearLayout{
        val tint=when(selected){
            VideoLibraryKind.MOVIE->0xffa82c45.toInt()
            VideoLibraryKind.SERIES->0xff7764a9.toInt()
            VideoLibraryKind.SPORT->0xff198e8b.toInt()
            else->0xff376375.toInt()
        }
        val card=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(10),dp(10),dp(10),dp(10))
            background=background(0xff1b3034.toInt(),0xff111d22.toInt(),19)
            isClickable=true;isFocusable=true
            setOnClickListener{play(ch)}
            contentDescription=ch.name+" • Oynat"
        }
        val poster=LinearLayout(this).apply{
            gravity=Gravity.CENTER
            background=background(tint,0xff1b3037.toInt(),13)
        }
        if(ch.logo.isNotBlank()){
            val pic=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER}
            ChannelLogoLoader.into(ch.logo,pic,this)
            poster.addView(pic,LinearLayout.LayoutParams(-1,-1))
        }else{
            poster.addView(text(when(selected){
                VideoLibraryKind.MOVIE->"▣"
                VideoLibraryKind.SERIES->"▤"
                VideoLibraryKind.SPORT->"⚽"
                else->"▶"
            },35f,Color.WHITE,true))
        }
        card.addView(poster,LinearLayout.LayoutParams(dp(88),dp(105)))
        val info=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(12),0,dp(5),0)
        }
        info.addView(text(ch.name,16f,Color.WHITE,true).apply{
            maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
        })
        info.addView(space(5))
        info.addView(text(ch.group,12f,muted).apply{
            maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
        })
        if(selected!=VideoLibraryKind.SPORT){
            val ms=VodProgressStore.position(this,ch.id)
            if(ms>0){
                info.addView(space(5))
                info.addView(text("↻ "+(ms/60_000L)+". dakikadan devam",11f,mint))
            }
        }
        info.addView(space(8))
        info.addView(text("▶ İzle",13f,mint,true))
        card.addView(info,LinearLayout.LayoutParams(0,-2,1f))
        card.addView(button(if(ch.favorite)"★" else "☆"){
            VideoStore.toggleFavorite(this,ch.id);refresh()
        },LinearLayout.LayoutParams(dp(48),dp(49)))
        return card
    }

    private fun play(ch:VideoChannel){
        RecentStore.remember(this,ch.id,"tv")
        startActivity(Intent(this,PlayerActivity::class.java).apply{
            putExtra(PlayerActivity.EXTRA_URL,ch.url)
            putExtra(PlayerActivity.EXTRA_TITLE,ch.name)
            putExtra(PlayerActivity.EXTRA_ID,ch.id)
            putExtra(PlayerActivity.EXTRA_IS_VOD,selected!=VideoLibraryKind.SPORT)
        })
    }

    private fun addSource(){
        val form=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(10),dp(18),dp(5))
        }
        val title=EditText(this).apply{
            hint="İçerik adı / varsa bölüm numarası";setSingleLine(true)
        }
        val address=EditText(this).apply{
            hint="https://.../video.m3u8 veya video.mp4"
            setSingleLine(true)
            inputType=android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        form.addView(title);form.addView(address)
        AlertDialog.Builder(this)
            .setTitle(selected.title+" • İçerik ekle")
            .setMessage("Yalnızca kullanım hakkın olan HTTPS medya bağlantısını ekle.")
            .setView(form)
            .setNegativeButton("İptal",null)
            .setPositiveButton("Kaydet"){_,_->
                if(VideoStore.add(this,title.text.toString().trim(),
                        address.text.toString().trim(),selected.sourceGroup)){
                    Toast.makeText(this,"İçerik eklendi",Toast.LENGTH_SHORT).show()
                    refresh()
                }else Toast.makeText(this,"HTTPS adresi geçersiz veya kaynak zaten var.",
                    Toast.LENGTH_LONG).show()
            }.show()
    }
    companion object { const val EXTRA_KIND="media_library_kind" }
    override fun onDestroy(){
        if(::sessionHeader.isInitialized)sessionHeader.release()
        super.onDestroy()
    }
}
