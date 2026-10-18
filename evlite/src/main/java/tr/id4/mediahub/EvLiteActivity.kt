package tr.id4.mediahub

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Single-purpose, media-free EV navigation and charging assistant.
 * Deliberately does not initialize MapLibre, VLC, Media3, IPTV, Mola or
 * any film/TV component. Android platform widgets only.
 */
class EvLiteActivity:Activity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=0xff071923.toInt()
        window.navigationBarColor=0xff071923.toInt()
        val root=ScrollView(this).apply {setBackgroundColor(0xff071923.toInt())}
        val content=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            val pad=d(18)
            setPadding(pad,d(32),pad,d(38))
        }
        root.addView(content)
        setContentView(root)
        content.addView(header("ID.4 YOL ARKADAŞIM",23))
        content.addView(body("Elektrikli araç ve şarj asistanı  •  Lite Beta"))
        content.addView(body("Bu sürümde film, dizi, maç, IPTV, video oynatıcı, " +
            "Mola ve ağır harita motoru bulunmaz."))
        content.addView(section("ŞARJ & NAVİGASYON"))
        content.addView(action("⚡ EPDK resmî şarj istasyonları", "Türkiye genelinde istasyon, soket tipi ve operatör ara") {
            startActivity(Intent(this,EvStationActivity::class.java))
        })
        content.addView(action("🧭 Yol tarifi", "Google Haritalar üzerinden güvenilir sesli navigasyon") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/")))
            }catch(_:Exception){message("Harita açılamadı","Bir harita uygulaması veya tarayıcı yükle.")}
        })
        content.addView(action("₺ Operatörlerin resmî şarj ücretleri", "Trugo ve ZES • Tarihli ve kaynağı belli tarifeler") {
            OfficialChargingTariffs.show(this)
        })
        content.addView(section("ARAÇ & BATARYA"))
        content.addView(action("🔋 ID.4 Enerji Merkezi", "Menzil, batarya yüzdesi, tüketim ve şarj maliyeti") {
            startActivity(Intent(this,EvEnergyActivity::class.java))
        })
        content.addView(action("📑 EPDK resmî sorgulama", "Kamuya açık istasyonların resmî sorgu sayfası") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(
                    "https://lisans.epdk.gov.tr/epvys-web/faces/pages/lisans/elektrikSarjAgiIsletmeci/sarjIstasyonuOzetSorgula.xhtml")))
            }catch(_:Exception){message("Sayfa açılamadı","Tarayıcı bulunamadı.")}
        })
        content.addView(section("VERİ DOĞRULUĞU"))
        content.addView(body("İstasyon konumları EPDK'nın halka açık kayıtlarından alınır. " +
            "Operatörler tarafından doğrulanmayan canlı müsaitlik veya sokete özel " +
            "fiyatlar 'müsait' ya da 'kesin fiyat' olarak gösterilmez."))
        content.addView(body("Aracın kapı, klima veya bataryasına uzaktan erişilmez. " +
            "Konum ve otomobilin OBD bilgileri toplanmaz."))
        content.addView(body("Sürüm 1.0 Lite • Sabit ve basit Android arayüzü. " +
            "Kaynak ve derleme: github.com/AlpTimucin-cpu/ID4-Media-Hub"))
    }
    private fun d(x:Int)=(x*resources.displayMetrics.density).toInt()
    private fun header(value:String,size:Int):TextView=TextView(this).apply {
        text=value;textSize=size.toFloat()
        setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD)
        setPadding(0,d(7),0,d(8))
    }
    private fun body(s:String):TextView=TextView(this).apply {
        text=s;textSize=13f
        setTextColor(0xffb6d1df.toInt())
        setPadding(d(4),d(7),d(4),d(10))
    }
    private fun section(s:String)=header(s,17).apply{
        setTextColor(0xff77e4cf.toInt());setPadding(0,d(21),0,d(5))
    }
    private fun action(title:String,description:String,onClick:()->Unit):LinearLayout {
        val card=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(d(15),d(12),d(15),d(12))
            background=GradientDrawable().apply{
                setColor(0xff193746.toInt());cornerRadius=d(15).toFloat()
            }
            isClickable=true
            isFocusable=true
            setOnClickListener{onClick()}
        }
        card.addView(header(title,15))
        card.addView(body(description))
        val params=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(9)}
        return card.apply{layoutParams=params}
    }
    private fun message(title:String,detail:String){
        AlertDialog.Builder(this).setTitle(title).setMessage(detail)
            .setPositiveButton("Tamam",null).show()
    }
}
