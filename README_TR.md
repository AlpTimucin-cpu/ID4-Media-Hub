# ID.4 YOL ARKADAŞIM • v0.51 BETA
### ID.4'e özgü, tek ekranda TV · radyo · web · araç bağlantı merkezi

**Hedef:** POCO F5 (Android 15) ve Volkswagen ID.4 Pure. Uygulamamızın içinde çalışan IPTV, çok sekmeli web tarayıcı ve radyo. **Araç ekranında video henüz doğrulanmadı.**

## v0.51 — Film ekranında ANR düzeltmesi ve kapsamlı JSON/.api içe aktarma

- Android 15 otomatik test görüntüsünde `ID.4 Yol Arkadaşım isn't responding` ANR penceresi bulundu. Katalog/dosya okumaları ve 7.232 film filtreleme artık **UI thread dışında tek sıralı işçi üzerinde** çalışır.
- Arama 280 ms gecikmeyle başlar, eski arama sonuçları nesil numarasıyla geçersiz kılınır. İlk onResume'de gereksiz ikinci yeniden yükleme kaldırıldı. Kategori değişirken önceki sekmenin kartları geçici olarak gösterilmez.
- Tam `filmler_full.m3u` dosyasının APK içerisine SHA-256 kontrolüyle dahil edilmesi devam eder; 7.232 film adı ve 7.223 benzersiz bağlantı vardır. Bu URL'lerin geçerli olduğu garanti edilmez; sunucudaki tarihlenmiş parametreler yenilenmez.
- Kullanıcı tarafından seçilen HTTPS `.json` / `.api` API bağlantılarıyla birlikte Android dosya seçicisi üzerinden yerel `.json`, NDJSON/`.jsonl`, `.api` ve M3U belge içe aktarma eklendi.
- JSON ayrıştırıcı, düz dizi, iç içe `data/items/series/episodes/sources` gibi farklı alanlarda dolaşır; JSONL satırlarını okur. Tarama derinliği, düğüm ve medya sayısı sınırlıdır; yalnız geçerli HTTPS medya adresi içeren isimli kayıtlar eklenir.
- `.api` tek bir evrensel standart değildir: yalnızca açık ya da kullanma izni bulunan, JSON veya M3U şeklinde veri döndüren servisler desteklenebilir. Özel sağlayıcı kimlik doğrulaması, şifre çözme, lisans veya DRM bypass desteklenmez.
- Android 15 smoke testi artık donma/ANR penceresini yakalar ve hata kayıtlarını başarısız testte de saklar.

## v0.50 — HTTPS .api / JSON / M3U medya içe aktarma

- Gönderilen orijinal 7.232 bağlantılı `filmler_full.m3u` aynen APK'da kalır.
- `filmler_full.m3u` içinde **.api uzantılı URL bulunmuyor** (7.229 `.m3u8`, 2 `.mp4`, 1 uzantısız). `tms` ve `token` parametreleri API uzantısı değildir.
- Film/Dizi/Maç ekranı alt araç çubuğuna **◈ API ekle** seçeneği eklendi. Kullanıcı sahip olduğu veya kullanma izni bulunan `https://.../api` veya `.json` adresini girebilir.
- API sonucunda JSON dizi veya `data`, `items`, `results`, `channels`, `streams`, `videos`, `movies`, `series` zarfı altındaki dizi; `name/title`, `url/stream_url/playUrl`, `group/category`, `logo/poster` alanlarını tanır. API M3U döndürüyorsa var olan M3U ayrıştırıcısı çalışır.
- Yalnız HTTPS video adresleri kullanıcı seçtiği Film/Dizi/Maç bölümüne eklenir. Belirli bir servisin özel API'si, login işlem sırası, DRM, yayın hakkı veya API anahtarı otomatik üretimi desteklenmez.
- Bağlantılar yalnızca cihazda saklanır. JSON parser testleri ve M3U karma biçim testi eklenmiştir.

## v0.49 — Tam filmler_full.m3u APK içinde, film kartından bağlantı çözümü

- Önceki v0.47/v0.48 yalnızca 7.232 film adı/türünü paketliyordu. **v0.49 derlemesi** ayrıca Google Drive üzerinden sağlanan tam 3.145.596 baytlık `filmler_full.m3u` dosyasını build sırasında indirip `assets/filmler_full.m3u` içine ekler. CI SHA-256 değerini ve 7.232 `#EXTINF` kaydını doğrular; başarısızsa APK oluşturmaz. Yayın URL'leri GitHub source'a commit edilmez.
- Film başlıklarından film kartına dokunulduğunda paketlenen M3U'daki başlığı bulur ve ExoPlayer'da açmayı dener. 7.232 M3U kaydının 7.223 tanesi benzersiz bağlantıdır. Oynatıcı bir URI bulunduğu için **yayının çalışacağını garanti etmez**.
- Orijinal dosyadaki 7.222 URL'de 29 Ağustos 2026 döneminden `tms` zaman parametresi görülmektedir. Uygulama eski `tms` kaydını kesinlikle bitmiş sanmak yerine kullanıcıya eski bağlantı olabileceği uyarısını ve oynatmayı deneme seçeneğini verir.
- Kullanıcının tam M3U dosyası Android'in özel paket varlığıdır, açılışta topluca SharedPreferences'a ve GitHub kaynak koduna yazılmaz; tek defa arka planda parse edilip RAM'e alınır.
- Film başlığı eşleşmeyen kayıtlar için yetkili kaynak ekleme ve M3U içe aktarma seçenekleri korunur.
- Android 15 emülatör testi Film ana sayfasından açılarak `TAM M3U PAKETTE` başlığını ve film tıklamasından açılan oynatma uyarısını doğrular. Daha önceki uzun YouTube/WebView test yolunun kaynak tükenmesi bu film testinden çıkarıldı.
- Bu liste REC TV ve diğer üçüncü taraf alan adları içerir. Yayın hakkı, medya erişimi ve oynatma garantisi verilmez; DRM veya Android Auto sürüş video kısıtlamaları değiştirilmedi.

## v0.48 — Filmlere dokunma düzeltmesi + ID.4 araç simgesi

