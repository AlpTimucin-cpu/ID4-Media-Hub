# ID.4 Media Hub v0.16 — Fermata + AABrowser yaklaşımı (deneysel)

**Hedef cihaz:** POCO F5 Android 15 + Volkswagen ID.4 Pure mevcut Android Auto ekranı.

**Durum:** Kod, GitHub'daki \`feature/fermata-style-car-video\` dalında geliştirilmektedir. Android uygulaması/birim testi otomatik derlenir. **ID.4 büyük ekranda WebView videosu oynatma henüz kanıtlanmadı.** Bunun çalıştığı izlenimi verilmemelidir.

## Gerçekleştirilen uygulama içi özellikler

- **Fermata'dan esinlenen IPTV**: kullanıcı M3U/M3U8 bağlantısı veya dosyası içe aktarabilir, HLS akışlarını Media3 ile izleyebilir, kanal listesi ve favorileri yönetebilir, oynatıcı kalitesini değiştirebilir, tam ekrana geçebilir.
- **AABrowser'dan esinlenen web medya**: uygulamanın **kendi içinde** YouTube ve Netflix web siteleri, 6 sekmeye kadar web görüntüleme, sekme geçişi ve uzun dokunuşla kapatma, güvenli HTTPS adres çubuğu, web favorileri/geçmiş, mobil/masaüstü modu, HTML5 video tam ekran.
- **Dünya dizini**: ülke ve kategoriye göre arama, seçilen iptv-org topluluk listesini isteğe bağlı içe aktarma. Listenin çalışacağı veya kullanım haklarının doğruluğu garanti edilmez.
- **Radyo/Android Auto ses**: mevcut MediaLibraryService tabanlı radyo servisi korunur.
- **Tanılama**: Kitaplık bölümünde Android Auto ve telefon sürüm bilgilerini gösteren rapor.

## Kullanım

1. Ana Sayfa → **Web Medya** veya TV → **YouTube Web** ile sekmeli tarayıcıyı aç.
2. Sekmelerin sağındaki **＋** ile yeni sekme oluştur; bir sekmeye **uzun basarak** kapat.
3. **☆** ile web favorisi kaydet; **☷** ile favori listesini aç; **⋮** menüsünden geçmişe ve masaüstü moduna ulaş.
4. Kitaplık → **Dünya kanalları / Kategoriye göre kanal ara** ile M3U dizininden seçim yap.
5. TV bölümünden kanal seçip kalite/tam ekran denetimlerini kullan.

## Araç ekranı ve üçüncü taraf içerikler

Kullanıcının mevcut POCO F5 + ID.4 kombinasyonunda **AABrowser'ın YouTube sayfasını araç ekranında açtığı** biliniyor; Netflix sayfası görüntüleniyor fakat Netflix videosu çalışmıyor. Bu sonuç kendi uygulamamızın otomatik olarak araçta video göstereceği anlamına gelmez.

Fermata Auto'nun araç entegrasyonunda eski araç SDK'sı / ayrı servis ve bazı kurulumlarda Xposed/kurulum kökeni farklılıkları bulunuyor. AABrowser, kendi Activity/WebView yapısını ve Android Auto ile ilgili intent kategorilerini kullanıyor. Sürücünün dikkatini dağıtmaya yönelik kısıtları aşan yöntemler veya uygulamayı sahte navigasyon aracı gibi kaydetme bu projeye eklenmedi.

**Netflix** DRM korumalı içeriği WebView içinde oynatmayı reddedebilir. **SportzX** üçüncü taraf APK'sı kendi Activity'sini başka bir APK'nın içine gömmez; yetkili yayın/API entegrasyonu doğrulanmadı. Bu işlevler tamamlanmış değildir.

## Kaynak mimarileri ve lisans

- https://github.com/AndreyPavlenko/Fermata — GPL-3.0
- https://github.com/kododake/AABrowser — GPL-3.0

Yeni BrowserStore/MediaWebActivity özellikleri bu projede **bağımsız yazıldı**; referans projelerden kaynak dosyası kopyalanmadı. İleride GPL kodu alınırsa GPL-3.0 kaynak yayımlama ve atıf koşulları yerine getirilmelidir.

---
# ID.4 Media Hub v0.8 — POCO F5 + Android Auto

