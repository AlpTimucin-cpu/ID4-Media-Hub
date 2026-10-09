# ID.4 Media Hub — Android / Android Auto

Kişisel deney projesi. **Google, Volkswagen, Netflix veya SportzX tarafından onaylanmış bir uygulama değildir.**

## Durum
- **v0.11 beta:** Ana Sayfa / TV / Radyo / Kitaplık; entegre IPTV oynatıcısı, kanal geçişi, çözünürlük üst sınırı, tam ekran, logolu kanallar, Radio Browser Türkiye dizini, otomatik günlük radyo yenileme, türler ve favoriler
- Android Auto tarafında sadece ses/radyo medya kitaplığı hedeflenir; **ID.4 uyumluluğu doğrulanmamıştır**
- IPTV kaynaklarının çalışması, erişim hakları ve coğrafi kısıtlamalar değişebilir
- Netflix, YouTube Music ve SportzX uygulamanın içinde oynatılmaz; ilgili resmî/kurulu uygulamaya yönlendirilir
- Sürüş sırasında sürücünün görebildiği ön ekranda video oynatmak için kısıtlama devre dışı bırakılmaz

## Derleme
GitHub Actions > **Build ID4 Media Hub APK** iş akışının başarılı çalışması beklenir. İndirilecek `ID4-Media-Hub-debug-APK` dosyası içindeki `app-debug.apk` yüklenebilir test sürümüdür.

**Önemli:** APK'nın derlenmiş olması telefonda/araçta çalıştığını kanıtlamaz. Gerçek cihaz testleri ayrıca yapılmalıdır.

## İlk cihaz testi (POCO F5)
1. Uygulamayı aç. **RADYO** sekmesinde Türkiye dizininin indirilmesini bekle; internet kullanılır. İlk indirme başarısız olursa **GÜNCELLE** düğmesine dokun. Önbellek varsa çevrimdışı da görüntülenir.
2. **Kitaplık** bölümünden Apple HLS test videosunu ekle, **TV** bölümündeki tek oynatıcıda seçip oynat. Kanal panelini, kalite seçimini ve tam ekranı dene.
3. Bu test başarılıysa M3U listesini yükle.
4. Kanal başlatılamıyorsa ekrandaki hata metnini kaydet; URL'deki erişim anahtarlarını paylaşma.
5. Android Auto'da uygulama görünmüyorsa, bunu telefonun çalışıp çalışmamasından ayrı hata olarak bildir.

## Kaynak
`app/src/main/java/tr/id4/mediahub` • Kotlin / Media3 • `app/src/main/AndroidManifest.xml`

Kod önceliği: düzgün telefon deneyimi, güvenilir ses oynatma ve ayrıntılı hata durumları. Sonuçlar cihazda doğrulanmadan 'tamamlandı' sayılmaz.

## Radyo kataloğu (v0.11)
- Topluluk katkılı [Radio Browser](https://www.radio-browser.info/) API üzerinden Türkiye (TR), son kontrolde çevrimiçi görünen HTTPS istasyonları alınır.
- İlk açılışta iki örnek istasyon bulunur; arka plan güncellemesi başarılıysa Türkiye radyoları otomatik eklenir.
- Dizindeki kayıtların gerçek cihazda oynatılabilmesi garanti edilmez (bölge, format, sunucu, HTTPS vb.).
- Son başarılı indirme zamanı cihazda tutulur; yaklaşık 24 saatte bir yenileme denenir.
- Kullanıcının eklediği istasyonlar ve favori bilgisi korunur.
- Radio Browser'ın sunucu erişimi başarısızsa uygulama kayıtlı kanalları göstermeye devam eder.

## Test kurulumunda önemli not
GitHub Actions debug APK'ları farklı runner'larda farklı debug anahtarlarıyla imzalanabilir. Android mevcut sürümün üzerine güncellemeyi reddederse **önce M3U kanal listesini yedekle**; kaldırma işleminden önce verileri kontrol et. Bu sürüm Play Store sürümü değildir.
