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
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private lateinit var counter: TextView
    private lateinit var favorites: Button
    private lateinit var tabs: LinearLayout

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
    }
    override fun onResume(){
        super.onResume()
        if(::content.isInitialized)refresh()
    }

    private fun updateTabs(){
        tabs.removeAllViews()
        listOf(VideoLibraryKind.MOVIE,VideoLibraryKind.SERIES,VideoLibraryKind.SPORT)
            .forEach{kind->
                val b=button(kind.title){selected=kind;refresh()}
                b.background=if(kind==selected)
                    background(0xff25796b.toInt(),0xff155a54.toInt())
                    else background(0xff1f3033.toInt(),0xff142124.toInt())
                b.setTextColor(if(kind==selected)Color.WHITE else muted)
                tabs.addView(b,LinearLayout.LayoutParams(0,dp(51),1f).apply{
                    rightMargin=dp(5)
                })
            }
    }

    private fun refresh(){
        updateTabs()
        val channels=VideoLibraryClassifier.filtered(VideoStore.load(this),selected,
            search.text.toString(),favoritesOnly)
        counter.text=selected.title+" · "+channels.size+" içerik"
        favorites.text=if(favoritesOnly)"★ Favoriler" else "☆ Favoriler"
        content.removeAllViews()
        if(channels.isEmpty()){
            content.addView(text(if(favoritesOnly)"Henüz favori içerik yok."
                else "Henüz "+selected.title.lowercase()+" kaynağı yok.\n\n"+
                    "Erişim hakkın olan HTTPS video/kanal bağlantısını ekle "+
                    "veya Kaynaklar bölümünden yasal M3U listenizi içe aktar.",
                15f,muted).apply{setPadding(dp(12),dp(45),dp(12),dp(15))})
        }else{
            channels.take(500).forEach{ch->
                content.addView(itemCard(ch),LinearLayout.LayoutParams(-1,-2).apply{
                    bottomMargin=dp(12)
                })
            }
            if(channels.size>500)content.addView(text("İlk 500 kayıt gösteriliyor. Aramayla daralt.",12f,muted))
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
}
