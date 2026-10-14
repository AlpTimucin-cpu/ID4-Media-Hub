package tr.id4.mediahub

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.webkit.WebView
import java.util.concurrent.Executors

/** TMDB film/TV discovery with original phone UI; never a source of copyrighted streams. */
class TmdbDiscoverActivity: Activity() {
    private var series=false
    private lateinit var page:LinearLayout
    private lateinit var search:EditText
    private lateinit var status:TextView
    private lateinit var movies:Button
    private lateinit var tv:Button
    private val backgroundColor=0xff0b1418.toInt()
    private val mint=0xff71e1cc.toInt()
    private val muted=0xffb0c0c6.toInt()
    private val executor=Executors.newSingleThreadExecutor()
    private var taskId=0

    private fun dp(n:Int)=(resources.displayMetrics.density*n).toInt()
    private fun color(a:Int,b:Int,r:Int=15)=
        GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(a,b)).apply {
            cornerRadius=dp(r).toFloat()
        }
    private fun text(v:String,size:Float=14f,c:Int=Color.WHITE,strong:Boolean=false)=
        TextView(this).apply {
            text=v;textSize=size;setTextColor(c)
            if(strong)setTypeface(typeface,Typeface.BOLD)
        }
    private fun action(v:String,doIt:()->Unit)=Button(this).apply {
        text=v;textSize=12.5f;isAllCaps=false;setTextColor(Color.WHITE)
        background=color(0xff215459.toInt(),0xff15353a.toInt())
        setOnClickListener{doIt()}
    }
    private fun spacer(n:Int)=View(this).apply{
        layoutParams=LinearLayout.LayoutParams(1,dp(n))
    }

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        series=intent.getBooleanExtra(EXTRA_SERIES,false)
        window.statusBarColor=backgroundColor
        window.navigationBarColor=backgroundColor
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(14),dp(12),dp(14),dp(12))
        }
        setContentView(root)
        if(android.os.Build.VERSION.SDK_INT>=30) {
            root.setOnApplyWindowInsetsListener{v,insets ->
                val i=insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(dp(14),i.top+dp(9),dp(14),i.bottom+dp(9))
                insets
            }
        }

        val header=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        }
        val labels=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        labels.addView(text("ID.4 YOL ARKADAŞIM",11f,mint,true))
        labels.addView(text("Film / Dizi Keşfet",23f,Color.WHITE,true))
        header.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(action("⌂ Geri"){finish()},
            LinearLayout.LayoutParams(dp(84),dp(47)))
        root.addView(header)
        root.addView(spacer(12))

        val categories=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        movies=action("Filmler"){series=false;switchTab();load()}
        tv=action("Diziler"){series=true;switchTab();load()}
        categories.addView(movies,LinearLayout.LayoutParams(0,dp(48),1f))
        categories.addView(tv,LinearLayout.LayoutParams(0,dp(48),1f).apply{leftMargin=dp(7)})
        root.addView(categories)
        root.addView(spacer(9))
        switchTab()

        val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        search=EditText(this).apply{
            setSingleLine(true);hint="Film veya dizi ara..."
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            textSize=14f
            background=color(0xff24343b.toInt(),0xff162a30.toInt())
            setPadding(dp(12),dp(8),dp(12),dp(8))
            setOnEditorActionListener{_,_,_->load();true}
        }
        controls.addView(search,LinearLayout.LayoutParams(0,dp(49),1f))
        controls.addView(action("Ara"){load()},LinearLayout.LayoutParams(dp(68),dp(49)).apply{
            leftMargin=dp(7)
        })
        root.addView(controls)
        root.addView(spacer(8))

        val management=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        management.addView(action("🔑 TMDB erişimi"){tokenDialog()},
            LinearLayout.LayoutParams(0,dp(45),1f))
        management.addView(action("ⓘ Kaynak / Atıf"){credits()},
            LinearLayout.LayoutParams(0,dp(45),1f).apply{leftMargin=dp(7)})
        root.addView(management)
        root.addView(spacer(7))
        status=text("",12f,muted)
        root.addView(status)
        root.addView(spacer(8))
        val scroll=ScrollView(this)
        page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        scroll.addView(page)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        if(TmdbCatalogClient.hasToken(this))load()
        else showEmpty("TMDB'nin ücretsiz API Read Access Token bilgisini 'TMDB erişimi' bölümünden ekle. Film/dizi bilgileri TMDB üzerinden görüntülenir; video yayınları dahil değildir.")
    }

    private fun switchTab(){
        if(!::movies.isInitialized)return
        movies.background=if(!series)color(0xff1f8579.toInt(),0xff12514f.toInt())
            else color(0xff293b42.toInt(),0xff18282e.toInt())
        tv.background=if(series)color(0xff1f8579.toInt(),0xff12514f.toInt())
            else color(0xff293b42.toInt(),0xff18282e.toInt())
    }

    private fun showEmpty(message:String){
        if(!::page.isInitialized)return
        page.removeAllViews()
        page.addView(text(message,14f,muted).apply {
            setPadding(dp(10),dp(35),dp(10),dp(20))
        })
        status.text=if(TmdbCatalogClient.hasToken(this))"TMDB film/dizi bilgileri" else "TMDB anahtarı gerekli"
    }

    private fun load(){
        if(!TmdbCatalogClient.hasToken(this)){
            showEmpty("Önce kendi TMDB API Read Access Token bilgini ekle.")
            return
        }
        val id=++taskId
        val category=series
        val query=search.text.toString()
        status.text="TMDB'den "+(if(category)"dizi" else "film")+" listesi yükleniyor..."
        page.removeAllViews()
        executor.execute {
            val result=runCatching{TmdbCatalogClient.search(this,category,query)}
            runOnUiThread {
                if(isDestroyed || isFinishing || taskId!=id)return@runOnUiThread
                result.onSuccess { data ->
                    status.text= "TMDB • "+data.size+" sonuç · Bilgi ve afiş"
                    if(data.isEmpty())showEmpty("Bu arama için içerik bulunamadı.")
                    else render(data)
                }.onFailure { e ->
                    showEmpty("TMDB'ye bağlanılamadı. Ağ bağlantısını ve anahtarını kontrol et.")
                    status.text=e.message?.take(100) ?: "Bağlantı başarısız"
                }
            }
        }
    }

    private fun render(results:List<TmdbTitle>){
        page.removeAllViews()
        results.forEach { item ->
            val row=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
                setPadding(dp(10),dp(9),dp(10),dp(9))
                background=color(0xff20343a.toInt(),0xff121e26.toInt(),16)
                isClickable=true;isFocusable=true
                contentDescription=item.name+" · ayrıntılar"
                setOnClickListener{details(item)}
            }
            val poster=ImageView(this).apply{
                scaleType=ImageView.ScaleType.CENTER_CROP
                setImageResource(android.R.drawable.ic_media_play)
            }
            if(item.posterUrl.isNotBlank()) ChannelLogoLoader.into(item.posterUrl,poster,this)
            row.addView(poster,LinearLayout.LayoutParams(dp(85),dp(124)))
            val labels=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL
                setPadding(dp(12),dp(1),dp(4),dp(1))
            }
            labels.addView(text(item.name,15f,Color.WHITE,true).apply{
                maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
            })
            labels.addView(text(
                (if(item.year.isNotBlank())item.year+" · " else "")+
                    String.format(java.util.Locale.US,"%.1f/10",item.rating),
                11f,mint))
            labels.addView(spacer(8))
            labels.addView(text(item.overview.ifBlank{"Özet bulunmuyor."},12f,muted).apply{
                maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.END
            })
            labels.addView(spacer(8))
            labels.addView(text("Bilgi ve yerel kaynak kontrolü ›",11f,mint))
            row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
            page.addView(row,LinearLayout.LayoutParams(-1,-2).apply{
                bottomMargin=dp(9)
            })
        }
        page.addView(text(
            "TMDB film ve dizi bilgisi sunar; bu liste video akışı sağlamaz.",
            12f,muted).apply{setPadding(0,dp(6),0,dp(13))})
    }

    private fun details(item:TmdbTitle){
        val local=TmdbCatalogClient.matchingLocalTitle(item,VideoStore.load(this))
        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setMessage(item.overview.ifBlank{"Açıklama bulunamadı."}+
                "\n\n"+(if(local!=null)"Kütüphanende eşleşen yerel yayın bulundu."
                        else "Kütüphanende aynı adlı lisanslı video bağlantısı bulunamadı.")+
                "\n\nTMDB video akışı sağlamaz.")
            .setPositiveButton(if(local!=null)"Kütüphanemde oynat" else "Tamam"){_,_->
                if(local!=null)startActivity(Intent(this,PlayerActivity::class.java).apply{
                    putExtra(PlayerActivity.EXTRA_URL,local.url)
                    putExtra(PlayerActivity.EXTRA_TITLE,local.name)
                    putExtra(PlayerActivity.EXTRA_ID,local.id)
                    putExtra(PlayerActivity.EXTRA_IS_VOD,true)
                })
            }
            .setNegativeButton("Kapat",null)
            .show()
    }

    private fun tokenDialog(){
        val input=EditText(this).apply{
            hint="TMDB API Read Access Token"
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val box=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(15),dp(10),dp(15),dp(3))
        }
        box.addView(text("TMDB hesabından kendine ait API Read Access Token al. Anahtar telefonun uygulama özel alanında saklanır; APK içine gömülmez.",12f,0xff344f55.toInt()))
        box.addView(input)
        AlertDialog.Builder(this)
            .setTitle("TMDB bağlantısı")
            .setView(box)
            .setPositiveButton("Kaydet"){_,_->
                if(TmdbCatalogClient.setToken(this,input.text.toString()))load()
                else showEmpty("Geçerli TMDB API Read Access Token girmen gerekiyor.")
            }
            .setNeutralButton("Anahtarı sil"){_,_->
                TmdbCatalogClient.removeToken(this)
                taskId++
                showEmpty("TMDB anahtarı silindi.")
            }
            .setNegativeButton("Vazgeç",null)
            .show()
    }

    private fun credits(){
        // TMDB's approved unmodified official short logo is loaded remotely.
        val approved="https://www.themoviedb.org/assets/2/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg"
        val html="""<!doctype html><html><body style='background:#0b1418;color:white;font:14px sans-serif'>
            <div style='text-align:center'><img alt='TMDB' style='max-width:115px;height:auto'
            src='"""+approved+"""'></div>
            <p>This product uses the TMDB API but is not endorsed or certified by TMDB.</p>
            <p>Veriler ve afişler: The Movie Database (TMDB). Kaynak yalnızca film ve dizi
            bilgileri sağlar; videoları ve telifli yayınları sağlamaz.</p>
            <p><a href='https://www.themoviedb.org' style='color:#7ce6cd'>themoviedb.org</a></p>
            </body></html>"""
        val web=WebView(this).apply{
            settings.javaScriptEnabled=false
            settings.allowFileAccess=false
            loadDataWithBaseURL("https://www.themoviedb.org/",html,"text/html","UTF-8",null)
        }
        AlertDialog.Builder(this).setTitle("TMDB / Kaynak ve atıf")
            .setView(web).setPositiveButton("Kapat",null).show()
    }

    override fun onDestroy(){
        taskId++
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object { const val EXTRA_SERIES="tmdb_show_series" }
}
