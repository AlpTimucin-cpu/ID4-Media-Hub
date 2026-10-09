# ID.4 Media Hub — Android / Android Auto

Kişisel deney projesi. **Google, Volkswagen, Netflix veya SportzX tarafından onaylanmış bir uygulama değildir.**

## Durum
- **v0.9 (test aşaması):** Telefonda Radyo / IPTV / Ayarlar ekranları; ses oynatma altyapısı; M3U/M3U8 içe aktarma ve telefon videoları
- Android Auto tarafında sadece ses/radyo medya kitaplığı hedeflenir; **ID.4 uyumluluğu doğrulanmamıştır**
- IPTV kaynaklarının çalışması, erişim hakları ve coğrafi kısıtlamalar değişebilir
- Netflix, YouTube Music ve SportzX uygulamanın içinde oynatılmaz; ilgili resmî/kurulu uygulamaya yönlendirilir
- Sürüş sırasında sürücünün görebildiği ön ekranda video oynatmak için kısıtlama devre dışı bırakılmaz

## Derleme
GitHub Actions > **Build ID4 Media Hub APK** iş akışının başarılı çalışması beklenir. İndirilecek `ID4-Media-Hub-debug-APK` dosyası içindeki `app-debug.apk` yüklenebilir test sürümüdür.

**Önemli:** APK'nın derlenmiş olması telefonda/araçta çalıştığını kanıtlamaz. Gerçek cihaz testleri ayrıca yapılmalıdır.

## İlk cihaz testi (POCO F5)
1. Uygulamayı aç, **RADYO** sekmesindeki deneme radyosunu seç.
2. **IPTV** sekmesinden Apple HLS test videosunu ekle ve oynat.
3. Bu test başarılıysa M3U listesini yükle.
4. Kanal başlatılamıyorsa ekrandaki hata metnini kaydet; URL'deki erişim anahtarlarını paylaşma.
5. Android Auto'da uygulama görünmüyorsa, bunu telefonun çalışıp çalışmamasından ayrı hata olarak bildir.

## Kaynak
`app/src/main/java/tr/id4/mediahub` • Kotlin / Media3 • `app/src/main/AndroidManifest.xml`

Kod önceliği: düzgün telefon deneyimi, güvenilir ses oynatma ve ayrıntılı hata durumları. Sonuçlar cihazda doğrulanmadan 'tamamlandı' sayılmaz.