**Durum:** Kaynak kodu ve JVM testleri kontrol edilmiş Android Studio projesi. **Henüz APK üretilmedi; Android derlemesi, telefonda kurulum ve araç testi yapılmadı.** APTV ile ilişkili veya onaylı resmî bir Android uygulaması değildir.

## v0.8 — Gerçek APK üretimi için hazırlık ve kararlılık

- Android Auto kanal listesi ve arama sayfalaması bellek dostu hale getirildi: yalnızca istenen 20 kayıt Media3 nesnelerine dönüştürülür.
- Hatalı/büyük sayfa numaralarının kontrolü için çevrimdışı Kotlin testleri eklendi.
- Telefonda uzun süren liste indirmesinden sonra Activity kapanmışsa artık sonuç ekranı güncellenmez. Ağ hata metinleri yayın URL'sindeki abonelik tokenlarını açığa çıkarmaz.
- GitHub Actions iş akışı artık **JUnit testi + APK derlemesi + SHA-256 kontrol toplamı + artifakt** üretecek şekilde yapılandırıldı; gerçek GitHub üzerinde henüz denenmedi.
- Test yönergeleri: `docs/APK_DERLEME_REHBERI_v0.8.md`, doğrulama sonuçları: `docs/DOGRULAMA_RAPORU_v0.8.md`.
- **Dikkat:** Ortamda Android SDK ve Gradle olmaması nedeniyle v0.8 için henüz APK yoktur. Üretilmiş veya cihazda doğrulanmış gibi gösterilmemelidir.

## v0.7 — SportzX bağlantısı ve kanal durumu kontrolleri

- Kullanıcının sağladığı **https://sportzxlive.com/** adresi, ayrı bir **"SportzX sitesini tarayıcıda incele"** düğmesiyle açılır. Her açılış için onay ekranı vardır. Sitenin **resmî olması, APK güvenliği veya yayın hakları bağımsız olarak doğrulanamadı**. Otomatik APK indirme/kurma ve site içindeki yayınları uygulamaya aktarma yoktur.
- Telefonda mevcut SportzX uygulamasını açma işlevi korundu. Telefonunda SportzX yüklü değilse artık güvenilirliği doğrulanamayan Telegram kanalına değil, kullanıcı tarafından sağlanan sayfa için onay ekranına yönlendirilir.
- Radyo ve video kanal satırlarına **Kontrol** düğmesi eklendi. Arka planda yalnızca HTTPS `HEAD` isteği gönderir; 200/206, 401/403, 404/410, 405/501 ve 3xx yanıtlarını anlaşılır biçimde sınıflandırır. Yönlendirmeleri otomatik takip etmez, indirme veya video oynatma yapmaz.
- **Önemli:** HTTP 200 yanıtı almış olmak yayın videonun çalıştığı anlamına gelmez. Bazı yayın sunucuları HEAD yöntemini reddeder; bu da yayın bozuk demek değildir. Gerçek oynatım yalnızca telefonda/desteklenen radyo arayüzünde test edilir.
- Yeni `StreamProbeTest.kt` çevrimdışı statü sınıflandırma testleri eklendi. APK, telefon ve araç testleri hâlâ tamamlanmadı.

## v0.6.1 — Açık IPTV kaynakları ve SportzX kısayolu

- **Türkiye açık TV listesi**: `https://iptv-org.github.io/iptv/countries/tr.m3u` bağlantısı uygulamada tek dokunuşla içe aktarılabilir. Topluluk listesinde bozuk, HTTPS dışı, bölge kısıtlı veya yetkisiz kayıtlar bulunabilir; uygulama yalnızca HTTPS bağlantıları alır, çalışırlık garanti edilmez.
- **Haber yayınları**: `https://iptv-org.github.io/iptv/categories/news.m3u` aynı şekilde isteğe bağlıdır.
- **TRT Haber canlı bağlantı adayı**: `https://tv-trthaber.medya.trt.com.tr/master_720.m3u8` TRT sunucusunu kullanır. Gerçek zamanlı test yapılamadı; çalıştığı iddiasında bulunulamaz. Akış yalnızca telefondaki video kanalına eklenir.
- **Apple HLS test videosu**: `https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8` yalnızca telefondaki video oynatıcının HTTPS/HLS yeteneğini test etmek içindir; canlı TV değildir.
- **SportzX**: Telefonda yüklü ve uygulama adı SportzX olan bir uygulama varsa **haricî uygulama olarak açılır**. SportzX APK'sı, üçüncü taraf maç yayınları veya kodları bu projeye eklenmez. Kurulu değilse kullanıcıyı geliştirici kanalına yönlendiren bilgi kutusu görünür.
- **Not:** SportzX'e ait resmî/değişmez bir uygulama paket adı veya geliştirici entegrasyon API'si doğrulanamadığı için Android Auto ekranına SportzX entegre edilmez; kısayol yalnızca telefondadır.

