# v0.36 — 14 Ekim 2026

- Mola 68,8 saniyelik video kaydından ayrı ekran gezinme ve tasarım envanteri.
- Kalıcı Media3 radyo oturumu paneli, Mola tarzı ana sayfa kart dizisi.
- Kategorilere ayrılmış TV kanal listesi: arama, favoriler, ızgara/liste ve oynatma.
- Kullanıcının kendi RSS podcast kaynakları, OPML içe/dışa aktarma ve bölüm oynatma.
- Kişisel HTTPS kısayolları; önceki M3U/EPG/YouTube/Netflix/Prime Video korunur.
- Telifli Mola/REC TV kodu, kapalı akışlar ve sürüşte video kısıtlarının aşılması yoktur.

# v0.35 — 14 Ekim 2026

- Mola görsel hiyerarşisinden esinlenerek Film, Dizi, Maçlar için yeni yerel medya ekranı.
- M3U kategori ayrıştırma, kullanıcı kontrollü HTTPS kaynak ekleme, arama, favoriler.
- Telefonun Media3 oynatıcısında film/dizi ilerleme kaydı ve DASH medya türü tanıma.
- REC TV'nin telifli yayınları, APK kaynak kodu veya özel API erişimi kopyalanmadı.
- Sürüşte araç ekranı video güvenlik kısıtlamaları değişmedi.

# v0.34 — 14 Ekim 2026

- REC TV 22.0.2 (161) kullanıcı APK'sının manifest/Flutter bileşenleri teknik incelendi.
- Telefon kurulumunda bulunursa Uygulamalar ekranından `REC TV'yi telefonda aç` kısayolu eklendi.
- REC TV uygulama kimliği `com.rectv.shot` için Android paket görünürlüğü desteği.
- Kaynak kodu, uygulama ikonları, telifli film/TV içeriği veya özel sunucu bağlantıları kopyalanmadı.
- YouTube/Netflix/Prime Video kartları resmî uygulamaları açmaya devam ediyor.

# v0.33 — 13 Ekim 2026

- Uygulamanın başlatıcı ve görünür ana ekran adı **ID.4 Yol Arkadaşım** oldu.
- YouTube, Netflix ve Prime Video büyük kartları ve ilgili telefon kısayolları doğrudan kurulu resmî Android uygulamasını açar; WebView'a otomatik geçiş yoktur.
- Paket kimliği, kullanıcı kayıtları ve ayrı Web Medya özelliği korunur.
- Android Auto sürüş halinde video kısıtlarına müdahale edilmedi.

# v0.32 — 13 Ekim 2026

- Media Cockpit üzerinde Mola ekranından esinlenen, orijinal büyük YouTube / Netflix / Prime Video poster kartları.
- Sinema görsel alanı, yuvarlatılmış koyu servis kartı, renkli çerçeve, büyük başlık ve yuvarlak açma düğmesi.
- Kartlar eski küçük Netflix/Prime web butonlarının yerini aldı; telefon içi oynatıcı ve ayrı geçmişler korundu.
- Android Auto'nun sürücü video kısıtları veya DRM sistemi değişmedi.

# v0.31 — 13 Ekim 2026

- Netflix ve Prime Video için telefon içinde ayrı içerik geçmişi ve tekrar açma.
- Platforma özel güvenli URL tanıma: Netflix watch/title, Prime Video detail, Amazon gp/video/detail.
- Web Medya geçmiş düğmeleri, kendi kısayolları ve isteğe bağlı resmî uygulama açma.
- Media Cockpit ana ekranında Netflix ve Prime Video erişimi.
- Takip parametrelerini kaydetmeyen ayrıştırma testi; Android 15 emülatör kontrolleri.
- DRM çözme, otomatik içerik indirme ve araç hareketinde video kısıtı aşma yok.

# v0.30 — 13 Ekim 2026

