# ID.4 Media Hub v0.6 — Test ve kabul ölçütleri

## 1. APK derleme (gerekli, henüz yapılmadı)
- [ ] Android Studio > Open > proje klasörü; Gradle Sync başarıyla tamamlandı
- [ ] `gradle :app:assembleDebug` hatasız bitti ve `app/build/outputs/apk/debug/app-debug.apk` üretildi
- [ ] POCO F5: `adb install -r app-debug.apk` başarılı
- [ ] Android 15 / HyperOS 3 üzerinde uygulama açıldı

## 2. Telefon
- [ ] Varsayılan SomaFM radyosu çalıyor; durdur/oynat çalışıyor
- [ ] MP3/AAC ve HLS radyosu aynı kaynakla açılıyor
- [ ] M3U listesi içe aktarma, arama, favori ve M3U dışa aktarma çalışıyor
- [ ] Video HLS yalnızca telefonda oynuyor; Android Auto kataloğunda yer almıyor
- [ ] Netflix kısayolu resmî telefon uygulamasını açıyor

## 3. Araç / Android Auto
- [ ] Araç sabitken ID.4 Media Hub Android Auto medya uygulaması listesinde görünüyor
- [ ] Kanala dokununca yayın başlıyor (MediaSession `onSetMediaItems` eşleme)
- [ ] Sonraki/önceki ses kanalı ve oynat/duraklat kumandaları çalışıyor
- [ ] İnternet kesilince uygulama kapanmıyor, kullanıcıya hata görünür oluyor
- [ ] Araç güvenli bir sürüş testi sırasında **yalnızca ses** oynatmaya devam ediyor
- [ ] Katalogda video/Netflix seçeneği bulunmuyor

## 4. Güvenlik ve sınırlamalar
- [ ] Otomobilin orijinal sistem yazılımına dokunulmuyor
- [ ] Sürüş sırasında ön ekrana video görüntüsü verilmiyor
- [ ] Radyo kanal tarama metadatasında tam URL / erişim tokenı yok
- [ ] M3U yedek dosyaları gizli tutularak saklanıyor

**Not:** Bu liste yerine getirilmiş testler değil, test planıdır. Henüz Android SDK derlemesi ve gerçek araç testi yapılmadı.


## v0.6 ek kontroller

- [ ] Telefon üzerinde en az 100 radyoluk liste içe aktar, ortadaki bir istasyonu başlat: ses başlamalı.
- [ ] Direksiyondan ileri/geri: seçilen istasyon çevresinde makul kanal geçişleri gerçekleşmeli.
- [ ] Araba medya sayfasını açık tut, telefonda yeni radyo ekle/sil: liste güncelleniyor mu kontrol et (gerekirse sayfayı tekrar aç).
- [ ] Favoriler klasöründeyken favori ekle veya kaldır: sınıflandırma değişiyor mu?
- [ ] Radyo yayını çalarken telefon ekranını kilitle ve Android Auto'yu kullan: ses devam ediyor mu?
- [ ] İnternet bağlantısını kes/aç: uygulama çökmeden hata bildiriyor mu?
- [ ] 41 öğelik kuyruk ucu: bu sınırın ötesine sonsuz geçiş bekleme; doğrudan kanal seçimi çalışıyor mu kontrol et.
- [ ] Araç park halindeyken telefonun kendi ekranındaki video oynatımını test et; ID.4 ön ekranında görüntü arama.

Not: Bu liste gerçek testler yapılana kadar tamamlanmış kabul edilmez.