- **Filme dokununca** yalnızca oynatılabilir içerik varmış gibi davranan uyarı yerine net bir **film ayrıntıları** penceresi açılır. Katalogda 7.232 film adı/türü vardır fakat REC TV yayın URL'leri APK içinde yoktur.
- **Yayın bağlantısı ekle**: Kullanım hakkı olan HTTPS HLS/MP4 video adresi, seçilen filmin adı ve kategorisiyle cihazda kaydedilir; **Kaydet ve oynat** bağlantıyı telefon oynatıcısında dener. **M3U içe aktar** seçeneği de film ayrıntılarındadır.
- Kaynağın CDN/DRM/ağ hatası varsa her denemede açılan Android hata Toast'ları kaldırıldı; oynatıcı hata türünü tek satırda açıklıyor.
- **Simge:** jenerik oynat simgesi yerine ID.4'ün ön görünüşü, uzun LED far şeridi ve `ID.4` yazan plaka; Android 8+ adaptive launcher icon.
- **Android 15 emülatör smoke**: doğrudan Film ekranını açıp gömülü film kartına dokunan; ayrıntı diyaloğunu ve dizi ekranını kontrol eden test eklendi.
- Kullanıcıya ait ve telifli olabilecek REC TV akışları APK içine yerleştirilmedi. Android Auto sürüş esnasında video gösterimi desteklenmez.

## v0.47 — Gönderilen M3U'dan 7.232 filmin bilgisi APK içinde hazır

- Google Drive'dan kullanıcı tarafından gönderilen `filmler_full.m3u` ayrıştırıldı. **7.232 başlığın film adı ve orijinal kategori metni** temiz UTF-8 katalog dosyalarına dönüştürülerek `app/src/main/assets/filmler_catalog_01..04.tsv` içinde gömüldü.
- APK ilk kez açıldığında **Filmler → Gömülü Film Kataloğu • 7232 Başlık** gösterilir. İnternet / TMDB anahtarı / Drive seçimi olmadan başlık araması, Türkçe karakter toleranslı arama ve ayrı tür filtreleri çalışır.
- Kaynak M3U'daki REC TV üçüncü taraf film akışı URL'leri, oynatıcı Referer/User-Agent başlıkları, tokenlar ve görsel hostları **APK'ya/GitHub'a eklenmedi**. Bu nedenle gömülü başlıklar tek başına oynatılabilir değildir; oynatma için kullanıcının yetkili medya bağlantısını içe aktarması gerekir.
- Her kart ayrıntıda bu ayrımı açıklar ve kullanıcıyı kendi M3U dosyasını seçmeye yönlendirir. Kullanıcı tarafından yetkili M3U içe aktarılırsa aynı başlığa karşılık gelen yerel oynatma kaydı kullanılır; çift görünüm engellenir.
- 7.232 satırlık katalog dört varlık dosyasında, 36 kartlık sayfalama ile sunulur; 7.232 ImageView aynı anda oluşturulmaz.
- YouTube, SportzX, Netflix, Prime Video ve Android Auto ses menüleri korunur. Sürüş video kısıtları veya DRM değiştirilmedi.

## v0.46 — Film/Dizi ekranları görünür, SportzX Mola tarzı dahili bölüm

- Ana sayfada Filmler, Diziler, Maçlar ve SportzX daha üstte ve **büyük, bağımsız kartlar** halinde.
- Film/Dizi kütüphanesi ekranında resmî sağlayıcılar ve açık Blender filmleri, henüz M3U içe aktarılmamış olsa bile **en üstte görünür**; kişisel M3U kütüphanesi ve sayısı ayrı bölümde.
- Resmî Blender açık film kataloğu ve WING IT! kısa filmi eklendi; canlı Blender API değişir veya çevrimdışı kalırsa akış oynatılamayabilir.
- SportzX uygulama içinde **Mola tarzı ekran**, arama (sayfada metin arama), ana sayfa, geri, yenileme, tam ekran web video desteği ve kurulu native SportzX APK'sını başlatma düğmesiyle açılır.
- SportzX'in `sportzxlive.com` sayfası bir APK tanıtım sitesidir. Native SportzX uygulama içeriğini Media Hub WebView'ine gömmek veya buradan spor yayınını oynatmak **gerçekleştirilmedi ve garanti edilmez**. Site dışı yönlendirmeler engellenir.
- Google Drive'da bağlantısı verilen REC TV M3U'daki telifli/özel oynatma adresleri APK'ya eklenmez. M3U'nun telefon veya Drive dosya seçicisinden içe alınması gerekir.
- Arabanın Android Auto ses medya menüsünde film, YouTube veya SportzX görüntüsü görünmez. Sürüş video kısıtlamaları değiştirilmedi.
- GitHub debug APK imzası her sürümde değişebileceğinden `INSTALL_FAILED_UPDATE_INCOMPATIBLE` oluşabilir; mevcut verileri silmeden önce M3U yedeği alın.

## v0.45 — Araç ekranında podcast ses kütüphanesi ve film listesi onarımı

- Android Auto ses ağacı artık **Tüm radyolar**, **Favori radyolar**, **Son dinlenen radyolar**, **Radyo grupları**, **Podcastlar** olmak üzere 5 bölümdür.
- Telefondaki Podcastlar bölümünde kullanıcı RSS kaynağına girip bölümleri yüklediğinde, ses formatlı HTTPS bölümler sınırlı biçimde cihazda önbelleğe alınır ve Android Auto Podcastlar bölümünde dinlenebilir. Podcast RSS bağlantıları araç menüsünde veya oturum metadatasında paylaşılmaz.
- Podcast RSS hesabı / kaynağı telefondan eklenir; Android Auto uygulama içinden RSS kaynağı tanımlama desteklenmez. İlk açılışta araçtaki Podcastlar boşsa önce telefondan kaynak ekleyip bir kez bölümleri yüklemek gerekir.
- Araç ses oynatma kuyruğunda rastgele video adresleri kabul edilmez. Netflix, Prime, YouTube ve Film ekranları Android Auto'ya yansıtılmaz; sürüş video engeli kaldırılmadı.
- Daha önce genel IPTV bölümünden içe alınan M3U filmleri, Film bölümünden aynı liste seçildiğinde tekrarlanmak yerine **Film** olarak yeniden sınıflandırılır; favori ve kaldığın yerden devam verileri korunur.
- Telefon tarafındaki 36-kartlı katalog sayfalaması ve üstteki M3U/Drive dosya seçicisi v0.44'ten devam eder.