- FermataX derlenmiş uygulamasındaki YouTube sınıf isimleri referans alındı; özgün YouTube URL kanonikleştirme ve gizliliği koruyan video geçmişi geliştirildi.
- Web Medya'da YT◷ menüsü ile yerel son 30 videoyu açma ve ayrıca temizleme.
- YouTube WebView SPA gezinmesindeki video geçişleri, başlık yenileme ve yalnızca video kimliğini saklama desteği.
- HTTPS/alan adı/11 karakterli video kimliği kontrolü ve takip parametrelerini atma üzerine JUnit testleri.
- Emülatör smoke testinde kaydırmalı TV kontrol araması ve YouTube geçmiş düğmesi denetimi.

# v0.29 — 13 Ekim 2026

- FermataX 2.2.6 (332) kullanıcı manifestindeki açık `ACTION_VIEW` medya etkinliği üzerinden kullanıcı onaylı, HTTPS TV/radyo telefon aktarımı.
- TV oynatıcısına **FermataX 2.2.6 ile telefonda aç** seçeneği; eksik uygulama, hatalı URL ve reddedilen Android Intent için açıklayıcı hatalar.
- Uygulama bağlantısının erişim anahtarı taşıyabileceği onaydan önce uyarılır. M3U listeleri, şifreler ve URL'ler raporlara yazılmaz.
- Emulator runner disk yetersizliği için `-partition-size 2048` ve Media Cockpit ekran kontrolü düzeltildi.
- FermataX proje kodu/kapalı APK kopyalanmadı ve Android Auto video kısıtları değiştirilmedi.

# v0.28 — 12 Ekim 2026

- Media Cockpit, eski ana ekranı değiştirir: uyarlanabilir 6 işlev kutusu, gerçek Media3 oturumu, hızlı geçmiş ve favoriler.
- Kaldığın yerden oynatma ve EPG durum bilgisi, URL/token içermeyen ortak model üzerinden çalışır.
- EPG kanal ID eşleştirme ve ilerleme çubuğu; favori ve geçmiş kartlarında tekrar tekrar depolama okuması kaldırıldı.
- Android 15 UI smoke testi artık Ana Sayfa'daki Media Cockpit oturumu ve Entegrasyon Merkezi kısayolunu doğrular.
- Başarılı Android Auto video/sürüş oynatımı iddia edilmez; yalnızca belgeli entegrasyonlar kullanılır.


# v0.27 — 12 Ekim 2026

- ID.4 Drive Hub: özgün lacivert/turkuaz/amber arayüz; uyarlanabilir ana sayfa, büyük medya kartları ve yeni uygulama simgesi.
- Yerel TV/radyo/favori sayaçları, canlı radyo oynatma durumuyla senkronize devam kartı ve eski ID'leri filtreleyen DriveHomePlanner.
- Canlı TV, radyo, Web Medya, Kitaplık, YouTube/Netflix ve Android Auto ses tanılama tek arayüzde.
- Aynı uygulama kimliği ve depolama; mevcut IPTV/radyo oynatıcıları ve Ortak Entegrasyon Merkezi yeniden kullanılır.
- DriveHomePlanner JUnit testleri ve Android 15 emülatöründe Drive Hub ana ekran doğrulaması eklendi.
- Gerçek araç video ekranı, hareket halinde video veya sahte OBD statüsü iddia edilmez.

# v0.26 — 12 Ekim 2026

- Kullanıcının FermataX 2.2.2 debug APK dosyasına salt okunur ZIP ve AndroidManifest analizi uygulandı; kaynak ile sürüm farkı belirtildi.
- Ortak Entegrasyon Merkezi'nde doğrulanmış `me.app.fermataX.auto.debug`, stable `me.app.fermataX.auto` paketleri ve açma kısayolu.
- Media3 oynatıcıya `media3-exoplayer-dash` modülü; `.mpd` dosyaları DASH, `.m3u8` HLS olarak tanınır.
- Telefon Ana Sayfa'sında son TV/radyo içeriği için özgün hızlı devam kartı.
- Yeni JUnit format ve paket tespiti testleri. LibVLC ikilileri lisans/provenans sebebiyle dahil edilmedi.

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