## v0.6: Daha az bellek kullanımı ve araç listesinin güncellenmesi

- Telefonda bir radyo seçildiğinde artık **tek kanal kimliği** ile oynatma isteği gönderilir; uygulama tüm M3U kataloğunu Android Auto oturumuna kopyalamaz.
- Android Auto servisi seçilen kanalı bulup yalnızca **en fazla 41 kanallık yakın oynatma kuyruğu** oluşturur. Bu, binlerce kanallı listelerde işlemin büyüklüğünü sınırlar. Kuyruğun ucuna gelindiğinde tüm 2500 kanalın otomatik dolaşımı hedeflenmez; arayüzden başka bir kanal seçilebilir.
- Telefonda radyo ekleme, silme veya favori güncellemesi yapılınca medya kitaplığı, bağlı Android Auto tarayıcılarına **yerel ve URL içermeyen güncelleme bildirimi** gönderir. Aracın güncellemeye hemen yanıt verip vermediği gerçek testte belirlenecektir.
- Değişiklikler için ek Kotlin/JVM testleri ve kaynak yapısı kontrolleri bulunur.
- Derleme ve araç testi eksikliği devam ediyor: kaynak testlerinin başarılı olması bir APK'nın başarıyla derlendiği anlamına gelmez.

## v0.5: Android Auto kanal başlatma ve gizlilik

- Android Auto araç arayüzü `playFromMediaId` veya `playFromSearch` benzeri isteklerle radyo seçtiğinde `onSetMediaItems()` katalog kimliğini oynatılabilir yayın URL'sine dönüştürür. Bu, önceki sürümde eksik olan önemli bir uyumluluk adımıdır.
- Tek radyo seçilirse ses oynatma kuyruğu genişletilir; **sonraki/önceki** radyo kumandası için uygun liste oluşur. Aracın direksiyon kumandalarının davranışı henüz test edilmemiştir.
- Android Auto kanal listesi artık M3U URL'lerindeki potansiyel erişim tokenlarını cihazlara yayınlanan tarama metadatasında taşımaz; yayın adresi yalnızca oynatma çağrısında kullanılır. Kullanıcının kendisinin oluşturduğu M3U yedekleri yine bağlantıları içerir.
- Eşleme algoritması için ilk 9 JVM test senaryosu eklendi; APK derlemesi yerine geçmez.
- Telif/DRM aşma, Netflix ön ekranı, sürüş sırasında video görüntüsü ve SFD2 müdahalesi yok.

## v0.4'ten devralınan işlevler

1. **Radyo/TV sınıflandırma düzeltmesi:** M3U dosyasında "Radyo Türkiye" gibi gruplar da ses olarak tanınır. İçe aktarımdan sonra türü yanlışsa kanalın yanındaki **TV** (radyo → telefon videosu) veya **♪** (telefon videosu → radyo) düğmesiyle düzeltebilirsin. İki liste arasındaki taşıma, hedefe kayıt başarıyla eklenmeden kaynağı silmez.
2. **M3U yedekleme:** Telefonda "M3U listelerimi yedekle" ile hem ses hem video kayıtları `.m3u` olarak dışa aktarılır. Yedek dosyası izin verdiğin konuma Android belge seçiciyle kaydedilir; Google veya haricî servislere otomatik yükleme yapılmaz. Favori işaretleri standart M3U içinde tutulmaz.
3. **Arama ve sayfalama:** Radyo/TV için ayrı arama, favoriler ve sayfa başına 20 sonuç. Büyük IPTV listelerini tek seferde binlerce görünüm oluşturarak ekrana basmaz.
4. **Android Auto ses kitaplığı:** "Favoriler", "Tüm radyolar" ve "Radyo grupları" klasörleri. `MediaLibraryService` üzerinden ses oynatma ve medya kumandaları.
5. **HLS ses:** `.m3u8` radyo bağlantılarında Media3'e HLS MIME türü bildirilir.
6. **Teşhis:** Oynatma hataları `ID4MediaHub` etiketiyle Android logcat içine kaydedilir. `scripts/diagnostics_windows.cmd` yalnızca ADB üzerinden paket ve medya oturumu bilgilerini okur, cihaz ayarlarını değiştirmez.
7. **Kararlılık:** Medya kitaplığında geçersiz sayfalama istekleri hata olarak yanıtlanır, çökme önlenir.

