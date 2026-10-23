# ID.4 Yol Arkadaşım — Volkswagen ID.4 Pure

**Harita · Rota · Türkiye AC/DC şarj istasyonları · ELM327/OBD · Mola tabanlı Android önizlemesi**

[![Yeni ID.4 sürüm derlemesi](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/actions/workflows/build-mola-native-id4.yml/badge.svg?branch=feature%2Fmola-native-id4)](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/actions/workflows/build-mola-native-id4.yml)
[![Otomatik ekran görselleri](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/actions/workflows/render-id4-readme.yml/badge.svg?branch=feature%2Fmola-native-id4)](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/actions/workflows/render-id4-readme.yml)

> **Aktif proje ve en güncel kod:** [**feature/mola-native-id4 → Tüm özellikler, APK kurulum adımları, kaynaklar ve test durumları**](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/tree/feature/mola-native-id4). Varsayılan `main` dalındaki eski Media Hub kodu ile Mola-ID4 önizlemesi farklı geliştirme hatlarıdır.

![Gerçek kokpit HTML kodundan alınan çevrimdışı tarayıcı önizlemesi](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/raw/refs/heads/feature/mola-native-id4/docs/screenshots/cockpit-browser-preview.png)

| Telefon rota önizlemesi | Kompakt yatay kokpit |
|---|---|
| ![Telefon HTML görünümü](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/raw/refs/heads/feature/mola-native-id4/docs/screenshots/route-phone-browser-preview.png) | ![Kompakt kokpit HTML görünümü](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/raw/refs/heads/feature/mola-native-id4/docs/screenshots/cockpit-compact-browser-preview.png) |

*Görseller uygulamanın gerçek HTML/CSS kodunun çevrimdışı tarayıcı renderlarıdır; Android cihaz/ID.4 araç ekranı fotoğrafı değildir. Sahte SoC, hız, rota, istasyon doluluğu gösterilmez.*

## Hızlı başlangıç

**[Son başarılı kurulabilir ID.4 APK derlemesini aç](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/actions/workflows/build-mola-native-id4.yml?query=branch%3Afeature%2Fmola-native-id4)** → çalışmanın **Artifacts** bölümünden ZIP'i indir → içindeki `Mola-ID4-Preview.apk` dosyasını çıkar.

**Önemli:** Bu sürüm orijinal Mola 3.2'nin üzerine kurulmaz; açık kaynak Mola 2.2.2 tabanından ayrı paket kimliğiyle derlenir. Android Auto'da telefon WebView'ı ve canlı araç ölçümleri fiziksel araçta doğrulanmadı. Canlı EPDK soket durumları, istasyona özel fiyat ve kampanyalar ancak izinli/güvenilir veri kaynağıyla görüntülenebilir. Detaylar için [aktif dalın README'sini](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/blob/feature/mola-native-id4/README.md) incele.

## Repo dalları

- **[ID.4 Yol Arkadaşım — aktif dal](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/tree/feature/mola-native-id4)**: Tesla-benzeri sade kokpit, OSRM, Open-Meteo, EPDK istasyonları, salt-okunur MEB/OBD deneyleri, Mola overlay kaynakları.
- **`main` / eski Media Hub**: İlk Android medya uygulaması prototipi; orijinal açıklama [docs/legacy-media-hub.md](docs/legacy-media-hub.md) içinde saklandı.

Bu bağımsız deney projesi Volkswagen, Mola veya Google tarafından onaylanmış değildir.
