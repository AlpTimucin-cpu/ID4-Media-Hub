package tr.id4.mediahub

/**
 * Publicly documented official entry points. Cards do NOT assert that a paid match,
 * film or episode is playable without the provider's subscription or country rights.
 * Verified provider pages are not repackaged streaming URLs.
 */
enum class OfficialMediaMode { OPEN_MOVIE, PROVIDER_APP, PROVIDER_WEB, TV_CHANNEL }

data class OfficialMediaCard(
    val id: String,
    val kind: VideoLibraryKind,
    val title: String,
    val detail: String,
    val provider: String,
    val mode: OfficialMediaMode,
    val target: String,
    val rightNote: String
)

object OfficialMediaDirectory {
    const val TABII_PACKAGE="com.trt.tabii.android"
    const val TRT_SPOR_PACKAGE="com.trt.trtspor"
    const val BLENDER_BASE="https://video.blender.org"
    val cards=listOf(
        OfficialMediaCard("bbb", VideoLibraryKind.MOVIE, "Big Buck Bunny",
            "9 dk • Blender açık animasyon filmi", "Blender Open Movies",
            OfficialMediaMode.OPEN_MOVIE, "bf1f3fb5-b119-4f9f-9930-8e20e892b898",
            "Creative Commons BY — Blender Foundation • film künyesi korunur"),
        OfficialMediaCard("tabii_movie", VideoLibraryKind.MOVIE, "tabii • Filmler",
            "TRT'nin film arşivini ve lisanslı yapımları keşfet", "tabii",
            OfficialMediaMode.PROVIDER_APP, TABII_PACKAGE,
            "İçerik ve abonelik koşulları tabii tarafından belirlenir"),
        OfficialMediaCard("cam2", VideoLibraryKind.SERIES, "Caminandes • Gran Dillama",
            "2. bölüm • Blender'ın açık animasyon serisi", "Blender Open Movies",
            OfficialMediaMode.OPEN_MOVIE, "x3QKwgFXGZQ7yvCTD2wHJA",
            "Blender açık film serisi — kaynak künyesi korunur"),
        OfficialMediaCard("tabii_gassal", VideoLibraryKind.SERIES, "Gassal • tabii",
            "Resmî tabii uygulamasında diziyi bul", "tabii",
            OfficialMediaMode.PROVIDER_APP, TABII_PACKAGE,
            "Bu kart doğrudan bölümü değil tabii uygulamasını açar"),
        OfficialMediaCard("tabii_ys", VideoLibraryKind.SERIES, "Yeşil Deniz Milenyum • tabii",
            "Dizi arşivini resmî uygulamada aç", "tabii",
            OfficialMediaMode.PROVIDER_APP, TABII_PACKAGE,
            "Bu kart doğrudan bölümü değil tabii uygulamasını açar"),
        OfficialMediaCard("tabii_discover", VideoLibraryKind.SERIES, "tabii • Diğer diziler",
            "TRT dizi ve sezon kataloğu", "tabii",
            OfficialMediaMode.PROVIDER_APP, TABII_PACKAGE,
            "Dizileri izlemek için uygulama ve varsa abonelik gerekir"),
        OfficialMediaCard("trt_spor", VideoLibraryKind.SPORT, "TRT Spor",
            "Resmî uygulamada spor haberleri, özetler ve yayınlar", "TRT",
            OfficialMediaMode.PROVIDER_APP, TRT_SPOR_PACKAGE,
            "Maç erişimi güncel yayın hakkına ve bölgeye bağlıdır"),
        OfficialMediaCard("tabii_match", VideoLibraryKind.SPORT, "tabii • Canlı maçlar",
            "TRT'nin lisanslı spor yayınları", "tabii",
            OfficialMediaMode.PROVIDER_APP, TABII_PACKAGE,
            "Maçlar ülke, paket ve yayın hakkına göre değişir"),
        OfficialMediaCard("trt_yildiz", VideoLibraryKind.SPORT, "TRT Spor Yıldız • Yayın akışı",
            "Resmî TRT yayın akışında maçları gör", "TRT",
            OfficialMediaMode.PROVIDER_WEB, "https://www.trt.net.tr/yayin-akisi",
            "Bu kart maç videosu değil resmî yayın akışını açar")
    )

    fun list(kind: VideoLibraryKind, query: String=""): List<OfficialMediaCard> =
        cards.filter { it.kind == kind &&
            (query.isBlank() || it.title.contains(query,true) ||
                it.detail.contains(query,true) || it.provider.contains(query,true)) }
}