## Telefon / araç fonksiyonları

| İşlev | POCO F5 | ID.4 / Android Auto |
|---|---|---|
| HTTPS M3U/M3U8 listesi içe aktar, ara, favorile | Evet | Ses listesi görüntülenir |
| Radyo ve ses yayınlarını oynat | Hedef | Hedef: hareket halinde oynatma |
| Direksiyon kontrolleri, oynat/duraklat | Hedef | Android Auto medya arayüzüyle hedef |
| IPTV video/HLS | Telefon oynatıcısı | **Hayır** |
| Netflix | Resmî uygulamaya kısayol | **Hayır** |
| YouTube Music | Resmî uygulamaya kısayol | YouTube Music kendi Android Auto uygulaması üzerinden |

Kendi yasal/izinli yayın adreslerini kullan. Bu proje telifli APTV kodu, değiştirilmiş Premium APK veya DRM aşma yöntemi içermez. ID.4 bilgisayarına, hız sinyaline veya güvenlik korumalarına müdahale etmez. Araç hareket halindeyken ön ekrana video yansıtmaz.

## Windows'ta APK derleme

1. ZIP'i açıp `ID4_Media_Hub_v0_8` klasörünü Android Studio'da **File > Open** ile aç.
2. **JDK 17**, **Android SDK Platform 36**, AGP **8.11.1** ve Kotlin **2.2.20** gerekir. Gradle **8.13** sürümünü kullan; Gradle Wrapper JAR bu projede yoktur. Android Studio üzerinden Gradle ayarlarını doğrula.
3. **Sync Project with Gradle Files** ardından **Build > Build APK(s)**.
4. Başarılı olduğunda `app/build/outputs/apk/debug/app-debug.apk` oluşur. Telefonu USB ile bağla, bilgisayarda ADB kuruluysa `adb install -r app-debug.apk` komutunu kullan.
5. Telefonda ID.4 Media Hub uygulamasını aç ve varsayılan internet radyosunu test et. Ardından Android Auto üzerinden aracın medya uygulamaları listesine bak. Geliştirici APK'sı Android Auto tarafından gizlenebilir; listede görünmesi veya oynatma çalışması henüz doğrulanmadı.
6. Android Auto uygulama görünürlüğü için gerekli ise **Android Auto geliştirici ayarları > Bilinmeyen kaynaklar** özelliği kullanılabilir. Her Android Auto sürümünde aynı davranışı garanti etmez. Aracın ECU/firmware yazılımını değiştirmen gerekmez.

**Alternatif derleme:** Kaynakları *kendi* GitHub depona yüklersen `.github/workflows/android-debug-apk.yml` altında manuel tetiklenebilen bir GitHub Actions iş akışı mevcuttur. Google depolarına erişebilen CI ortamında derlemeyi dener; başarısı henüz doğrulanmadı. İş akışının ürettiği hata dökümünü kontrol et.

## Testler (SDK gerekmez)

```bash
python tests/architecture_checks.py
kotlinc app/src/main/java/tr/id4/mediahub/M3uParser.kt app/src/main/java/tr/id4/mediahub/StreamProbe.kt tests/StreamProbeTest.kt -include-runtime -d /tmp/stream-probe.jar && java -jar /tmp/stream-probe.jar
kotlinc app/src/main/java/tr/id4/mediahub/RadioQueuePlanner.kt tests/RadioQueuePlannerTest.kt -include-runtime -d /tmp/radio-queue.jar && java -jar /tmp/radio-queue.jar
kotlinc app/src/main/java/tr/id4/mediahub/M3uParser.kt tests/M3uParserTest.kt -include-runtime -d /tmp/parser.jar && java -jar /tmp/parser.jar
kotlinc app/src/main/java/tr/id4/mediahub/M3uParser.kt app/src/main/java/tr/id4/mediahub/M3uExporter.kt tests/M3uExporterTest.kt -include-runtime -d /tmp/exporter.jar && java -jar /tmp/exporter.jar
```

