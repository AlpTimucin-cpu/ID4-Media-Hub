# ID.4 Yol Arkadaşım — GitHub proje incelemesi (22.10.2026)

Bu belge, Mola 2.2.2 GPL kaynak çatalı üzerindeki **ayrı** ID.4 preview geliştirmesinin teknik kararlarını açıklar. Kullanıcının yüklü Mola 3.2 APK'sı / imzası değiştirilmez.

## İncelenen kaynaklar

| Proje | Kullanılabilecek yaklaşım | Doğrudan kopyalanmayan / kısıt |
|---|---|---|
| [VW-Dash](https://github.com/Mo3he/VW-Dash) (MIT) | Tek bakışta araç durumu; ölçüm zaman damgası; SoC, sürüş/şarj kayıtlarını ayrı kaynaklarla ilişkilendirme | Arka uç WeConnect erişimi Avrupa hesabına bağlı. Türkiye VIN/servisleri için otomatik çalıştığı varsayılmaz; uzaktan komut gönderilmez |
| [IDDataLogger](https://github.com/robske110/IDDataLogger) (GPL-3.0) | Şarj geçmişi; kaynak/ölçüm zaman damgaları; ABRP'ye veri aktarma modeli | PHP + sunucu / VW bağlantısı gerektirir; telefondaki ELM327 yerine geçmez |
| [IDTelemetry](https://github.com/TheInGoF/IDTelemetry) | MEB/UDS telemetri örnekleri; ayrı sensör güncelliği, veri güvenilirliği, kopma sonrası dayanıklılık | Proje ESP32 ve harici CAN donanımı içindir. Ek donanım istemeyen telefon uygulamasına firmware/sensör formülleri doğrudan taşınmaz |
| [Optimization_EVCRP](https://github.com/TAtaTrkylmz/Optimization_EVCRP) | Türkiye EPDK verileri, şarj ihtiyacı + güzergâh optimizasyonu, batarya rezervi ve modelin açık varsayımları | TomTom API anahtarı ve sunucu tabanlı optimizasyonu gerektirir. Demo algoritmasının hatasız canlı navigasyon olduğu varsayılmaz |
| [evnav](https://github.com/giraldeau/evnav) (MIT) | Enerji kısıtlı rota ağında durak seçimi | Basit Dijkstra örneği; hava, yükseklik, sürüş alışkanlığı yok |

Kaynaklara ait içerik, veriler ve lisanslar aynen ilgili projelere aittir. Buradaki geliştirmeler tasarım fikirlerinden esinlenmiştir; başka projelerden kaynak kodu topluca kopyalanmamıştır.

## Uygulanmış değişiklikler

1. **Bağlantı güvenilirliği:** Her ELM327/MEB sensörü ayrı `*At` zaman damgası ile güncellik denetiminden geçer. Örneğin yeni HV voltajı eski SoC'nin ekranda canlı görünmesini sağlamaz. Ön izleme 25 saniye eşiğini kullanır.
2. **Haritada araç HUD:** SoC, HV paket voltajı ve batarya sıcaklığı yalnızca ilgili ECU yanıtı güncelse görünür. OBD eşleştirme ilk sefer açık kullanıcı seçimiyle yapılır.
3. **Otomatik rota enerjisi:** OSRM geometrisi ve mesafesi geldikten sonra ID.4 Pure için açıkça etiketli model aralığı gösterilir (16–24 kWh/100 km; kullanılabilir kapasite varsayımı 52 kWh). Geçerli son SoC varsa model varış SoC aralığı eklenir. Yetersiz enerji için şarj uyarısı gösterilir; negatif SoC "0%" diye gösterilmez.
4. **Navigasyon ve medya ayrımı:** Mola TV/radyo/oynatıcı sınıfları korunur. ID.4 haritası telefon ekranı önizlemesidir. Aracın Android Auto host onayı ve sesli yol tarifi doğrulanmış değildir.

## Kaynak doğruluğu ve yapılmayanlar

- Canlı trafik, kaza ve yol çalışması için yetkili feed henüz **yoktur**; OSRM demo rotası trafik verisi sayılmaz.
- EPDK istasyon snapshot'u **canlı doluluk** ve **soket bazlı kesin fiyat** vermez.
- Son SoC gözlemi araçtan anlık canlı veri anlamına gelmez; rota tahmininde kaynak zaman damgası ile ele alınır.
- Model tahmini, BMS'nin anlık tüketim, akım, SoH veya gerçek kalan menzil değeri değildir.
- Türkiye WeConnect hesapları için çalışan uzaktan komut / klima kontrolü iddia edilmez.
- Sürüş esnasında önde video gösterme kısıtlamaları aşılmaz.

## Sonraki teknik aşama

1. GitHub Actions APK derlemesinin **başarılı** olması ve telefon üzerindeki ilk test.
2. ELM327'de VIN uyumlu DID yanıtlarının gerçek Car Scanner loglarıyla kıyaslanması; ECU'nun gerçekten verdiği değerler dışında formül kullanmama.
3. Rota ekranında EPDK konumlarının ölçeklenebilir işaretçi kümeleriyle birleştirilmesi (harita alanına göre sorgu, günlük snapshot doğruluğu).
4. Operatör izinli/OCPI kaynaklarından canlı durum ve fiyat bağlantıları. Resmî fiyatın zaman damgası olmadan toplam maliyet üretmeme.
5. Modelin tüketim aralığını yeterli *doğrulanmış* yerel sürüş geçmişiyle daraltma; gereksiz manuel giriş eklememe.
6. Uzun rotalarda enerji/rezerv/şarj noktası optimizasyonu; demo rota servisini üretim garantisi gibi kullanmama.

Her geliştirme, cihazda doğrulanana kadar **deneysel** kabul edilir.


## 22.10.2026 — yeni kaynak kontrolü ve mimari kararlar

**Android Auto – resmî destek, ayrı host ekranı:** Android for Cars App Library
(`androidx.car.app`) navigasyon uygulamalarında `CarAppService`,
`androidx.car.app.category.NAVIGATION`, `NavigationTemplate` ve
`MapWithContentTemplate` kullanır. Harita `SurfaceCallback` üzerinden
render edilir; Android telefondaki normal `Activity` içindeki `WebView`
ve özel HTML HUD, Android Auto araç ekranında kendiliğinden gösterilemez.
`Id4NavigatorActivity` şu an **yalnızca telefon kokpiti** olarak
kabul edilmeli. Mola medya oynatıcı tabanı Android Auto harita/navigation
servisine dönüştürülmeden veya ayrı bir uygun kategorili Android Auto
modülü eklenmeden araç ekranı vaadi yapılamaz. Sürüş sırasında HTML
medya/video yüzeyi gösterilmesi hedef değildir.

Kaynaklar:
- https://developer.android.com/training/cars/apps/navigation
- https://developer.android.com/training/cars/apps/library/draw-maps
- https://developer.android.com/training/cars/apps/library/interact-map

**OSM erişim politikası:** `tile.openstreetmap.org` yardım amaçlı
best-effort standart karo sunucusudur, SLA/çevrimdışı toplu indirme
vermiyor. Kaynak gösterimi HUD tarafından kapanmamalıdır; doğru HTTPS
URL, uygulamayı tanımlayan User-Agent ve HTTP önbellek kurallarına
uyulmalıdır. Android WebView için özel uygulama UA eki ve
`WebSettings.LOAD_DEFAULT` ayarı, Leaflet haritasına ayrıca sürekli
görünen © OpenStreetMap contributors etiketi eklendi.
Bu önlem üretim seviyesinde garantili tile servisi sunmaz.
Kaynak: https://operations.osmfoundation.org/policies/tiles/

**ID.4 telemetri kaynakları:** `Mo3he/VW-Dash` live WebSocket ve
WeConnect hesabıyla çalışır; projenin kendi README'si Avrupa dışı
hesaplar için sınırlama bildirir. `robske110/IDDataLogger` geçmiş
şarj ve ABRP entegrasyon yaklaşımı için örnektir; bunlar ELM327
Bluetooth üzerinden doğrulanmış batarya PID verileriyle aynı değildir.
Dış hesap/kullanıcı adı/şifre istemeden, Türkiye'de kesin çalışacak
uzaktan telemetri varsayımı yapılmayacak.
Kaynaklar:
- https://github.com/Mo3he/VW-Dash
- https://github.com/robske110/IDDataLogger

**EPDK resmî veri:** Kurumun enerji dönüşümü SSS'si kamuya açık
istasyon/işletmeci web servisleri olduğunu bildirir, ayrıca özel
API erişimi için talep gerekmeyebileceğini belirtir.
Uygulamamız henüz bunun canlı ve belgelenmiş uç noktasını
kullanmıyor; yalnızca doğrulanma tarihi belirtilen
`epdk_public_stations.json.gz` snapshot'unu kullanır.
Canlı soket doluluğu ve kWh bazlı kesin ücret gösterilmez.
Kaynak: https://www.epdk.gov.tr/Detay/Icerik/12-1040/enerji-donusumu

**Gerçekleşen dayanıklılık geliştirmeleri:** Model varış SoC bandı,
*mevcut* BMS SoC ve hatasız, güncel GPS konumuyla rotadaki yaklaşık
kalan mesafeyi birleştirir. Şarj miktarı bilinmiyorsa sonuç
boş bırakılır. Eski istasyon istekleri yeni rotayı örtmez.
Bu hesap tüketim/yükselti/sıcaklık detaylarını ölçen bir BMS
navigasyon motoru **değildir**.

### Teknik öncelikler

1. Son kod için GitHub CI'nin yeşil olması ve gerçek telefonda
   16:9/19.5:9 ölçülerde ekran görüntüsü kontrolü.
2. Gerçek ID.4 için Car Scanner loglarıyla MEB DID doğrulaması
   (karıştırılmış/yanlış ECU değerlerinden kaçınma).
3. Bağımsız, Google kategorisi/izinlerine uygun Android Auto
   navigasyon servis prototipi; harita çizimi + rota + şarj noktaları,
   önce DHU/uyumlu host testleri.
4. EPDK belgeli XML/web servis şemasını yetkili ve kaynak uyumlu
   yöntemle alma; operatör bazlı canlı CCS doluluğu ayrı sözleşme/izin.
5. Türkiye'de üretim dağıtımı için SLA'lı harita/routing ve
   yasal/teknik tile önbellekleme mimarisi.
