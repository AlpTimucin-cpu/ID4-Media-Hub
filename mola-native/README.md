# Mola'nın İçinde ID.4 — Kaynak Kod Koruması ve Entegrasyon

## Kullanıcının isteği
Elindeki **Mola 3.2** uygulamasının TV/IPTV/YouTube/tarayıcı, Mola oynatma motoru
ve Android Auto işlemleri **kesinlikle bozulmadan** uygulama içine
**Türkiye haritası, EPDK istasyonlar, maliyet ve yol/enerji hesapları** eklemek.

## Mevcut kurulum güvenliği — çok önemli
- Kullanıcının verdiği Mola 3.2 APK SHA256:
  `2cf92adf8df595c97e8ac9b5c9e0d73f64e2e3ce8601756426773f14e50f3f47`
- İncelenen açık Mola kaynağı: https://github.com/moris545454/araba-,
  commit `3f45ad3557a25822c2a3a9f152f32ef82f57e005`,
  README / Gradle katalogunda sürüm **2.2.2**.
- **2.2.2 kaynaklarının, 3.2 APK'sının gerçek kaynak koduyla eşleştiği
  doğrulanmadı.** Geliştirme, gerçek 3.2 APK binary'sinin üzerinde değil,
  GPL-3.0 kaynaklı Mola 2.2.2 **ayrı test çatalında** yapılır.
- Test derlemesi, `APP_ID_SFX=.id4preview` ile farklı kimlik alır;
  **3.2 APK üzerine kurulmaz, 3.2'nin kullanıcı verilerini silmez**.
- Kullanıcının Mola 3.2 APK'sını yeniden imzalamıyoruz ve içinde özel
  şifre/oturum belirteci arayıp kopyalamıyoruz.
- Gerçek 3.2'nin kendi kaynak deposu ve yetkili imza anahtarı varsa
  aynı ekleme doğrudan o sürümün kaynağına taşınabilir. Yeni anahtar
  eski imzalı APK'yı üzerine yükseltemez.
- GPL-3.0 kaynak kullanıldığı için türetilen kod GPL yükümlülüklerine uygun
  kaynak/attribution ile dağıtılmalıdır.

## Mola'nın çalışma davranışını koruyan değişiklik
- GitHub Actions: `.github/workflows/build-mola-native-id4.yml`
- Kesin kaynak kontrolü ve değişiklikler:
  `mola-native/patch_mola.py`:
  1. Orijinal Mola'nın üç dosyasının blob hash'lerini kontrol eder.
  2. Ana dashboard XML'ine **tek bir ID.4 butonu** koyar.
  3. DashboardFragment onViewCreated'a **tek tıklama callback'i** ekler.
  4. Manifestte **exported=false** ayrı Activity tanımlar.
  5. `Id4EvActivity.java` ve lokal harita HTML'sini yeni dizine ekler.
  6. TV/radyo/podcast/YouTube/ExoPlayer/VLC/MediaProjection, araç servisleri,
     kaynak listeleri, ana uygulama kimliği, güncelleme akışı ve kullanıcı
     tercihleri gibi diğer kodlarda **sıfır değişiklik** hedefler.
  7. Beklenmeyen dosya farklılıklarında işlemi iptal eder.
- ID.4 ekranı ilk açılana kadar EPDK verisi/Leaflet yüklenmez.
- Auto projection ortamında henüz Android Auto navigasyon şablonu
  uyumluluğu doğrulanmadığı için ID.4 butonu **telefon Mola ekranında**
  görünür; araç ekranındaki Mola davranışı korunur.

## ID.4 ekranının doğrulanabilir ilk fonksiyonları

**Türkiye haritası**
- OpenStreetMap tile'ları + Leaflet ile uygulamanın içinde harita.
- Adres araması komoot Photon demo servisinden **manuel ve düğmeye basınca** (makul kullanım sınırları içinde).
- Haritaya dokununca hedef; uzun basınca başlangıç; OSRM açık demo
  servisiyle rota **önizlemesi**. Tam navigasyon motoru, otomatik reroute,
  sesli kılavuz ve canlı trafik mevcut değildir.
