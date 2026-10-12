# v0.25 — 12 Ekim 2026

- IPTV kanal listesindeki XMLTV programları için APTV esinli ŞİMDİ/SONRA kartları, süre ilerleme çubuğu ve yayın adına göre arama.
- TV kanal seçim ekranına "📺 Şimdi yayında" kategorisi; EPG verileri her kart için tekrarlı depolama okuması olmadan tek indeksle eşleştiriliyor.
- M3U başlık bilgisinden x-tvg-url / url-tvg / tvg-url kaynaklarını ayıklama; yalnızca geçerli HTTPS kabul ediliyor.
- M3U dosyası EPG kaynağı önerirse otomatik indirme yerine kullanıcı onayı isteyen rehber yükleme penceresi.
- EPG indeks sınır koşulları, program adı araması ve HTTPS rehber keşfi için yeni JUnit testleri.
- Harici APK birleşimi, hız/vites değişikliği, Android Auto sürüş kısıtı atlatma veya Netflix DRM kırma bulunmaz.

# v0.24 — 12 Ekim 2026

- Tek menü: Ortak Entegrasyon Merkezi. Uygulama türü, kurulu sürüm, dahili/harici/diagnostic/referans sınıfı ve gerçekleştirebildiği eylemleri tek ekranda gösterir.
- Fermata, Screen2Auto, ScreenOnAuto, AABrowser, Shizuku, AAAD, CarStream, SportzX, MirrorMobile, DiPlay, Android Auto, Media3 ve APTV-esinli EPG kaynaklarını belgeli envanterde harmanlama.
- Kullanıcı başlatmalı harici uygulama açılışı ve ScreenOnAuto resmî Intent başlat/durdur düğmeleri; ek APK indirmesi yapılmaz.
- Paket görünürlüğü yalnızca belirli isimlerle sınırlandırıldı. Durum raporu IPTV URL'si veya giriş anahtarı içermez.
- Tüm kaynaklar için JUnit doğrulaması ve entegrasyon matrisi eklendi.

# v0.23 — 12 Ekim 2026

- APTV esinli M3U + XMLTV (EPG) program rehberi: HTTPS XMLTV/XMLTV.GZ indirme, güvenli XML ayrıştırma, yerel sınırlı önbellek.
- M3U tvg-id ve tvg-logo içe/dışa aktarma, kanal kaydında saklama ve TV oynatıcısında Şimdi / Sonra gösterimi.
- ScreenOnAuto resmi belgelenmiş kullanıcı tetikli başlat/durdur komutları ile isteğe bağlı birlikte çalışma.
- XMLTV ile EPG kimlik eşleştirme, DTD ve dış XML entity reddi için JUnit testleri.
- APTV'nin Apple/CarPlay kodları ve uygulama markası kopyalanmadı; Android Auto'da sürüş esnasında video atlatma işlevi yok.

# v0.22 — 12 Ekim 2026

- Resmî Shizuku 13.1.5 API ile bağlantı/izin durumunu salt okunur kontrol.
- AAAD ve Fermata Mobile/Auto uygulamalarının yüklü sürümünü tespit.
- Fermata yüklüyse yalnızca telefonda manuel açma; Android Auto video projeksiyonu veya sürüş kısıtını atlatma uygulanmaz.
- Kitaplık ekranına yardımcı uygulama durum testi, araç bağlantı raporuna aynı tanılama.
- Android 15 emülatöründe yardımcı uygulamalar bulunmadığında güvenli hata yönetimi ve arayüz kontrolü.

# v0.21 — 11 Ekim 2026

- Aktif Web Medya sekmesini AABrowser'a kullanıcı komutuyla HTTPS / ACTION_VIEW üzerinden aktarma düğmesi.
- Bağlantı hedefi kurulu değilse kontrollü uyarı; rastgele tarayıcıya yönlendirme yok.
- Android 15 emülatöründe AABrowser kurulu değilken hata ekranı doğrulama senaryosu.
- AABrowser'a telefon üzerinden bağlantı gönderme, gerçek araç ekranında video gösterimini kanıtlamaz.