Bu testler Kotlin/JVM yardımcı kodlarının davranışını kontrol eder. **Android derlemesi veya ID.4 uyumluluk testi yerine geçmez.**

## Gerçek derleme durumu (9 Ekim 2026)

Bu geliştirme ortamında Java 21 ve Kotlin derleyicisi bulundu. **Android SDK, Gradle ve Android bağımlılıkları yerel ortamda yoktu; dış indirme sunucularına DNS erişimi de sağlanamadı.** Bu nedenle `:app:assembleDebug` çalıştırılmadı, herhangi bir `.apk` üretilmedi. XML/kaynak denetimi ve yerel JVM testleri çalıştırıldı. Windows'ta Android Studio ya da GitHub Actions workflow üzerinden **gerçek derleme sonucu** alınmalıdır. Ardından POCO F5 ve Volkswagen ID.4 üzerinde kullanım testleri gerekir.

## Çalışma ve sınırlamalar

- Kaynaklar yalnızca `https://` bağlantılarını kabul eder; bazı IPTV sağlayıcılarının `http://`, oturum açma veya DRM gerektiren yayınları desteklenmez.
- Herhangi bir M3U8 HLS bağlantısını doğrudan kanal listesi olarak yorumlamaz; bu adresleri M3U dosyasında kanal URL'si olarak referans verebilirsin.
- Dışa aktarılan M3U, yayın URL'sindeki erişim anahtarlarını/tokenları da içerebilir. Bu yedekleri kimseyle paylaşma.
- Otomobil bağlantısı, modem/operatör, servis politikaları ve yayın formatı nedeniyle uyumluluk değişebilir.
- Herhangi bir adımda hata olursa `scripts/diagnostics_windows.cmd` kullanabilirsin; çıktı diğer uygulamalara dair kişisel metadata içerebilir, paylaşmadan önce gözden geçir.

Google'ın resmî Android Auto medya desteği:
https://developer.android.com/media/implement/surfaces/cars
https://developer.android.com/media/media3/session/serve-content
https://developer.android.com/training/cars/media/auto

Resmî video kapsamı:
https://developer.android.com/agents/skills/cars/android-for-cars-parked-apps/skill


## Güncelleme bildirimi nasıl işler?

Radyo kataloğu telefonda değişince `StationStore.save()` yalnızca `tr.id4.mediahub.CATALOG_UPDATED` eylemiyle uygulama içine yayın gönderir. `AudioLibraryService` kayıtlı `BroadcastReceiver` ile bunu alır; `MediaLibrarySession.notifyChildrenChanged()` aracılığıyla radyo ve grup klasörlerini yeniletir. M3U bağlantıları ve erişim tokenları bildirime eklenmez. Bu Android Auto'nun canlı katalog güncelleme mekanizmasını kullanır; Bluetooth/Android Auto'nun araç davranışını değiştirmez.

## v0.6 için henüz doğrulanmamış noktalar

- Android Studio/Gradle ile tam `assembleDebug` derlemesi.
- POCO F5 üzerinde yükleme, arka planda oynatma, ses kesintisi ve kilit ekranı kumandaları.
- Volkswagen ID.4 Pure ekranında görünürlük, kontrol ve katalog değişikliği bildirimleri.
- Gerçek HTTP/HLS yayın sunucusu testi (yerel testler yalnızca örnek URL/playlist üzerinde çalışır).

## Güvenlik sınırı

Android Auto sürüş ekranına Netflix/IPTV videosu aktarılmaz; sürüş video kilitlerini aşan kod, SFD2/ECU yazılım değişimi, yetkisiz Premium APK veya DRM atlatma içermez. **Müzik ve sesli yayınlar** için Android Auto'nun resmî medya kitaplığı kullanılır. Telefon ekranında videoyu yalnızca park halindeyken kullan.
