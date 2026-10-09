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