# v0.20 — 11 Ekim 2026

- Media3 ses kitaplığına ek olarak Android 15 üzerinde **platform MediaBrowser** ile gerçek kök kategori taraması yapabilen yerel tanılama eklendi.
- Legacy servis kökü ve üç kategori doğrulaması; 10 saniyelik zaman aşımı; başarısızlık mesajları.
- Android emülatör iş akışında hem Media3 hem legacy tarama kontrolü zorunlu.
- Tanılamalar yalnızca telefon içinden yapılır; Android Auto araç ekranı video erişimi henüz doğrulanmamıştır.

# v0.8 — 9 Ekim 2026

- MediaLibraryService: önce liste sayfalama, sonra Media3 dönüşümü; gereksiz nesne üretiminin azaltılması.
- `LibraryPage.kt` yardımcı sınıfı ve 10 Kotlin sınır/taşma testi.
- Telefon Activity yaşam döngüsü düzeltmeleri ve IPTV bağlantılarının erişim anahtarı içerebilen hatalarının güvenli mesajlarla değiştirilmesi.
- GitHub Actions: JUnit/Gradle build, APK varlık kontrolü, SHA-256 ve 14 günlük artifakt.
- Yeni `MediaHubUnitTest.kt` dört test, Windows derleme betiği, `.gitignore`, APK ve doğrulama rehberi.
- Çevrimdışı testler geçti; tam Android APK derlemesi ve araç testi hâlâ yapılmadı.

# Değişiklikler — v0.7

- SportzX kullanıcı adresi için onaylı, haricî tarayıcı kısayolu; rastgele APK indirilmez.
- Kaynağı doğrulanmamış önceki Telegram yönlendirmesi kaldırıldı.
- Radyo/IPTV bağlantıları için indirimsiz, yönlendirmesiz HEAD kontrolü ve kullanışlı hata durumları.
- HTTP yanıtı yayın oynatımının kanıtı olarak gösterilmez; HTTPS şartı korunur.
- Çevrimdışı birim testleri genişletildi; mevcut v0.6.1 işlevleri korundu.
- Kaynak paketidir: Android SDK olmadan tam APK derlemesi yapılamadı.

# 0.6.1 — 9 Ekim 2026

- TRT Haber için TRT CDN üzerinde yayınlanan HLS video kısayolu (çalışırlık telefonda doğrulanacak)
- Türkiye/haber açık IPTV listesi içe aktarma düğmeleri; Apple HLS telefon test videosu
- Kurulu SportzX uygulamasını ada göre bulan güvenli dış uygulama kısayolu
- SportzX APK içeriği/maç yayınları gömülmedi, araç ekranına video aktarılmıyor

# Değişiklikler

## 0.6.0 — 2026-10-09
- Tek radyo seçimini yalnızca tek medya kimliği üzerinden oturuma gönderme; çok büyük Binder isteği riskini azaltma.
- Medya oturumu oynatma listesini seçilen öğenin çevresinde en fazla 41 radyo ile sınırlama.
- Radyo/favori değişince medya tarayıcılarının klasörlerine URL/token içermeyen güncelleme bildirimi.
- 2500 kayıt senaryolarını kapsayan Kotlin testleri ve yeni kaynak kontratı denetimleri.
- Hâlâ Android SDK / araç testi yok; gerçek APK derlemesi doğrulanmadı.

## 0.5.0 — 2026-10-09
- `onSetMediaItems` Android Auto/legacy MediaController oynatma isteklerini destekler
- Ses kanalı seçildiğinde ileri/geri radyo kuyruğu oluşturma
- Ses kataloğunun tarama/metadata öğelerinde yayın URL'sini gizleme
- Radyo seçimi için 9 JVM test senaryosu
- Hatalarda yayın URL/token dökümleri bulunmaması için Log kullanımını sınırlandırma
- Test rehberi ve derleme durumunu netleştirme

## 0.4.0
- M3U dışa aktarma, grup/arama, ses-video tür değiştirme, HLS ve Android Auto klasörleri
