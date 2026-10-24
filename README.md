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
| **Trafik** | Resmî Google/Yandex SDK'larına hazırlık, anahtarsız durumda OSM | Geçerli anahtar/lisans olmadan canlı trafik yok, ETA'ya eklenmez |
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
