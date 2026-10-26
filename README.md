# ID.4 Yol Arkadaşım

**Volkswagen ID.4 Pure 2024 için sade bir EV kokpiti, Türkiye şarj istasyonları, rota planlama ve salt-okunur OBD tanılama önizlemesi.**

[![APK Build](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/actions/workflows/build-mola-native-id4.yml/badge.svg?branch=feature%2Fmola-native-id4)](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/actions/workflows/build-mola-native-id4.yml)
[![UI Screenshots](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/actions/workflows/render-id4-readme.yml/badge.svg?branch=feature%2Fmola-native-id4)](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/actions/workflows/render-id4-readme.yml)

> **Geliştirme sürümü.** Bu proje Volkswagen, Google veya Mola tarafından onaylanmış değildir. Android Auto araç ekranında ID.4 kokpiti henüz doğrulanmadı. Mevcut **Mola 3.2 üzerine kurulmaz**; açık kaynak Mola 2.2.2 tabanından **farklı paket kimliğiyle** derlenir.

## Gerçek arayüz kodundan ekran önizlemeleri

![Yatay araç kokpiti — navigator.html HTML arayüzünden tarayıcı önizlemesi](docs/screenshots/cockpit-browser-preview.png)

| Telefon görünümü | Kompakt yatay görünüm |
| :---: | :---: |
| ![Telefon navigator.html ekran önizlemesi](docs/screenshots/route-phone-browser-preview.png) | ![Kompakt araç navigator.html ekran önizlemesi](docs/screenshots/cockpit-compact-browser-preview.png) |

**Görselin kaynağı:** Bunlar uygulamanın kendi `navigator.html` DOM/CSS kodundan otomatik alınan **çevrimdışı tarayıcı renderlarıdır**. Harita karosu, konum, araç telemetrisi veya rota **uydurulmaz**. Android telefon/ID.4 araç ekranında çekilmiş gerçek cihaz fotoğrafı değildir. Görseller [otomatik ekran önizleme iş akışı](.github/workflows/render-id4-readme.yml) ile kaynak değişince yeniden üretilir.

## Neler çalışıyor, neler sınırlandırılmış?

