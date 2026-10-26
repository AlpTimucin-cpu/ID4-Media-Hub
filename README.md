# Elektrikli Yol Arkadaşım

**Elektrikli araçlar için rota, şarj planlama, trafik, gerçek araç verileri ve medya deneyimi.**

> **Geliştirme / test sürümü.** Bağımsız bir açık kaynak uygulama çalışmasıdır; herhangi bir araç üreticisinin resmî ürünü değildir. Fiziksel araç ekranında tüm özelliklerin çalıştığı henüz doğrulanmamıştır.

## Kokpit görünümü

![Elektrikli Yol Arkadaşım — gerçek uygulama HTML/CSS kaynaklarından elde edilen kokpit önizlemesi](docs/screenshots/cockpit-browser-preview.png)

| Telefon görünümü | Kompakt yatay görünüm |
| :---: | :---: |
| ![Telefon kokpiti](docs/screenshots/route-phone-browser-preview.png) | ![Yatay kokpit](docs/screenshots/cockpit-compact-browser-preview.png) |

Bu ekran görüntüleri gerçek uygulama arayüzünden **çevrimdışı tarayıcı testi** ile üretilir. Canlı yol, GPS, OBD ve trafik verisi değildir; gerçek araç içi kullanım kanıtı sayılmaz.

## Özellikler ve doğrulama sınırları

| Modül | Var olan altyapı | Önemli sınır |
|---|---|---|
| **Rota ve harita** | Tek Leaflet haritası, OSRM yol geometrisi ve gerçek alternatif rotalar | Genel OSRM tahmini sürüş süresi canlı trafik süresi değildir |
| **Canlı trafik** | Kullanıcının kendi anahtarıyla renkli trafik katmanı ve olay sorguları | Servis çağrısı/gerçek karo yüklemesi başarıyla doğrulanmadan “canlı” gösterilmez |
| **Şarj** | EPDK tabanlı istasyon listesi, soket tipi, güzergâh yakınlığı | Anlık soket doluluğu veya tüm operatörlerin güncel ücretleri garanti edilmez |
| **Enerji** | Batarya durumundan türetilen açık etiketli menzil ve tüketim modeli | Model değeri gerçek araç ölçümü gibi sunulmaz |
| **OBD** | İzinli Bluetooth ELM327, salt okunur doğrulanmış veri, veri tazeliği | PID doğrulanmamışsa ekranda `—` gösterilir |
| **Yol ilerlemesi** | Doğrulanmış GPS konumu rota geometrisine izdüşürülür | GPS eski veya rota dışında ise ilerleme ölçülmüş gibi gösterilmez |
| **Medya** | TV, video, müzik, radyo ve diğer mevcut medya motorlarına bağlantı | Üçüncü taraf servislerin üyeliği veya oynatma garantisi verilmez |

## APK

GitHub **Actions** bölümünden **Elektrikli Yol Arkadaşım APK** adlı en son başarılı iş akışını aç, **Artifacts → Elektrikli-Yol-Arkadasim-APK** ZIP dosyasını indir ve içindeki APK'yı Android telefona kur.

Yeni debug sürümlerinin imzası farklıysa önceki kurulum üzerine yüklenmeyebilir. Kullanıcı verilerini yedeklemeden uygulamayı kaldırma. Cihaz izinlerini yalnız ilgili özelliğe ihtiyaç duyduğunda ver.

## Kullanım ve güvenlik

- Güzergâh, konum ve trafik servisleri harici sunuculara ağ isteği gönderebilir.
- Gerçek SoC ve sensör değeri yoksa örnek/sabit telemetri kullanılmaz.
- Şarj, hava ve enerji tahmini doğrulanmış araç sensörü değildir.
- Görsel medya araç hareketindeyken sürücü ekranı için kullanılmaz; sürüş kısıtlamaları kaldırılmaz.
- Kişisel trafik servis anahtarı cihazda Android Keystore ile şifreli saklanır. Çalışan istemcinin ağ isteğinde anahtar görünme riski ayrıca bulunabilir.
- Uygulama, otomatik testler başarılı olsa bile fiziksel telefon ve araç testinden geçirilmelidir.

## Teknik entegrasyon

Kaynak dokümandaki rota, OBD ve ilerleme yaklaşımı mevcut kodla uyumlu biçimde uygulanmıştır: tek Leaflet haritası; geçerli OSRM GeoJSON yol geometrisi; Android katmanında gerçek ELM327 olayları; doğruluk ve tazelik kontrolleriyle GPS konumunun rotaya izdüşürülmesi. **İkinci JavaScript köprüsü veya sabit demo verileri eklenmez.**

Ayrıntılar: [Teknik entegrasyon rehberi](docs/ELEKTRIKLI_YOL_ARKADASIM_ENTEGRASYON.md).

## Lisanslar ve veri güvenliği

Bu önizleme sürümü GPL-3.0 lisanslı açık kaynak medya tabanını değişikliklerle birlikte kullanır. Dağıtımda kaynak kod ve üçüncü taraf atıfları korunmalıdır. Tüm hak sahipliği, lisans koşulları ve bağımlılıklar için [LICENSE](LICENSE), [NOTICE.md](NOTICE.md), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) ve [trafik anahtarı güvenliği](SECURITY_TOMTOM.md) belgelerine bak.

**Görünen uygulama adı: Elektrikli Yol Arkadaşım.** Geriye dönük uyumluluk nedeniyle bazı eski dahili dosya, sınıf ve paket tanımlayıcıları halen korunur. Bunlar ayrı bir kontrollü geçiş gerektirir; mevcut uygulama güncellemesini bozacak şekilde topluca değiştirilmez.
