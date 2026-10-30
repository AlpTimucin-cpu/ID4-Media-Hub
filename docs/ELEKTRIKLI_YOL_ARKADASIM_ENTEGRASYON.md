# Elektrikli Yol Arkadaşım — Harita, Canlı Araç Verisi ve Rota İlerleme Entegrasyonu

> Kaynak: Kullanıcının yüklediği `tesla_integration_guide.docx` teknik rehberindeki üç bölüm. Rehberdeki kod örnekleri gerçek projeye doğrudan yapıştırılmamıştır; çalışır mimariye uyarlanmıştır.

## 1. Tek harita, gerçek OSRM geometrisi

Uygulama halihazırda `navigator.html` içinde **bir** `L.map('map')` açıyor. `id4-live-map.js` ve trafik sağlayıcısı buna `attachExistingMap(map)` ile bağlanır; ikinci bir `L.map('map-canvas')` oluşturulmaz.

Gerçek rota `/route/v1/driving/{lon},{lat};{lon},{lat}?overview=full&geometries=geojson` OSRM şemasını izler. Yanıt `code='Ok'`, `routes[0].geometry.coordinates`, mesafe ve süre kontrolünden sonra Leaflet polyline ile çizilir. Rota hesapları ağ hatası, zaman aşımı veya geçersiz geometri nedeniyle başarısız olabilir; başarısız istek haritayı silmez.

- Sunucu: public demo OSRM, yoğun kullanım için garanti verilmez
- Harita altlığı: proje yapılandırmasındaki gerçek açık harita katmanları; ikinci karo altlığı eklenmez
- Rota süresi: trafik servisinin süresi doğrulanmadıkça trafiksiz olduğu açıkça belirtilir
- Rota başlangıç/varış: kullanıcı seçimi veya açık izinli GPS, **sabit İstanbul koordinatı yok**

## 2. Android Bluetooth ELM327 / WebView olay akışı

`Id4NavigatorActivity.java` mevcut `Id4LiveTelemetry` modülünden gelen ölçümleri, **halihazırda kayıtlı tek** JavaScript köprüsü ve `molaTelemetry` etkinliği üzerinden ön uca gönderir. Yeni `AndroidBridge` veya periyodik `getLiveOBDData()` köprüsü eklenmez. Araç ölçümlerini her 500 ms'de sorgulamak yerine geldikleri zaman işler; ham/kalibrasyonsuz PID'ler gerçek SoC/hız kabul edilmez.

### Verinin kaynağı
- **SoC, HV voltajı, batarya sıcaklığı, güç**: yalnızca geçerli, taze ve doğrulanmış OBD okumaları.
- **Hız, konum, rota ilerlemesi**: yalnızca son 30 saniyede alınmış, doğruluğu uygun gerçek GPS konumu.
- **Menzil ve enerji**: gerekli ölçüm yoksa `—`; varsa "model/tahmin" olarak etiketlenir.
- Rehberin `120 km/sa`, `%68`, `355 V`, `318 km` gibi rakamları **simülasyon örnekleridir, gerçek uygulamaya yazılmamıştır**.

Orijinal medya oturumlarına, DRM'ye, Bluetooth sistem eşleştirmesine veya Android Auto sürüş kısıtlarına müdahale edilmez.

## 3. Gerçek rota ilerlemesi ve hareketli araç simgesi

`navigator.html`, OSRM GeoJSON koordinatlarını yaklaşık 1.800 düğüme indirger; her gerçek GPS konumunu en yakın rota segmentine izdüşürüp kat edilen oranı bulur. Bu, basit `(toplamKm-kalanKm)/toplamKm` formülünün gerektirdiği gerçek kalan mesafeyi de sağlar.

- GPS taze (≤30 sn), doğruluğu ≤60 m ve rota yakınlığı kabul edilebilir ise ilerleme yüzdesi `0..100` aralığında hesaplanır.
- Alt bar doluluğu `0..100%`, **🚙 araç simgesi `0..92%` aralığında** tutulur; bar dışına taşmaz.
- GPS yoksa, eskimişse ya da rota dışında ise araba ikonu saklanır; "GPS rota ilerlemesi yok / konum rota dışında" gösterilir.
- Aynı gerçek `molaPosition` konum olayı tüm ilgili ekranları günceller. Örnek/sahte ilerleme tetiklemesi kullanılmaz.

## 4. Marka, gizlilik ve lisans sınırları

Uygulamanın **görünen adı** `Elektrikli Yol Arkadaşım`dır. Geriye dönük uyumluluk ve mevcut APK'nın aynı uygulama olarak güncellenebilmesi için teknik Java sınıf adları, bazı dosya yolları, JSON etkinlik adları ve paket kimlikleri henüz yeniden adlandırılmamıştır. Bunların bir anda değiştirilmesi mevcut kullanıcılarda veri kaybı veya derleme kırılması yaratabilir.

Üçüncü taraf açık kaynak atıfları (ör. [LICENSE](../LICENSE), [NOTICE](../NOTICE.md)) ve lisansın zorunlu isimleri korunur. Proje kullanım izinleri ve gerçek cihaz testleri tamamlanmadan üretim doğrulaması sayılmaz.