- Haritada 55'ten fazla işaret aynı anda çizilmez, harita açılışı
  ana Mola oynatıcısını yüklemeye veya durdurmaya çalışmaz.
- Photon, OSRM, OSM demo tile'ları yüksek hacimli üretim için
  ücretsiz sınırsız/garantili servis **değildir**. Büyümede lisanslı
  tile, geocoder ve routing backend gereklidir.

**Resmî şarj**
- EPDK resmî `HALKA_ACIK` istasyon snapshot'u:
  `data/charging/epdk_public_stations.json.gz`. Son doğrulanan
  kayıtta **13.118 halka açık**, **3.908 özel** kayıt. Tarihli ve
  koordinatlı kayıt; sayı zamanla değişebilir.
- Konum, operatör, marka, adres, CCS/Type2/CHAdeMO soket türü.
- EPDK normalize snapshot sınırlı SSL bağlantısıyla indirilir, gzip/JSON
  arka planda işlenir, boyut/güncellik kontrol edilir.
- Görünen alanda işaret sayısı sınırlıdır; tüm ülke bir defada WebView'a yüklenmez.
  İstasyon listesi 25'li sayfalamayı, AC/DC filtrelerini, isteğe bağlı GPS mesafe
  sıralamasını, Türkçe adres aramasını ve EPDK kaynağını yenilemeyi destekler.
- Canlı soket doluluğu ve kesin istasyona özgü fiyat **BU VERİDE YOK**.
  İzinli operatör/OCPI servislerinden sağlanana dek uydurulmaz.
- Trugo ve ZES resmî yayınlanan, güncellik etiketli **genel tarife**
  kaydı: `data/charging/operator_prices.json` (kesin soket fiyatı değil).