## v0.44 — Boş film ekranı ve büyük M3U kataloğu düzeltmesi

- **M3U / Google Drive'dan Filmler ekle** düğmesi Film/Dizi/Maç sekmelerinin hemen altına taşındı; üstte sürekli görünür ve seçilen bölümün adıyla güncellenir.
- Daha önce M3U içe aktarmadıysan film bölümünde neden boş olduğunu açıklayan mesaj ve **M3U dosyasını şimdi seç** düğmesi gösterilir.
- İçe aktarılan filmler önce, resmî servisler daha aşağıda gösterilir. 7.000+ filmde 500 görselin aynı anda yüklenmesi önlendi: 36 kartlık sayfa ve **Daha fazla göster** seçeneği eklendi.
- M3U kaynak dosyası **otomatik indirilmiyor**. Android/Google Drive'dan kullanıcı dosyayı seçmeli; APK içine üçüncü taraf film bağlantıları eklenmez.
- Gerçek dünya yapısını taklit eden güvenli sentetik `#EXTVLCOPT`/afiş/tür örnekleriyle ayrıştırıcı regresyon testi eklendi.
- DRM/süreli akış anahtarları ve araç ön ekranı video güvenlik kısıtları değiştirilmedi.

## v0.43 — Kullanıcının M3U film listesini içe aktarma

- Film / Dizi / Maçlar ekranının altına **M3U dosyasından içe aktar** eklendi. Android dosya seçicisi üzerinden telefondan ya da Google Drive'dan bir M3U dosyası seçilebilir.
- 2.500 kayıt sınırı 15.000'e yükseltildi; 7.232 satırlı örnek sentetik video kataloğu için ayrıştırıcı birim testi eklendi.
- Her kaynak, seçilen Film / Dizi / Maçlar sekmesine kaydedilir; M3U içindeki grup türleri (`Dram | Türkçe Dublaj`) ayrı kategori filtrelerine dönüştürülür.
- Aynı bağlantı yeniden eklenmez. Kaynak ve afiş URL'leri sadece cihazın özel uygulama verisinde saklanır; herhangi bir liste veya kimlik bilgisi projeye gömülmez.
- **REC TV'ye ait dağıtım sunucuları, bağlantı tokenları veya ücretli/telifli yayın listeleri APK'ya gömülmez.** Yalnızca erişim hakkın olan kişisel M3U dosyasını seçip yerel oynatıcıda deneyebilirsin.
- M3U içerisindeki HTTP Referer / User-Agent gerektiren yayınlar, lisans süresi dolan tokenlar veya DRM korumalı videolar açılmayabilir; bunların çalışacağı garanti edilmez.
- Netflix, Prime Video, YouTube, radyo, IPTV ve Android Auto ses fonksiyonları korunur. Araç ekranında video oynatma kısıtı kaldırılmadı.
- Test ve paketleme: GitHub Actions testDebugUnitTest ve assembleDebug + Android 15 emülatör smoke sonucu ayrıca doğrulanır.

## v0.42 — Film, dizi ve maçlarda kullanışlı katalog

- Film/Dizi/Maçlar: kişinin eklediği M3U grupları için gerçek sayıları gösteren kategori çipleri; arama ve favori filtresi korunur.
- Resmî sağlayıcı kartlarına cihazda saklanan favori yıldızı eklendi; favori ekranında artık kendi kaynaklarınla beraber bu kartlar da görülebilir.
- Kaldığın yerden devam et: sadece kullanıcının açtığı ve ilerlemesi Media3 tarafından kaydedilmiş film/dizi dosyalarında görünür; son açılma sırasına göre ilk sekiz içerik.
- Açık lisanslı Blender videoları için varsa kayıtlı dakikadan devam etiketi; indirme ve gerçek oynatma ayrı ayrı doğrulanmalıdır.
- Radyo, IPTV, Android Auto ses hizmeti, YouTube, Netflix, Prime Video ve araç teşhis/bağlantı panelleri değişmedi.
- REC TV 22.0.2 yeniden statik doğrulandı; hiçbir özel video akışı, token, uygulama kodu veya marka varlığı kopyalanmadı.
- Android Auto sürüş ekranı videosu / Netflix-Prime DRM oynatımı doğrulanmış değildir. Cihaz testi ve GitHub Actions durumuna göre ayrıca raporlanır.
- Teknik not: `docs/REC_TV_AND_LIBRARY_V042.md`.

## v0.41 — REC TV kaynak araştırması / Türkiye'de hangi platformda?

- Kullanıcı APK'sından REC TV film/dizi **özel katalog servisi** ile **TMDB afiş/bilgi API'si** ayrımı yapıldı. Özel yayın URL'leri, kodları, tokenları aktarılmadı.
- TMDB film veya dizi detayında **Türkiye'de nerede izlenir?**: JustWatch/TMDB sağlayıcı listesi (abonelik, ücretsiz/reklamlı, kirala, satın al).
- **İzleme seçeneklerini aç** yalnızca resmî TMDB izleme sayfasına gider; videoları veya üyelikleri kopyalamaz.
- Kullanıcının TMDB API Read Access Token'ı ile çalışır; uygulama içine gizli anahtar gömülmez.
- Dokümantasyon: `docs/REC_TV_OFFICIAL_WATCH_PROVIDER_V041.md`.

## v0.40 — Film, Dizi, Maç ve TV ekranları için resmî kaynak keşfi

- `30856.mp4`: kullanıcı uygulamasında film/dizi/maçlar boş; `30857.mp4`: REC TV'de afiş, yıl, puan, bölüm ve TV kataloglarının dolu olduğu karşılaştırıldı.
- Film/Dizi/Maçlar bölümüne kendi M3U listen olmasa da görünen, hakları belirtilmiş **resmî içerik ve sağlayıcı kartları** eklendi.
- Blender Open Movies açık filmleri için resmî PeerTube video API'sinden HLS/MP4 alma ve oynatıcıya aktarma, resmî afiş/veri kataloğu. Sunucu bağlantısı çalışmazsa erişim hatası ve resmî site seçeneği gösterilir.
- **Diziler:** tabii'nin Gassal, Yeşil Deniz ve dizi arşivi; Blender'ın açık animasyon serisi. **Maçlar:** TRT Spor, tabii ve TRT Spor Yıldız resmî kanalları.
- TV sayfası boşsa TRT Haber bağlantısını ekleme; TRT Spor/tabii uygulamalarını açma; spor açık kaynak M3U listesini kullanıcı onayıyla içe aktarma.
- Ücretli beIN/S Sport yayın akışları, REC TV özel sunucuları ve DRM korumaları kopyalanmadı; resmî uygulamadaki abonelik koşulları devam eder.
- Ayrıntılar: `docs/LEGAL_FILM_SERIES_SPORTS_V040.md`.

