# Elektrikli Yol Arkadaşım — Sürüm Notları

Bu belge güncel kullanıcıya açık uygulamanın değişikliklerini özetler. Eski teknik deneyler ve bunların özgün kayıtları Git sürüm geçmişinde korunur. Telif sahipliği ve üçüncü taraf lisans bildirimleri [LICENSE](LICENSE), [NOTICE.md](NOTICE.md) ve [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) dosyalarındadır.

## 26 Ekim 2026 — Entegrasyon ve marka yenilemesi

- Görünen Android uygulama adı ve kokpit başlığı **Elektrikli Yol Arkadaşım** olarak güncellendi.
- Özgün elektrikli otomobil/rota uygulama simgesi ve daha sade medya kartları hazırlandı.
- Harita üzerinde tek Leaflet örneği korunarak gerçek OSRM GeoJSON rota geometrisi ve mevcut açık harita altlığı kullanıldı. Var olan güzergâh/istasyon fonksiyonları değiştirilmedi.
- Android'deki doğrulanmış Bluetooth OBD ölçümleri, mevcut yerel olay köprüsü üzerinden arayüze aktarılmaya devam ediyor. Sahte SoC, voltaj, hız veya kalan menzil gösterilmiyor.
- Rota ilerleme çubuğu yalnız yeni ve yeterince doğru GPS konumunu rota geometrisine izdüşürerek güncelleniyor. Araç ikonu çubuk sınırları içinde tutuluyor.
- Harita üzerindeki hava etiketleri ve alternatif rota çizgileri daha okunaklı olacak şekilde sınırlandırıldı; veri ayrıntıları korunuyor.
- Trafik katmanının gerçek yükleme durumu gösteriliyor; trafik verisi doğrulanmamışsa canlı durum etiketi kullanılmıyor.
- Kişisel trafik servis anahtarının cihazda şifreli saklanması ve eski yerel ayarlardan güvenli taşınması eklendi.
- Yayın öncesi kaynak, görünür marka, sürüş güvenliği ve APK paketleme kontrolleri güçlendirildi.
- Teknik entegrasyon: [Harita, araç verisi ve rota ilerleme rehberi](docs/ELEKTRIKLI_YOL_ARKADASIM_ENTEGRASYON.md).

## 14–20 Ekim 2026 — Medya ve rota geliştirmeleri

- Canlı TV, radyo, podcast, yerel medya ve web özellikleri için modüler kullanıcı arayüzü.
- Var olan medya oynatıcı oturumlarının korunması; ağ kopması, oynatıcı yeniden bağlanma ve kullanıcı dostu hata açıklamaları.
- Rota arama, alternatif güzergâh, istasyon önerisi ve örnek veriden ayrılan enerji tahminleri.
- Ekran yönü, telefon görünümü ve araç ekranındaki kart yerleşimlerinin iyileştirilmesi.
- Harita, GPS, telemetri ve bağlantı izni hatalarında çökmeyi önleyen korumalar.

## Yayın / kullanım notu

Sürüm **test aşamasındadır**. Otomatik kaynak ve derleme testlerinin geçmesi, gerçek cihazda canlı trafik ya da OBD bağlantısının doğrulandığı anlamına gelmez. Sürücü dikkatini dağıtabilecek görsel medya için hareket hâlindeki kullanım kısıtlamaları kaldırılmamıştır.