| Bölüm | Bu dalda bulunanlar | Açıkça belirtilen sınır |
|---|---|---|
| **Kokpit** | Tam ekran yatay, rota yoksa gereksiz bilgi kutularını gizleme, panel ayrıntısı açma | Telefon ve araçta ekran görüntüsü testi gerekir |
| **Rota** | Nereden/nereye araması, OSRM alternatifleri, model enerji ve mola hesapları, rota çizimi | OSRM demo ETA canlı trafiği içermez; kritik navigasyon değildir |
| **Şarj** | EPDK halka açık istasyon listesi, AC Type 2 / DC CCS filtreleri, adres/operatör arama, konuma göre mesafe, 25'li sayfalama, açıkça kaynak yenileme | Kaydedilen EPDK snapshot tarihi gösterilir; gerçek soket doluluğu yok |
| **Canlı doluluk** | Resmî [Şarj@TR](https://play.google.com/store/apps/details?id=tr.gov.epdk.sarjetTR) uygulamasını açma | Yol Arkadaşım canlı doluluğu doğrudan okumuyor |
| **Fiyatlar** | Tarihli genel Trugo/ZES ağ tarifeleri ve resmî bağlantıları | İstasyona özgü fiyat, tüm ağlar ve indirimler henüz yok; eski değer işaretlenir |
| **Hava** | Open-Meteo model tahminleri, güzergâh boyunca saatlik kontroller | Model verisidir, araç sensörü değildir |
| **Trafik** | TomTom Orbis Flow raster tile katmanı (kullanıcıya özel anahtar), ayrı olay bildirimi sorguları | Gerçek TomTom tileload başarısı olmadan canlı ibaresi yok; OSRM ETA trafiksiz |
| **OBD** | Eşleşmiş Bluetooth ELM327; salt-okunur MEB DID kontrolü, sensör yaşı, detay görünümü | Car Scanner/VIN üzerinde doğrulanmamış sayılar özellikle gizlenir |
| **Mola medya** | Ayrı ID.4 Activity; ana oynatma kaynaklarına değişiklik yapılmıyor | Mola 3.2 kaynak eşdeğerliği ve tüm oynatıcı işlevleri cihazda doğrulanmalı |

## APK yükleme

1. [**ID.4 preview Actions sonuçlarını aç**](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/actions/workflows/build-mola-native-id4.yml?query=branch%3Afeature%2Fmola-native-id4). Yalnızca **son başarılı** derlemeyi seç.
2. **Artifacts → `Mola-OpenSource-ID4-Preview-Not-3.2-APK`** ZIP dosyasını indir ve `Mola-ID4-Preview.apk` dosyasını çıkar.
3. Android telefona yükle; uygulamanın **ID.4 Araç Merkezi** bölümünü aç.
4. Konum ve Bluetooth izinlerini yalnız ilgili özellik gerektiğinde ver.

**Yükseltme uyarısı:** Actions debug APK'ları farklı imzalar taşıyabilir. Önceki önizleme sürümünün üzerine kurulamazsa kullanıcı verilerini yedeklemeden kaldırma. Mevcut Mola 3.2 uygulaması ayrı kalır.

## Güvenlik ve veri güvenilirliği

- **Gerçek araç ölçümü yoksa gösterge `—` kalır.** Bağlanan ELM327 adaptörü tek başına kalibre edilmiş MEB PID verisi anlamına gelmez.
- **Aynı anda Car Scanner + Yol Arkadaşım aynı Bluetooth ELM327 soketine bağlanmaz.** Kayıtlar ayrı oturumlarda karşılaştırılır.
- EPDK istasyon listesi konum/adres/soket türünü taşır; **canlı müsaitlik, kesin soket fiyatı veya kampanya** içerdiği iddia edilmez. Resmî EPDK [Şarj@TR](https://www.epdk.gov.tr/Detay/Icerik/1-3428/serbest-erisim-platformu--sarj%40tr) bunları kullanıcıya gösterse de uygulama içinde yetkili veri entegrasyonu olmadan taklit edilmez.
- Trafik, hava ve yol enerjisi aynı şey değildir. İndirilen hava modeli ve hesaplanan şarj maliyeti **tahmin** olarak ayrılır.
- Android Auto'da telefon WebView kokpitinin çalıştığı **iddia edilmez**. Sürüş sırasında video kısıtlamaları aşılmaz.

## Teknik yapı

**Aktif geliştirme dalı:** [`feature/mola-native-id4`](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/tree/feature/mola-native-id4)

- [Mola entegrasyon ve lisans notları](mola-native/README.md)
- [Kokpit HTML/CSS](mola-native/overlay/fermata/src/main/assets/id4/navigator.html)
- [Android EV Activity kaynakları](mola-native/overlay/fermata/src/main/java/me/aap/fermata/ev/)
- [GitHub Actions APK derlemesi](.github/workflows/build-mola-native-id4.yml)
- [Kaynak HTML'den ekran görüntüsü üretimi](docs/scripts/render_id4_readme.py)
- [Kaynak/telafi sınırları: trafik SDK](mola-native/TRAFFIC_PROVIDERS.md)

**Temel teknolojiler:** Android Java, WebView, Leaflet/OpenStreetMap, OSRM, Photon, Open-Meteo, EPDK istasyon kaydı, Bluetooth ELM327. Mola'nın GPL kaynakları pinli açık kaynak commit'ten türetilir; kullanıcının imzalı Mola 3.2 APK'sı değiştirilmez.

## Yol haritası

**Öncelikler:** Gerçek POCO F5 ekran ölçüleriyle okunabilirlik, istasyonların resmi kaynaklarla güncellenmesi, güvenilir izinli soket doluluk/fiyat API'si, Car Scanner karşılaştırmalı sensör kalibrasyonu, Android Auto için ayrı uyumlu navigasyon uygulaması ve donanımda test. Kod derlemesinin başarılı olması bu başlıkların çözüldüğü anlamına gelmez.

**Katkı:** Hata raporlarında ekran boyutu, uygulama sürümü/commit, tekrarlama adımları ve kişisel bilgileri çıkartılmış loglar paylaşılabilir. OBD loglarındaki VIN/MAC ve lokasyonlar gizlenmelidir.

### TomTom Orbis v2 trafik katmanı
Resmî yoğunluk ve yol olayları raster katmanı `Katmanlar → Trafik → TomTom Orbis Trafik` seçeneğiyle eklendi. Gerçek veri için `ID4_TOMTOM_API_KEY` Actions secret gerekir; bu anahtar olmadan canlı trafik etkin değildir. Mevcut OSRM ETA hâlâ trafiksizdir. [Kurulum ve sınırlar](mola-native/TRAFFIC_PROVIDERS.md).

## Yeni uygulama kimliği ve doğrudan kokpit açılışı
Telefon launcher adı **ID.4 Yol Arkadaşım**. Özel koyu lacivert, ID.4/elektrikli araç temalı vektör simgesi vardır. Uygulamaya dokununca önce yatay araç kokpiti açılır. Ekrandaki **Mola** düğmesi medya ana ekranına döner; mevcut medya oynatma sınıfları değiştirilmedi. Bu ayrı `.id4preview` paketidir; yüklü Mola 3.2'yi güncellemez. Android Auto navigasyon ekranı yine doğrulanmış değildir.

**OSRM alternatif arama stratejisi:** Resmî OSRM `alternatives=6` seçeneğiyle birincil yola ek olarak en fazla altı farklı güzergâh araştırılır. Gerçekten bulunan bütün yollar (koruyucu üst sınır 12) renkli çizgilerle aynı haritada gösterilir. OSRM daha az yol döndürürse uygulama bunu dürüstçe gösterir; her iki nokta arasında sınırsız sayıda rota garantisi verilemez. <https://project-osrm.org/docs/v26.6.1/http>

### Canlı trafik sadeleştirmesi — 24 Ekim 2026
Google/Yandex canlı trafik seçenekleri kaldırıldı; bu iki servis için çalışan
canlı sağlayıcı erişimi doğrulanmamıştı. Android trafik katmanı yalnız
**TomTom Orbis** ve **OpenStreetMap** ile çalışır. Gerçek TomTom akış karosu
başarılı olmadıkça canlı etiket görünmez. Trafik hesaplayan Orbis v3 rota
bağlantısı API anahtarı varsa ayrı şekilde denenir, başarısızsa OSRM kullanılır.

### Rota sağlayıcıları değerlendirmesi (24 Ekim 2026)
2016 iGISMAP rota API karşılaştırması güncel resmî belgelerle yeniden ele
alındı. Hedef: TomTom Orbis v3 canlı trafik ve gerçek alternatifler, OSRM
trafiksiz yedek. GraphHopper/ HERE ancak geçerli API yetkisi ve cihaz
testiyle değerlendirilecek. [Ayrıntılı değerlendirme](mola-native/TRAFFIC_PROVIDERS.md).

### Yeni: İzinli GPS yolculuk günlüğü

Silent But Fast'teki yolculuk takibi yaklaşımından esinlenen bağımsız modül.
Kokpitte **▣ Yolculuklar** düğmesiyle gerçek GPS sürüşü açıkça başlatılır.
Ön planda, hatalı/eski konumları dışlayarak km/süre toplanır; koordinatlar
saklanmaz. Kayıt için en az 30 saniye, 200 metre ve iki doğru GPS örneği
gereklidir. Son 30 özet telefona özel depoda tutulur; tümünü temizleyebilirsin.
Ölçülmemiş gerçek enerji veya maliyet uydurulmaz. Android Auto deneyimi bu
modülden ayrı olup halen doğrulanmamıştır.

### OSM tabanlı yol güvenliği katmanı

Kokpitteki **📷 Yol güvenliği** düğmesi ve harita katmanı, kullanıcı istediğinde açık OSM `highway=speed_camera` noktalarını gösterir. Yalnız yakınlaştırılmış alan, en fazla 120 düğüm ve 90 sn istek aralığı desteklenir. GPS hız göstergesi yalnız yeni ve doğruluğu iyi bir `Location.hasSpeed` ölçümünde görünür. En yakın OSM kamerasına 450 m'de olasılıksal görsel uyarı, ayrıca etkinleştirilirse 350 m'de TTS uyarısı verilir.

Kaynak: https://wiki.openstreetmap.org/wiki/Tag:highway=speed_camera — Atıf: © OpenStreetMap contributors (ODbL).

**Sınırlar:** Kamera verisi eksik veya eski olabilir. Mobil radar, kırmızı ışık ve ortalama hız uyarıları ile geçerli yasal hız limiti yoktur; OSM kamera etiketindeki `maxspeed` yol üzerindeki geçerli sınır olarak yorumlanmaz. Bu özelliğin kullanımına ilişkin yerel mevzuatı kontrol etmek kullanıcının sorumluluğundadır. Sürüş sırasında ekrana dokunma önerilmez.

### Referans kokpit v3 — 24 Ekim 2026

Kullanıcının paylaştığı **1536 × 864** kokpit görseline göre gerçek
`assets/id4/navigator.html` görünümü yeniden düzenlendi. Önceki çoklu
stil katmanları altında kaybolan araç ekranı artık doğru sırayla görünür:

- Üstte **VOLKSWAGEN ID.4 PURE / ID.4 Yol Arkadaşım – Rota – İstasyon** menüsü,
  cihazın gerçek yerel saati, doğrulanmış TomTom durum göstergesi ve
  yalnız Open-Meteo yanıtı olduğunda sıcaklık
- Solda büyük batarya, menzil ve 2 × 3 araç veri kartı;
  **OBD doğrulanmadığında gösterge rakamları boş**
- Sağda gerçek manevra/kalan mesafe/süre/varış/şarj bilgi kartı;
  **rota kurulmadan gizlenmez**, 'Rota bekleniyor' der
- Altta gerçek GPS ilerlemesine bağlanan rota çubuğu;
  rota kurulmadan gizlenmez, uydurma hareket/SoC göstermez
- Altta Mola, uygulamalar, müzik, telefon ve klima için güvenli kontrol
  etiketleri, rota, şarj, TomTom, OBD, radar, geçmiş, katmanlar.
  **Telefon, iklimlendirme ve koltuk ısıtma kontrolü bağlı olmadığı için
  bilinçli olarak devre dışı; 22°C gibi sahte değerler yok.**
- Seçim yokken kokpit, tam ekran Leaflet haritası üzerindeki durum
  göstergeleriyle beraber kalır. Gerçek ağla OSM karo yüklenir, TomTom
  ve OSRM/TomTom alternatifleri gerçek API verisi dönerse işlenir.

1536 × 864 çözünürlüğünde Chromium offline DOM/render kontrolü tamamlandı.
Başarılı sayfa renderi, uygulama içi Android Auto/OBD/TomTom gerçek cihaz
testi anlamına gelmez. İçerik Google/Yandex canlı trafik sağlayıcılarını
tekrar eklemez; TomTom kullanıcı anahtarı GitHub'a yazılmaz.

## 24 Ekim — haritanın boş kalmasına karşı bağımsız coğrafya katmanı

Tek OSM karo sunucusu cevap vermediğinde arka planda düz lacivert görünmesi
engellendi. Uygulama yalnız kullanıcının görüntülediği harita karolarını önce
OSM Foundation, sonra OSM France üzerinden açmayı dener. Her iki kaynak
erişilemezse uygulamayla birlikte dağıtılan **Natural Earth 110m kamu malı
ülke sınırları** ve Türkiye il merkezleri (MIT) görünür kalır.
Kaydırma, yakınlaştırma ve hedef seçimi çalışır; **bu coğrafi görünüm sokak
haritası, çevrimdışı yol ağı veya trafik bilgisi değildir**. Durum ekranında
hangi kaynak açıldığı belirtilir ve kullanıcı manuel **↻ Yeniden dene**
yapabilir. OpenStreetMap kaynaklarını toplu indirme/önbellek doldurma yoktur.
Uygulama alt menüsündeki çalışmayan tüm klima, set sıcaklığı ve koltuk
ısıtma düğmeleri HTML ve CSS'den silindi.

Natural Earth lisansı: https://www.naturalearthdata.com/about/terms-of-use/
OSM Foundation karo politikası: https://operations.osmfoundation.org/policies/tiles/
OSM France kullanım şartları: https://www.openstreetmap.fr/usage/

### TomTom Orbis v2 sokak haritası
Aynı özel kullanıcı TomTom anahtarı, harita servisinin yetkisi varsa Orbis Raster Map Display v2 için de kullanılabilir. Koyu ve açık sokak haritası stilleri desteklenir. Harita yetkisi veya bağlantı başarısızsa OSMF, OSM Fransa ve kamu malı coğrafi görünüm sırayla devreye girer. Gerçek tileload olmadan canlı harita var iddiası gösterilmez. TomTom istekleri kota tüketebileceğinden geliştirici hesabında sınırlama gerekir. Kaynak: https://docs.tomtom.com/map-display-api/documentation/tomtom-orbis-maps/v2/raster/raster-tile

### Harita V3 kritik paketleme düzeltmesi

V3 sürümünde HTML iki harita dosyasını yüklemeyi beklerken build scripti
bunları yalnız kaynakta *doğruluyor*, derleme klasörüne *kopyalamıyordu*.
Bundan dolayı başarılı APK derlemesinde bile **id4-basemap.js** ve
**id4-offline-geography.js** bulunmuyordu. Düzeltildi: her ikisi overlay
kopya listesine alındı ve build sırasında **nihai APK ZIP girdileri**
yoklanıyor. Eksik olursa GitHub Actions başarısız sayılacak.

Karo indirme başarısız olursa çevrimdışı Natural Earth ülke sınırları ve
şehir işaretleri açıkça belirtilerek gösterilir; bu sokak navigasyonu
değildir. Gerçek TomTom/OSM karo yükleme başarısı yalnız olay geldiğinde
etiketlenir. Eski trafik görünümü CSS'sinin gerçek Leaflet haritasını
gizlemesi de engellendi. Desteklenmeyen klima, ısıtma ve telefon
düğmeleri kokpit ana dock'undan kaldırıldı.

### TomTom anahtarının kalıcı ve şifreli saklanması

ID.4 Yol Arkadaşım'a **kullanıcının kendi TomTom anahtarını bir kez kaydetmesi** yeterlidir. Anahtar, Android Keystore ile korunan **AES-256-GCM** şifrelemesiyle uygulamaya özel depoda tutulur; aynı uygulama imzasıyla yapılan normal güncellemede korunur. Kullanıcı anahtarı Ayarlar'dan değiştirebilir veya silebilir; uygulama kaldırıldığında ya da Keystore erişimi kaybolduğunda yeniden istenir.

Daha önceki `id4_traffic_keys` düz metin kayıtları, bir sonraki anahtar kullanımında otomatik olarak şifrelenmiş depoya aktarılıp temizlenir. Şifreli kayıt başarısız olursa trafik anahtarı kullanılmaz. **APK'da sabit TomTom anahtarı bulunmaz.** Ücretsiz kota, kullanım koşulları ve olası maliyet kullanıcıya ait TomTom hesabında yönetilir.

JavaScript'e herkese açık anahtar okuma köprüsü eklenmedi; mevcut yerel Android anahtar penceresi kullanılır. TomTom'un raster trafik karoları için URL parametresinde anahtar taşınması gereği, çalışan sayfada/ağ isteklerinde anahtarın görünür olabileceği anlamına gelir. Anahtarı herkese açık log veya ekran görüntülerinde paylaşmayın.

## Lisans, atıf ve API anahtarı güvenliği

Bu depodaki **Mola/Fermata 2.2.2 kaynak tabanından üretilen ID.4 Yol Arkadaşım önizleme APK'sı**, pinlenmiş açık kaynak projenin [GNU GPL-3.0](LICENSE) lisansına tabi türev çalışmadır. Dağıtılan sürümün karşılık gelen kaynak kodu ve değişiklikleri erişilebilir tutulmalıdır. Depodaki bağımsız önceki bileşenlerin ve üçüncü taraf kütüphanelerin kendi telif/lisans koşulları ayrıca geçerlidir.

Harita, hava, medya, adres ve diğer üçüncü taraf bağımlılıkları için [NOTICE.md](NOTICE.md) ve [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) dosyalarına bak.

**TomTom anahtarı APK'ya gömülmez.** Her kullanıcı kendi anahtarını ID.4 Yol Arkadaşım uygulamasında **Harita servis anahtarı** bölümünden girer. Önceki derlemelerde anahtarın varlıklara yazılabilme riski, yapılacak yenileme adımları ve yeni APK doğrulama kapısı [SECURITY_TOMTOM.md](SECURITY_TOMTOM.md) belgesinde açıklanmıştır.

Bu önizleme sürümünde GitHub derleme/test başarısı, POCO F5, Volkswagen ID.4, Android Auto veya canlı TomTom veri erişimi üzerinde fiziksel çalışmanın doğrulandığı anlamına gelmez.