## v0.39 — REC TV film/dizi kaynak araştırması ve TMDB keşfi

- REC TV 22.0.2 (161) Flutter derlemesinin içeriği incelendi. `api.themoviedb.org` içerik/afiş metaverisi sağlıyor; özel yayın kataloğu yolları ve `rectv_api_url_enc` Firebase ayarı farklı bir katmanda.
- **Filmler / Diziler → TMDB film / dizi keşfet** üzerinden Türkçe film/dizi bilgileri, afiş, popülerler ve isimle arama (kullanıcının TMDB API Read Access Token'ı gerekir).
- Bulunan filmin, **sadece kullanıcının kendi yerel M3U/HTTPS kütüphanesindeki** olası kaynağı aranır; varsa mevcut Media3 oynatıcısına bağlanır.
- TMDB anahtarı uygulama dosyasına/APK'ya yazılmaz, yalnızca açık işlem süresince bellekte tutulur.
- REC TV'nin şifreli/özel yayın kaynakları, gömülü kimlikleri, kodu veya telifli akışları aktarılmadı. Ayrıntılar: `docs/REC_TV_MOVIE_API_STATIC_ANALYSIS_V039.md`.

## v0.38 — Netflix ve Prime Video için YouTube ile aynı Mola gezinmesi

- Netflix ve Prime Video büyük kartları artık **Mola içinde ayrı katalog ekranlarına** açılır.
- Aynı Mola ses/bağlantı başlığı, platform içinde ara, ana sayfa, son açılan içerikler, geri ve yenile düğmeleri.
- **↗ Uygulamada izle** seçeneği geçerli film/dizi sayfasını, mümkünse yerel resmî Netflix/Prime Video uygulamasına yönlendirir; aksi durumda uygulama ana ekranına geçilir.
- Netflix `/tr/title` gibi yerelleştirilmiş film sayfaları için geçmiş desteği; her platformun geçmişi ayrıdır, takip parametreleri saklanmaz.
- **Önemli:** Netflix ve Prime Video'nun DRM korumalı yayınları Android WebView'de çalışmayabilir. Bu yerel katalog/web görünümüdür; platformların lisanslı video oynatıcılarının kopyası değildir. Arama sayfalarının çalışması da sağlayıcıya bağlıdır.
- Kapsam: `docs/MOLA_NETFLIX_PRIME_V038.md`. Araç hareketindeyken ön ekranda video kısıtı değiştirilmez.

## v0.37 — Mola içinde YouTube, ekranlar arasında ses kontrolleri

- Büyük YouTube kartı artık harici uygulamayı başlatmak yerine **uygulama içindeki YouTube bölümüne** gider.
- Özgün başlık ve arama alanı, YouTube Ana Sayfa / Shorts / Siz sekmeleri, sayfa geri/yenileme ve yalnızca telefonda tutulan son video geçmişi.
- İçerik **YouTube'un mobil web sayfasından WebView ile gösterilir**; orijinal YouTube uygulaması kopyalanmaz. Google giriş/DRM/oynatım destek garantisi yoktur.
- Web sitesi çalışmazsa başlıktaki ↗ düğmesi telefondaki resmî YouTube uygulamasını açar.
- Radyo başlığı ve gerçek Media3 oynatma kontrolleri TV, podcast ve film/dizi/maç ekranlarında da kullanılabilir. AA bağlantısı, doğrulanmadıkça bağlı gösterilmez.
- Android Auto sürüş halinde video kısıtları değişmedi.

## v0.36 — Mola videosundaki kullanım akışı

- Mola ekran videosundaki düzen temel alınarak **kalıcı oynatıcı başlığı, TV/YouTube/Ekran Yansıtma/Radyo/Podcastlar ana sayfa kartları** yerleştirildi.
- TV kategori listesi, grup bazında gerçek kanal sayıları, arama, favoriler ve ızgara/liste görünümü uygulama içinde çalışır.
- Podcastlarda RSS URL ekleme, bölüm listeleme ve OPML içe/dışa aktarma desteklendi.
- Kişisel HTTPS kısayolları, ana sayfada son içerikler ve favoriler korunur.
- YouTube/Netflix/Prime Video kartları tarayıcı yerine kurulu resmî telefon uygulamasını başlatır. Referans Mola'nın iç YouTube görünümü ile bu davranış **birebir aynı değildir**.
- Gerçek araç ön ekranında video ve DRM kısıtları kaldırılmaz. Teknik rapor: `docs/MOLA_VIDEO_UI_V036.md`.

## v0.35 — Mola tarzı film, dizi ve maç merkezi

- Ana sayfada özgün Mola benzeri **FİLMLER, DİZİLER, MAÇLAR** kartları; telefon içinde kategori ekranları.
- Kendi M3U listenin grup adlarına göre otomatik ayrılan film, dizi ve spor yayınları; arama ve favoriler.
- Her kategoriye kendi HTTPS kaynağını ekleme; uygulama içinde Media3 oynatımı. Film/dizide kaldığın dakikadan devam.
- Mevcut YouTube/Netflix/Prime Video kısayolları kurulu resmî Android uygulamaları açar.
- REC TV'den telifli video, yayın akışı, Flutter kodu veya özel sunucu bağlantıları kopyalanmaz. Katalog erişim hakkı bulunan kaynaklarla dolar.
- Android Auto sürüş sırasında video kilitleri değiştirilmez. Teknik not: `docs/MOLA_FILM_DIZI_MAC_V035.md`.

## v0.34 — REC TV APK inceleme ve uygulamalar bağlantısı

- Sağlanan `rectv22.0.2-161mobilbox.apk` incelendi: paket adı `com.rectv.shot`, Flutter AOT, BetterPlayer, YouTube IFrame, reklam ve indirme bileşenleri tespit edildi.
- **Uygulamalar → REC TV'yi telefonda aç:** REC TV yüklüyse normal Android başlatıcısı ile açılır; yüklenmemişse uyarı gösterilir. APK otomatik kurulmaz veya içeriği kopyalanmaz.
- YouTube / Netflix / Prime Video büyük kartları, `v0.33` sürümünde olduğu gibi **resmî kurulu Android uygulamalarını açar**, web tarayıcısı değil.
- APK içeriği Mola uygulaması değildir; orijinal Dart kaynak kodu, gizli sunucular ve telifli varlıklar kopyalanmaz. Ayrıntılar `docs/REC_TV_2202_APK_INCELEME.md`.

## v0.33 — Doğrudan uygulamaları aç (A seçeneği)

- Görünen uygulama adı ve ana başlık **ID.4 Yol Arkadaşım** olarak değiştirildi. Paket kimliği `tr.id4.mediahub` değişmedi; mevcut veriler korunur (aynı imzayla güncelleme gerekir).
- Mola tarzı büyük **YouTube / Netflix / Prime Video** kartları yalnızca **telefonunda kurulu resmî uygulamayı** açar. Kartın tamamına veya ok düğmesine basmak aynı işlemi yapar.
- Uygulama bulunmuyorsa anlaşılır uyarı gösterilir. **Web'e otomatik geçiş yoktur.**
- Bu tercih yalnızca video platform kartları ve ana ekrandaki platform kısayolları içindir. Ayrı **Web Medya** aracı, isteyenler için HTTPS tarayıcı olarak kalır.
- Android Auto sürüş sırasında video oynatma kısıtları aşılmaz; bu kartlar normal Android telefon uygulamalarını açar.

## v0.32 — Mola tarzı görselli akış platformları

- YouTube, Netflix ve Prime Video için **büyük, dikey sıralı medya kartları**: üstte poster tarzı özgün grafik, altta platform adı, açıklama ve yuvarlak açma oku.
- Üç kart, **Media Cockpit → Video Platformları** altında yer alır. Önceki küçük Netflix/Prime butonlarının yerini alır.
- Her kart doğrudan kendi Web Medya girişini açar; ayrı geçmişler (YT◷ / V◷ / P◷) ve resmî uygulama kısayolları korunur.
- Afişler Android Canvas ile **orijinal vektör tasarım** olarak oluşturulmuştur: indirme, takip, API anahtarı veya telifli film posteri gerekmez.
- Referans alınan ekranın genel **görsel hiyerarşisi** uyarlanmıştır; Mola'nın özgün görselleri, logosu veya kodu kopyalanmamıştır.
- Android Auto'nun sürüş halindeki video kısıtlamaları aşılmaz; kartlar telefon arayüzü içindir.

## v0.31 — Netflix ve Prime Video içerik geçmişi

- Web Medya'da **V◷ Netflix** ve **P◷ Prime Video** geçmiş kısayolları; YouTube **YT◷** yerinde kalır.
- İki hizmetin kendi son 30 içerik sayfası, isteğe bağlı temizleme, ziyaret edilen sayfanın tekrar açılması.
- Media Cockpit ana ekranında Netflix ve Prime Video web kısayolları; uygulama içinden resmî telefon uygulamasını açma.
- HTTPS içerik bağlantıları sabit kimlikle normalleştirilir; yeni hizmet geçmişlerinde sorgu parametreleri/çerezler/kullanıcı bilgileri saklanmaz.
- **Gerçek oynatma konumu takibi veya DRM çözümü değildir.** Bazı sayfalar WebView içinde çalışmayabilir; araç ekranında video desteği iddia edilmez.
- Ayrıntılar: `docs/NETFLIX_PRIME_HISTORY_V031.md`.

## v0.30 — YouTube videolarım

- FermataX'in 653 satırlık sınıf/metot envanterinden esinlenen, tamamen **özgün** YouTube video geçmişi ve oturum gezinmesi eklendi.
- Web Medya → **YT◷** üzerinden son 30 YouTube videosu açılabilir, geçmiş istenildiğinde silinebilir.
- YouTube Watch, Shorts, Live, Embed ve youtu.be bağlantıları tek video kimliğine çevrilir; `si`, `t`, `feature` gibi takip parametreleri ayrı video geçmişine alınmaz.
- YouTube'un SPA sayfa geçişleri ve video başlığı değişimleri izlenir. URL'ler yalnızca telefonda saklanır; Android Auto ile paylaşılmaz.
- YouTube URL güvenliği için JUnit testleri ve Android 15 Web Medya düğmesi kontrolü. Teknik not: `docs/YOUTUBE_SESSION_V030.md`.
- Herhangi bir YouTube kodu/APK ikilisi kopyalanmaz; reklam veya DRM atlatma, hareket halinde araç videosu bulunmaz.

## v0.29 — FermataX 2.2.6 telefon bağlantısı

- **TV / Radyo → FermataX 2.2.6 ile telefonda aç:** seçili HTTPS TV veya radyo bağlantısını **yalnızca kullanıcı onayından sonra** telefonda yüklü FermataX Debug uygulamasının manifestte ilan edilmiş dışarıya açık medya etkinliğine gönderir.
- Kurulum/izin otomatik değiştirilmez; FermataX kurulu değilse açıklayıcı uyarı gösterilir. Abonelik anahtarı içeren URL'lerin üçüncü taraf uygulama ile paylaşılabileceği önceden açıklanır.
- **Yeni sürüm 2.2.6 / kod 332** manifestinden doğrulanan entegrasyon; önceki 2.2.2 APK analiziyle karıştırılmaz. Kaynak: `docs/FERMATAX_226_INTEROP_V029.md`.
- Daha önce Android 15 emülatörü disk alanı yüzünden başlamamıştı; CI emülatörü için userdata boyutu sınırlandı ve pano metin kontrolü güncellendi.
- FermataX'in araçta çalışması veya yayın açması **henüz cihazla doğrulanmadı**. Android Auto hareket halinde sürücü videosu etkinleştirilmedi.

## v0.28 — Media Cockpit ana ekranı

- **Yeni gerçek Ana Sayfa:** Ekran genişliğine göre değişen 1/2/3 sütunlu altı işlev kutusu (TV, Radyo, Web Medya, Kitaplık, ID.4 Durum, Uygulamalar).
- **Ses Oturumu** gerçek Media3 radyo durumu ve oynat/duraklat eylemini gösterir; telefon araç bağlantısı varmış gibi davranmaz.
- **Kaldığın Yerden ve Favoriler** son açılan radyo/TV kayıtlarını kayıpsız kullanır; IPTV URL/tokenları görünüm modeline alınmaz.
- **TV programı** mevcut XMLTV önbelleğinden ŞİMDİ ve yayın ilerlemesini gösterir.
- **Kurulum:** TV listesi boşsa kaynak ekleme düğmesiyle yönlendirir. Depolama ve Android Auto ses API'si korunur.
- Özgün tasarım: `CockpitDashboard.kt`; model: `CockpitPlanner.kt`. Detay: `docs/ID4_MEDIA_COCKPIT_V028.md`.
- Android 15 emülatör testi araç ekranındaki video işlevinin kanıtı değildir.

## v0.27 — Baştan tasarlanan ID.4 Drive Hub arayüzü

- Yenilenen orijinal Android 15 telefon ana ekranı: lacivert / turkuaz / amber tema, ID⁴ simgesi, yeni uygulama adı ve özgün vektör ikonu.
- **Media Control** kahraman paneli: gerçek kayıtlı TV, radyo ve favori sayıları; araçtan okunmayan OBD ve batarya verisi uydurulmaz.
- **Şimdi dinleniyor:** Media3 ses servisi çalıyorsa istasyon adı, duraklatma ve radyo sayfasına geçiş. Aksi hâlde doğrulanmış son TV/radyo için tek dokunuşla devam.
- **Canlı TV / Radyo / Web Medya / Kitaplığım** büyük ve dokunmatik medya kartları; altlarında YouTube ve Netflix hızlı bağlantıları.
- **Son açılanlar:** eski/silinmiş içerikleri filtreleyen, tekrarsız yerel liste ve hızlı devam düğmeleri.
- **ID.4 Bağlantı Merkezi:** araç ses tanılama ile harici uygulamalar raporuna tek noktadan erişim.
- Uygulama kimliği `tr.id4.mediahub` korundu; önceki kanal/favori kayıtları için depolama geçişi gerekmez. Kurulum imzasının aynı olması gerekir.
- Tasarım ve sınırlamalar: `docs/ID4_DRIVE_HUB_V027.md`. Kaynak GPL kodu veya APK ikilileri Media Hub'a kopyalanmadı.

## v0.26 — Yüklenen FermataX APK analizinden geliştirmeler

- Gerçek FermataX debug APK paketi `me.app.fermataX.auto.debug` olarak tespit edildi (APK 2.2.2, kod 323, target SDK 36).
- Ortak Entegrasyon Merkezi'nde FermataX debug / kararlı varyantlarını saptama ve kullanıcının telefonundan açma desteği.
- Media3 tabanlı yerel oynatıcıya MPEG-DASH `.mpd` desteği eklendi; HLS `.m3u8` ve varsayılan medya biçimleri korunuyor.
- FermataX'in dashboard deneyiminden esinlenen özgün **Kaldığın yerden devam et** kartı, son TV/radyo seçimini Ana Sayfa'da sunar.
- FermataX APK'sında üç ABI için LibVLC kütüphaneleri bulundu. GPL kaynak/dağıtım uyumluluğu ve APK'nin kaynak koduyla aynı sürümden gelmesi doğrulanmadığından bu ikililer Media Hub'a kopyalanmadı.
- Kaynak bulguları ve SHA-256 değerleri `docs/FERMATAX_YUKLENEN_APK_ANALIZI.md` dosyasında.
- YouTube bağlantısının videosu bu oturumda açılamadığı için içeriği izlenmiş olarak raporlanmaz. Video kilidi/DRM değiştirilmedi.

## v0.25 — IPTV rehberi ve APTV tarzı kanal seçici

- **TV → Kanallar**: XMLTV kaynağı yüklüyse her eşleşen kanalın **Şimdi** program başlığı, sıradaki program ve ilerleme çubuğu gösterilir.
- **📺 Şimdi yayında** filtresi ve kanal adı yerine program adıyla arama (ör. haber programı) eklendi.
- M3U listesinin `#EXTM3U` satırında `x-tvg-url`, `url-tvg` veya `tvg-url` varsa kaynak HTTPS ise önerilir. **İndirme sadece onay verirsen gerçekleşir.**
- Rehberin ön bellekte saklanması, IPTV kaynak bağlantılarının raporlarda gizlenmesi ve kullanıcı kontrolü korunur.
- Gerçek ID.4 ekranında video hâlâ doğrulanmadı; bu video özellikleri öncelikle POCO F5 telefon ekranı içindir.

## v0.24 — Tüm uygulamaları tek ekranda harmanlama

- Ana Sayfa ve Kitaplık üzerinden **🧩 Ortak Entegrasyon Merkezi**: Fermata, Screen2Auto, AABrowser, ScreenOnAuto, Shizuku, AAAD, MirrorMobile, CarStream, DiPlay, SportzX, Media3, APTV-esinli EPG ve Android Auto ses kataloglarının teknik durumu.
- Kurulu APK sürümleri, güvenli başlatma seçenekleri, ScreenOnAuto'nun belgelenmiş yansıtma komutları ve gizli URL içermeyen kopyalanabilir cihaz raporu.
- Harici uygulamalar otomatik yüklenmez; kapalı APK'lar başka uygulamanın içine kopyalanmaz. Tüm işlemler kullanıcı tarafından başlatılır.
- Kaynak/özellik matrisi: docs/ENTEGRE_UYGULAMALAR_V024.md.
- Yeni durum modeli için JUnit doğrulamaları; gerçek Volkswagen ekranında video desteklendiği iddia edilmez.

## v0.23 — APTV esinli IPTV deneyimi + ScreenOnAuto

- APTV Apple ve CarPlay uygulamasıdır; Android APK'sı yoktur. APTV kodu veya görselleri kopyalanmaz.
- Kitaplık → TV Program Rehberi: kullanıcıya ait HTTPS XMLTV / XMLTV.GZ kaynaklarından EPG alır ve sınırlandırılmış yerel önbelleğe kaydeder.
- M3U içindeki tvg-id ve tvg-logo bilgisi saklanır; içe aktarma, M3U dışa aktarma ve Şimdi / Sonra program eşlemesi desteklenir.
- Güvenlik: DTD ve harici XML varlıkları engellenir; indirme HTTPS, açılmış dosya ve kayıt sayısı sınırlarına tabidir.
- Kitaplık → ScreenOnAuto komutları, ayrı kurulu uygulamanın belgelenmiş start/stop activity arayüzünü kullanır.
- APTV'nin iCloud veya CarPlay bileşenleri Android üzerinde çalıştırılmaz; CarPlay videosu Android Auto videosu değildir.
- Hareket halinde sürücünün ön ekranında video gösterimi veya DRM atlatma yoktur. Gerçek ID.4 üzerinde park testi gerekir.

## v0.22 — Shizuku / AAAD / Fermata yardımcı entegrasyonu

- **Kitaplık → Shizuku / AAAD / Fermata durumunu kontrol et**: yüklenmiş paket sürümleri, Shizuku API'nin canlılığı ve Media Hub'ın mevcut izin durumu görüntülenir.
- Shizuku 13.1.5 resmî API kullanılır: yalnızca `pingBinder()` ve `checkSelfPermission()` çağrıları; yeni izin, ayrıcalıklı komut veya kurulum değişikliği yok.
- AAAD (`sksa.aa.customapps`) ve Fermata Mobile/Auto varyantları tanınır. **Fermata'yı aç** yalnızca telefon üzerindeki Fermata etkinliğini başlatır.
- Sürüşte ekran videosu etkinleştirme, hız/vites/park sinyali değiştirme veya sahte oyun/navigasyon bildirimi uygulanmaz. Araçta video doğrulanmış değildir.
- Daha önceki Shizuku kurulumu tekrarlanmaz; emülatör testinde yardımcı uygulamalar kurulu değilken kontrollü durum raporu aranır.

## v0.21 — AABrowser ile araç ekranına web bağlantısı gönderme denemesi

- **Web Medya → 🚘 AABrowser** düğmesi, seçili sekmenin **HTTPS adresini** `com.kododake.aabrowser` paketine yöneltilmiş `ACTION_VIEW` isteğiyle gönderir.
- Bu giriş noktası AABrowser'ın açık kaynak manifesti ve Android `onNewIntent` işleyicisinde doğrulanmıştır. Başka tarayıcıya sessiz yönlendirme yapılmaz.
- Android 15 emülatöründe AABrowser kurulu olmadığındaki hata davranışı ve uygulamanın çökmediği test edilir.
- **Gerçek ID.4 ekranında aktarım, URL'nin orada görünmesi ve video oynatma henüz test edilmedi.** AABrowser'ı sahte oyun/navigasyon olarak kendi APK'mıza taşımıyoruz. Deneme yalnızca araç park halindeyken yapılmalıdır.
- Şimdilik web sekmeleri içindir: Media3 IPTV akışının AABrowser WebView'de çalıştığı ya da Netflix DRM'nin çözüldüğü iddia edilmez.

## v0.20 — Android 15 legacy + Media3 ses tarama uyumluluğu

- **Kitaplık → Ses kitaplığı API bağlantısını kontrol et** artık telefon içinde iki ayrı istemci çalıştırır: modern Media3 `MediaBrowser` ve Android'in platform `android.media.browse.MediaBrowser` (legacy) API'si.
- Legacy istemci servis kökünü açar ve **Tüm radyolar / Favoriler / Radyo grupları** kategorilerini gerçekten listeleyip doğrular. 10 saniyelik zaman aşımı ve net hata tanılaması bulunur.
- GitHub Android 15 emülatör testi her iki tarayıcının da başarıyla bağlandığını kontrol eder. Bunlar **telefondan başlatılmış testlerdir**, Volkswagen ekranındaki uygulama veya video kanıtı değildir.
- AABrowser/YouTube araç ekranı referans alınmaya devam edilir; tarayıcıyı oyun ya da navigasyon uygulaması gibi yanlış kategorilendirme yapılmaz.

## v0.19 — Android Auto ses bağlantısının doğrudan teşhisi

- **Kitaplık → Ses kitaplığı API bağlantısını kontrol et**: Telefonda Media3 MediaBrowser ile mevcut ses servisine bağlanır, kök/kanal kategorilerini sorgular ve sonucu gösterir. **Araç ekranı veya video testi değildir.**
- **Kitaplık → ID.4 / Android Auto bağlantı raporu**: Media3 ses oturumu bağlantısı ile gerçek kütüphane taramasını ayrı ayrı kaydeder; Android Auto istemcisi ile telefon içindeki istemciyi birbirine karıştırmaz.
- Tanılamada hesap, M3U URL'si, abonelik tokenı, telefon konumu ve istemci paket adı kaydedilmez. Raporda yalnızca istemci türü ve zaman bilgisi bulunur; **Paylaş** ve **Kopyala** seçenekleri vardır.
- Android Auto kaydı bulunmaması, araçla uyumsuzluk kanıtı değildir. Ses API testinin geçmesi de ID.4 ön ekranında web/video çalıştığı anlamına gelmez.

## v0.18 — Video arka plan kararlılığı ve Android 15 UI testi

- IPTV videosu manuel olarak durdurulunca, TV sekmesinden ayrılınca veya uygulama arka plana geçince **bekleyen otomatik yeniden bağlantılar iptal edilir**.
- Uygulama tekrar görünür olduğunda video kendiliğinden başlamaz; kullanıcı oynat komutuyla devam eder.
- Android 15 emülatör testine **TV → Yayın performansı ve bağlantı raporu** düğmesini gerçek kullanıcı etkileşimiyle açıp sayaçları kontrol eden senaryo eklendi.
- **Araç ekranında video hâlâ doğrulanmış değildir**; ses/radyo hizmeti Android Auto'da ayrı tutulur.

## v0.17 — DiPlay'dan öğrendiklerimizi IPTV oynatıcısına uyguladık

DiPlay 0.2.16'nın video performans ve kesinti tanılama yaklaşımı incelendi; **CarPlay alıcı kaynak kodu, özel sertifikalar ve BYD bileşenleri alınmadı**. İlgili ölçümler Media3'ün kendi olaylarıyla bağımsız olarak yeniden geliştirildi.

- TV → **Yayın performansı ve bağlantı raporu**: ilk görüntünün gelme süresi, tamponlama süresi/sayısı, düşen kareler, işlenen kare sayısından hesaplanan **tahmini** işleme hızı, görüntü çözünürlüğü, video çözücü ve başlatma gecikmesi, hata türü, yeniden bağlanma denemeleri.
- Rapor yereldir ve **video URL'si, M3U erişim tokenı, çerez, DRM verisi veya hesap bilgisi** içermez; kullanıcı isterse kopyalayabilir.
- Geçici bağlantı sorununda en fazla **2 otomatik yeniden deneme** (1,5 ve 4 saniye). 401, 403, 404, 429, DRM veya codec hatalarında otomatik deneme yapılmaz.
- Kanal değiştirildiğinde, TV sekmesinden çıkıldığında ya da uygulama kapandığında bekleyen yeniden denemeler iptal edilir.
- Hız/kare ölçümleri Media3'ün olaylarıyla sınırlıdır. **Bu sürümde gerçek araç ekranı video aktarımı hâlâ doğrulanmadı.**

## Özellikler

| Bölüm | İşlev |
|---|---|
| **TV** | Media3 ExoPlayer ile HTTPS HLS/IPTV, kendi M3U/M3U8 dosyasını veya adresini içe aktarma, kanal rehberi, favoriler, tam ekran, görüntü kalitesi, ses dili/altyazı seçimi |
| **Web Medya** | Uygulama içinde YouTube, Netflix web sayfası ve HTTPS siteleri; en çok 6 sekme, oturum geri yükleme, favoriler, geçmiş, mobil/masaüstü modu, geri/ileri/yenile, HTML5 video tam ekran ve yeni pencere desteği |
| **Radyo** | Çevrim içi katalog, favoriler, ses oynatıcısı ve Android Auto MediaLibraryService |
| **Dünya IPTV dizini** | Ülkeleri/kategorileri arayıp iptv-org topluluk M3U listesini kullanıcı onayıyla içe aktarma |
| **Tanılama** | Android Auto uygulama sürümü, cihaz ve medya uyumluluğu raporu |

## Kullanım

**Ana Sayfa → Web Medya** veya **TV → YouTube Web**: Kendi web tarayıcımız açılır. **＋** ile yeni sekme açılır, sekmeye uzun basınca kapatılır. **☆** web favorilerine ekler; **☷** favorileri, **⋮** geçmiş, masaüstü modu ve diğer seçenekleri açar. **Netflix Web**, Netflix'in kendi web sayfasını açar; videonun DRM nedeniyle çalışmayabileceği önemle belirtilir.

**TV → Kanallar**: Mevcut M3U kaynaklarını oynat. **Kitaplık → Dünya kanalları / Kategoriler**: Kullanıcının seçtiği ülke/kategori M3U dizinini getir. Açık kaynak dizinleri yayın haklarını veya erişilebilirliği garanti etmez. Yalnızca erişim izni olan kaynakları kullan.

**Kitaplık → ID.4 / Android Auto bağlantı raporu**: Android Auto sürüm/cihaz bilgisini kopyalayabilirsin. İzin, root, Shizuku veya kurulum otomatik değiştirilmez.

## Araç ekranı gerçek durumu

- Bu kullanıcının ID.4 ekranında **AABrowser üzerinden YouTube açılmıştır**; Netflix sitesi açılmış fakat Netflix videosu oynatılmamıştır. Shizuku daha önce kurulup ayarlanmıştır. Bu adımları baştan tekrarlamak gerekmiyor.
- Bizim uygulamanın araçta sunduğu **onaylanmış** Android Auto işlevi ses/radyo medya kataloğudur. WebView/TV görüntüsünün ID.4'ün araç ekranına yansıtıldığını kanıtlayan test yok.
- Android Auto'nun video uygulamalarına ilişkin kısıtlamalarını gizlemek, uygulamayı sahte navigasyon uygulaması olarak sunmak, sürüş sırasında görüntü güvenlik engellerini kaldırmak **projenin kapsamına alınmamıştır**.
- Netflix DRM lisansı, üçüncü taraf SportzX APK'sının ekranını başka APK içine gömme ve izinsiz spor yayınları **tamamlanmış entegrasyonlar değildir**. DRM izni gerektiğinde web tarayıcı kullanıcıya sorar; izin verilmesi Netflix oynatımını garanti etmez.

## Kaynak incelemesi / lisans

- [Fermata](https://github.com/AndreyPavlenko/Fermata), GPL-3.0: M3U/IPTV ve video oynatıcısı, ayrı Android Auto araç bileşenleri. Bazı araç bileşenleri eski/proprietary SDK veya Xposed tabanlıdır.
- [AABrowser](https://github.com/kododake/AABrowser), GPL-3.0: WebView, sekmeler, tam ekran ve araç ekranıyla ilgili kayıtlar. AABrowser'da video çalışması bu kodun aynısını kopyalamakla veya bir APK'nın derlenmesiyle garanti edilemez.
- **ID.4 Media Hub**: Bu kaynaklardan kod dosyası kopyalamadan, özellikleri bağımsız olarak Kotlin ile uyguluyoruz. GPL kaynakları bir gün doğrudan alınırsa telif/lisans bildirimi, GPL-3.0 ve karşılık gelen kaynak kodun sunulması gereklidir.

## Derleme ve kalite kontrolü

GitHub Actions \`Build ID4 Media Hub APK\` iş akışı \`:app:testDebugUnitTest\` ve \`:app:assembleDebug\` çalıştırır; APK ve SHA-256 raporunu **Artifacts** kısmında paylaşır. \`Android emulator launch smoke test\` Android 15 emülatöründe uygulama sekmelerini ve ayrıca **Web Medya** ekranının UI üzerinden açılışını test eder.

**Not:** Başarılı GitHub Actions testi, gerçek POCO F5 kurulumu veya ID.4 büyük ekranda video testi değildir. Sonuçlar ayrı ayrı raporlanmalıdır.

Kod: [feature/fermata-style-car-video](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/tree/feature/fermata-style-car-video).