**Tesla-benzeri EV planlama (ID.4'ün sistemine müdahale yok)**
- Batarya kullanılabilir kapasitesini manuel girme.
- Yol tüketimi ve mesafesinden tahmini varış SoC.
- Varışta istenen rezerv ile ek şarj ihtiyacı.
- Seçilen şarj gücüne göre teorik şarj zamanı.
- Seçilen tarife üzerinden tahmini şarj maliyeti.
- Bu sonuçlar gerçek DC şarj eğrisini, mevsimi, hava durumunu,
  yokuşu veya batarya yönetim sistemini içermez.
- **Tesladaki araç kontrolü ve donanım özellikleri** (kapı/klima,
  batarya ön ısıtma, SoH/BMS verileri, uzaktan bağlantı, Sentry,
  Dog Mode, yazılım/OTA açma, Autopilot) yalnızca APK ekleyerek
  güvenli biçimde kazanılamaz; VW yetkili API/donanım erişimi gerekir.

## Derleme hedefi ve test durumu
GitHub Actions pinli GPL Mola kaynağını alır, güvenli ID.4 overlay'ini uygular,
mevcut medya kaynaklarının değişmediğini kontrol eder, otomatik testleri ve
`packageAutoDebugUniversalApk -PAPP_ID_SFX=.id4preview` paketlemeyi çalıştırır.
HTML arayüzünün otomatik tarayıcı önizlemeleri `docs/screenshots/` içindedir.
**Gerçek telefon veya Android Auto aracında test edildiği anlamına gelmez.**

## Kaynak ve kullanım politikaları
- https://operations.osmfoundation.org/policies/tiles/
- https://github.com/komoot/photon (kamu demo API'si makul kullanım; garanti yok)
- https://www.openstreetmap.org/copyright
- https://project-osrm.org/
- https://www.epdk.gov.tr/
- https://github.com/moris545454/araba-/blob/main/LICENSE


### OpenFreeMap vector basemap

The phone cockpit uses OpenFreeMap (no API key) through bundled MapLibre GL JS 4.7.1
and maplibre-gl-leaflet 0.1.3. Dark/light vector styles replace the former
OSM Standard raster tiles and CSS inversion. OpenFreeMap covers Turkey as part
of its global OpenMapTiles/OSM data; visible attribution remains required.
Traffic still requires a separate live data provider; an open-source map is not
a live congestion feed. A traffic key no longer changes the selected basemap.
The packaged Natural Earth fallback is explicitly only country geography.

Upstream libraries: https://github.com/maplibre/maplibre-gl-js (BSD-3-Clause),
https://github.com/maplibre/maplibre-gl-leaflet (ISC); notices are packaged with assets.
Provider: https://openfreemap.org/quick_start/ .

## Word canlı harita paketi — entegrasyon durumu (25.10.2026)

Kaynak: `ID4_Yol_Arkadasim_Teknik_Dokuman_Kodlar_Dahil.docx`.
Ana motor `overlay/fermata/src/main/assets/id4/id4-live-map.js`, adaptör
`id4-navigator-adapter.js`; `navigator.html` mevcut Mola Leaflet haritasına
`ID4_LIVE_ATTACH_ONLY` ile bağlanır. Tesla kokpit düzenini korumak için
`id4-live-addon.css` kullanılır; Word içindeki bağımsız `standalone-index.html`
mevcut kokpitin yerini almaz.

- Mola `molaRoute` → gerçek geometri, rota orta noktası hava modeli ve enerji paneli.
- Mola `molaPosition` ve `molaTelemetry` → yalnız taze, doğrulanan GPS/SoC.
- Mola `molaStations` (`kind: route`) → gerçek EPDK rota kayıtlarının sayısı,
  orijinal Mola şarj işaretçilerinden bağımsız, ikinci harita oluşturmadan aktarılır.
- Rota koridoru listesi **Türkiye geneli tam DC listesi değildir**: küresel
  en yakın DC bilgisi ancak ayrıca eksiksiz veri `setStations(...)` ile
  verilirse sunulur. Fiyat, anlık doluluk veya yol sapması bu JSON'dan
  doğrulanmış sayılmaz.
- Open-Meteo model tahminidir, fiziksel canlı sensör değildir.
- Yerel kazalar/yol kapanmaları Firebase bağlanmadıkça paylaşılmaz;
  doğrulanmış trafik yoğunluğu olarak sunulmaz.

Tam ülke DC kaynağı için `setStations(stations, metadata)` sözleşmesi:
```javascript
window.ID4LiveMap.setStations(actualVerifiedEpdkStations, {
  source: 'EPDK', coverage: 'country', complete: true,
  geocodedCount: actualVerifiedEpdkStations.length,
  verifiedAtUtc: actualEpdkSnapshotVerifiedUtc
});
```
Bu iki `actual...` değeri üretimde gerçek EPDK içe aktarıcısından alınmalıdır.
Yalnızca tam/kayıt sayısı tutarlı/en az 5000 tekil kayıt içeren ve 72 saatten yeni
veriyle ülke çapında en yakın DC işaretleme etkinleşir. Bu, kapsam
kontrolüdür; EPDK kökeninin kriptografik doğrulaması değildir.
Kısmi veya eski veriler en yakın DC olarak işaretlenmez.
Düşük SoC işaretleme için %20 altı gerçek SoC, 30 saniyeden yeni GPS ve
30 saniyeden yeni telemetri birlikte zorunludur. Open-Meteo zaman damgası
90 dakikadan eskiyse model tahmini reddedilir. Ölçülmüş kapasite ve tüketim
profili yoksa varış yüzdesi hesaplanmaz.
Doğrulama: `node mola-native/tests/test_live_map.js` doğrudan paketlenecek JS'i
kullanır; aynı kontrol `.github/workflows/build-mola-native-id4.yml` içinde
APK derlemesinden önce çalışır. Gerçek ID.4, POCO F5, OBD ve Android Auto
testleri ayrı kabul kapısıdır; bu kaynak değişikliği bir APK üretildiğini
veya araçta doğrulandığını tek başına kanıtlamaz.
