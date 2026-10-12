# ID.4 MEDIA HUB • v0.22 BETA
### Fermata IPTV + AABrowser benzeri web medya yaklaşımı

**Hedef:** POCO F5 (Android 15) ve Volkswagen ID.4 Pure. Uygulamamızın içinde çalışan IPTV, çok sekmeli web tarayıcı ve radyo. **Araç ekranında video henüz doğrulanmadı.**


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
