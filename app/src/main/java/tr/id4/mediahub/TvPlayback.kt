package tr.id4.mediahub

import android.app.Activity
import android.app.Dialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Phone-only IPTV. This UI never projects video into Android Auto. */
class TvPlayback(
    private val activity: Activity,
    private val catalog: () -> List<VideoChannel>,
    private val showLibrary: () -> Unit,
    private val onOpened: (VideoChannel) -> Unit
) {
    private var player: ExoPlayer? = null
    private var current: VideoChannel? = null
    private var maxQualityHeight = Int.MAX_VALUE
    private var fullScreen: Dialog? = null
    private var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private lateinit var videoHost: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var overlay: View
    private lateinit var title: TextView
    private lateinit var playbackState: TextView
    private lateinit var catalogState: TextView
    private lateinit var quickAccess: LinearLayout
    private var initialized = false

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int = 14) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun label(content: String, size: Float = 14f, muted: Boolean = false): TextView =
        TextView(activity).apply {
            text = content
            textSize = size
            setTextColor(if (muted) 0xffa8bdd2.toInt() else Color.WHITE)
            setPadding(dp(5), dp(8), dp(5), dp(8))
        }
    private fun button(content: String, action: () -> Unit): Button = Button(activity).apply {
        text = content
        textSize = 13f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = bg(0xff244b66.toInt(), 10)
        setOnClickListener { action() }
    }

    fun view(): View {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        root.addView(label("CANLI TELEVİZYON", 22f))
        root.addView(label("Tek oynatıcı · Hızlı kanal değiştirme", 12f, true))
        videoHost = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
        val screenWidth = activity.resources.displayMetrics.widthPixels - dp(32)
        root.addView(videoHost, LinearLayout.LayoutParams(-1, screenWidth * 9 / 16))
        playerView = PlayerView(activity).apply {
            useController = true
            controllerShowTimeoutMs = 3500
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
        }
        videoHost.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        overlay = label("▶   Kanal seçmek için dokun", 17f).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(0xdd0b1c31.toInt())
            setOnClickListener { showChannels() }
        }
        videoHost.addView(overlay, FrameLayout.LayoutParams(-1,-1))
        title = label("Kanal seçilmedi", 18f)
        root.addView(title)
        playbackState = label("Kanallar düğmesiyle yayını başlatabilirsin.", 12f, true)
        root.addView(playbackState)

        val navigation = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        navigation.addView(button("◀") { next(-1) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        navigation.addView(button("▦  Kanallar") { showChannels() },
            LinearLayout.LayoutParams(0,dp(48),2.7f))
        navigation.addView(button("▶") { next(1) }, LinearLayout.LayoutParams(0,dp(48),1f))
        root.addView(navigation)
        val options = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val quality = button("⚙  Görüntü kalitesi") {}
        quality.setOnClickListener { showQuality(quality) }
        options.addView(quality, LinearLayout.LayoutParams(0,dp(49),1f))
        options.addView(button("⛶  Tam ekran") { fullScreen() },
            LinearLayout.LayoutParams(0,dp(49),1f))
        root.addView(options)

        catalogState = label("", 12f, true)
        root.addView(catalogState)
        root.addView(label("HIZLI KANAL SEÇ", 16f))
        val scroller=HorizontalScrollView(activity).apply{isHorizontalScrollBarEnabled=false}
        quickAccess=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
        scroller.addView(quickAccess)
        root.addView(scroller)
        root.addView(label("Otomatik kalite ve seçilen çözünürlük, yayının mevcut HLS varyantlarıyla sınırlıdır.",11f,true))
        initialized=true
        refresh()
        return root
    }

    private fun player(): ExoPlayer {
        player?.let { return it }
        val instance = ExoPlayer.Builder(activity).build()
        instance.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (!initialized) return
                playbackState.text = when(state) {
                    Player.STATE_BUFFERING -> "●  Yayın yükleniyor…"
                    Player.STATE_READY -> "●  Yayın oynatılıyor"
                    Player.STATE_ENDED -> "●  Yayın bitti"
                    else -> "●  Bağlanılıyor"
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (initialized) playbackState.text = "Yayın hatası: " + error.errorCodeName +
                    " · Kanalı değiştir veya yeniden dene."
                Toast.makeText(activity, "Yayın açılamadı: " + error.errorCodeName,
                    Toast.LENGTH_LONG).show()
            }
        })
        player=instance
        playerView.player=instance
        applyQuality(maxQualityHeight)
        return instance
    }

    fun play(channel: VideoChannel) {
        current=channel
        if (!initialized) return
        overlay.visibility=View.GONE
        title.text=channel.name
        playbackState.text="●  Yayın başlatılıyor…"
        try {
            val address=channel.url.substringBefore('?').substringBefore('#')
            val item=MediaItem.Builder().setUri(Uri.parse(channel.url)).apply {
                if (address.endsWith(".m3u8", true)) setMimeType(MimeTypes.APPLICATION_M3U8)
            }.build()
            player().apply {
                setMediaItem(item)
                prepare()
                playWhenReady=true
            }
            onOpened(channel)
        } catch (_: Exception) {
            playbackState.text="Bu kanal başlatılamadı"
        }
        refresh()
    }

    fun refresh() {
        if (!initialized) return
        val channels=catalog()
        catalogState.text=channels.size.toString() + " kanal · " +
            channels.count { it.favorite }.toString() + " favori"
        quickAccess.removeAllViews()
        if (channels.isEmpty()) {
            quickAccess.addView(button("＋ Kitaplıktan kanal ekle") { showLibrary() },
                LinearLayout.LayoutParams(dp(230), dp(80)))
            return
        }
        val featured=channels.filter{it.favorite}.take(10).ifEmpty{channels.take(10)}
        featured.forEach { channel ->
            val tile=LinearLayout(activity).apply {
                orientation=LinearLayout.VERTICAL
                gravity=Gravity.CENTER
                background=bg(0xff1b3048.toInt())
                setPadding(dp(6),dp(6),dp(6),dp(6))
                setOnClickListener { play(channel) }
            }
            val image=ImageView(activity).apply {
                setImageResource(android.R.drawable.ic_media_play)
                scaleType=ImageView.ScaleType.FIT_CENTER
            }
            tile.addView(image, LinearLayout.LayoutParams(dp(48),dp(47)))
            if(channel.logo.isNotBlank())ChannelLogoLoader.into(channel.logo,image,activity)
            tile.addView(label(channel.name,11f).apply {
                maxLines=1
                ellipsize=android.text.TextUtils.TruncateAt.END
                gravity=Gravity.CENTER
            },LinearLayout.LayoutParams(-1,dp(38)))
            val lp=LinearLayout.LayoutParams(dp(126),dp(94))
            lp.setMargins(0,0,dp(7),0)
            quickAccess.addView(tile,lp)
        }
    }

    private fun next(direction: Int) {
        val channels=catalog()
        if (channels.isEmpty()) {showLibrary();return}
        val index=channels.indexOfFirst{it.id == current?.id}
        play(channels[if(index < 0) 0 else (index + direction + channels.size) % channels.size])
    }

    private fun applyQuality(height: Int) {
        maxQualityHeight=height
        val playing=player ?: return
        playing.trackSelectionParameters=playing.trackSelectionParameters
            .buildUpon().setMaxVideoSize(Int.MAX_VALUE,height).build()
    }

    private fun showQuality(anchor: View) {
        val options=listOf("Otomatik" to Int.MAX_VALUE,"360p" to 360,
            "480p" to 480,"720p" to 720,"1080p" to 1080)
        val popup=PopupMenu(activity,anchor)
        options.forEach { (title,height) ->
            popup.menu.add((if(height==maxQualityHeight)"✓  " else "")+title)
                .setOnMenuItemClickListener {
                    applyQuality(height)
                    playbackState.text = "Kalite tercihi: " + title +
                        " · Kaynağın desteklediği çözünürlük uygulanır"
                    true
                }
        }
        popup.show()
    }

    private fun fullScreen() {
        if (!initialized || fullScreen != null) return
        val previousParent=playerView.parent as? ViewGroup ?: return
        previousParent.removeView(playerView)
        previousOrientation=activity.requestedOrientation
        val dialog=Dialog(activity,android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val frame=FrameLayout(activity).apply{setBackgroundColor(Color.BLACK)}
        frame.addView(playerView,FrameLayout.LayoutParams(-1,-1))
        val top=LinearLayout(activity).apply{
            orientation=LinearLayout.HORIZONTAL
            setBackgroundColor(0xb90b1728.toInt())
        }
        top.addView(button("×  Kapat"){dialog.dismiss()},LinearLayout.LayoutParams(0,dp(47),1f))
        top.addView(button("▦  Kanallar"){showChannels()},LinearLayout.LayoutParams(0,dp(47),1f))
        val quality=button("⚙  Kalite"){}
        quality.setOnClickListener{showQuality(quality)}
        top.addView(quality,LinearLayout.LayoutParams(0,dp(47),1f))
        frame.addView(top,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
        dialog.setContentView(frame)
        dialog.setOnDismissListener {
            (playerView.parent as? ViewGroup)?.removeView(playerView)
            previousParent.addView(playerView,FrameLayout.LayoutParams(-1,-1))
            activity.requestedOrientation=previousOrientation
            fullScreen=null
        }
        fullScreen=dialog
        dialog.show()
        dialog.window?.decorView?.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun showChannels() {
        val all=catalog()
        if(all.isEmpty()){showLibrary();return}
        val dialog=Dialog(activity,android.R.style.Theme_Material_NoActionBar)
        val root=LinearLayout(activity).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(0xff0b1728.toInt())
            setPadding(dp(13),dp(10),dp(13),dp(10))
        }
        val header=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
        header.addView(label("KANALLAR",22f),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("×"){dialog.dismiss()},LinearLayout.LayoutParams(dp(53),dp(47)))
        root.addView(header)
        val search=EditText(activity).apply{
            hint="Kanal veya kategori ara"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xffa4b7c9.toInt())
            background=bg(0xff243b53.toInt())
            setPadding(dp(14),dp(9),dp(14),dp(9))
        }
        root.addView(search,LinearLayout.LayoutParams(-1,dp(50)))
        val categories=listOf("Tümü","★ Favoriler")+
            all.map{it.group}.filter{it.isNotBlank()}.distinct().sorted().take(35)
        var selected="Tümü"
        val strip=HorizontalScrollView(activity).apply{isHorizontalScrollBarEnabled=false}
        val categoryRow=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
        strip.addView(categoryRow)
        root.addView(strip,LinearLayout.LayoutParams(-1,dp(60)))
        val count=label("",12f,true)
        root.addView(count)
        val grid=GridView(activity).apply{
            numColumns=if(activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)5 else 3
            horizontalSpacing=dp(7)
            verticalSpacing=dp(7)
            stretchMode=GridView.STRETCH_COLUMN_WIDTH
            clipToPadding=false
        }
        root.addView(grid,LinearLayout.LayoutParams(-1,0,1f))
        var shown:List<VideoChannel> = all
        val adapter=object:BaseAdapter(){
            override fun getCount()=shown.size
            override fun getItem(position:Int):Any=shown[position]
            override fun getItemId(position:Int)=position.toLong()
            override fun getView(position:Int,convertView:View?,parent:ViewGroup):View{
                val channel=shown[position]
                val tile=LinearLayout(activity).apply{
                    orientation=LinearLayout.VERTICAL
                    gravity=Gravity.CENTER
                    setPadding(dp(3),dp(5),dp(3),dp(4))
                    background=bg(0xff1a314a.toInt(),12)
                    setOnClickListener{dialog.dismiss();play(channel)}
                }
                val logo=ImageView(activity).apply {
                    setImageResource(android.R.drawable.ic_media_play)
                    scaleType=ImageView.ScaleType.FIT_CENTER
                }
                tile.addView(logo,LinearLayout.LayoutParams(dp(60),dp(58)))
                if(channel.logo.isNotBlank()) ChannelLogoLoader.into(channel.logo,logo,activity)
                tile.addView(label(channel.name,12f).apply{
                    maxLines=2
                    gravity=Gravity.CENTER
                    ellipsize=android.text.TextUtils.TruncateAt.END
                },LinearLayout.LayoutParams(-1,dp(49)))
                return tile
            }
        }
        grid.adapter=adapter
        fun update(){
            val q=search.text.toString().trim()
            shown=all.filter { channel ->
                (selected=="Tümü" || selected=="★ Favoriler" && channel.favorite ||
                    channel.group==selected) &&
                (q.isBlank() || channel.name.contains(q,true) || channel.group.contains(q,true))
            }
            count.text=shown.size.toString()+" kanal · Oynatmak için seç"
            adapter.notifyDataSetChanged()
        }
        categories.forEach{ category ->
            val chip=button(category){selected=category;update()}
            val lp=LinearLayout.LayoutParams(-2,dp(43))
            lp.setMargins(0,dp(7),dp(7),dp(4))
            categoryRow.addView(chip,lp)
        }
        search.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){update()}
            override fun afterTextChanged(s:Editable?){}
        })
        update()
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setLayout(-1,(activity.resources.displayMetrics.heightPixels*0.86).toInt())
    }

    fun pause(){player?.pause()}
    fun release(){
        fullScreen?.dismiss()
        fullScreen=null
        playerView.player=null
        player?.release()
        player=null
    }
}
