# EV Cockpit

Elektrikli araçlar için Kotlin, Jetpack Compose, MapLibre ve OSRM tabanlı, harita odaklı kokpit.

## Arayüz

- Tam ekran harita; sol üstte büyük batarya yüzdesi ve tahmini menzil.
- Sonraki manevra, varış saati ve tahmini varış SoC değerini içeren sade panel.
- İnce Rota / Şarj / Medya / Ayarlar menüsü.
- Koyu lacivert yüzeyler, turkuaz vurgu, büyük tipografi ve daha keskin köşeler.
- Ölçüm yoksa `—`; simülasyon açıksa açık test verisi etiketi.

## Modüller

- `nativecockpit`: `com.evcockpit` paketli Compose kokpiti ve Android Auto navigasyonu.
- `app`: `com.evcockpit.mediahub` paketli medya araçları.
- `evlite`: `com.evcockpit.evguide.lite` paketli hafif harita uygulaması.
- `media-engine`: ayrı lisanslı medya tabanı için kaynak entegrasyonu.

## Gerçek modüller ve derleme

Dallar birbirinden farklıdır: `main` dalı `ID4MediaHub` adlı, yalnızca
`:app` modülünü içeren medya uygulamasıdır. **Gerçek kokpit**
`feature/ev-cockpit` dalındaki `:nativecockpit` modülüdür. Bu dalda
Gradle modülleri `:app`, `:evlite` ve `:nativecockpit` olarak tanımlıdır.
`media-engine/` kaynak dizini Gradle modülü değildir.

Java 17, Android SDK 36 ve SHA-256 ile doğrulanan Gradle Wrapper 8.14.3 kullanılır.
Mevcut `:nativecockpit` minimum Android sürümü **28**'dir. İstenen API 26 desteği
henüz test edilmediği için etkinleştirilmedi.

```bash
git switch feature/ev-cockpit
./gradlew --version
./gradlew projects
./gradlew :nativecockpit:lintDebug :nativecockpit:testDebugUnitTest :nativecockpit:assembleDebug --no-daemon --stacktrace
```

Başarılı Debug APK yolu:
`nativecockpit/build/outputs/apk/debug/nativecockpit-debug.apk`.

FAZ 0 güvenlik iyileştirmeleri
[`audit/phase0-native-validation-20261107`](https://github.com/AlpTimucin-cpu/ev-cockpit/pull/25)
dalındadır: Gitleaks, APK anahtar denetimi, Türkçe metinler ve yalnızca
Mod 01 / UDS 0x22 tanı okuması için testler. Bu PR henüz `feature` dalına
birleştirilmedi.

**ID.4 BMS SoC DID/CAN adresleri/çarpanları doğrulanmadı.** Taslak güvenlik
düzeltmesi otomatik MEB DID taramasını ve sayısal batarya çıkarımını kapatır;
kullanıcının başlattığı UDS 0x22 tanılamasının ham yanıtı ölçülmüş SoC sayılmaz.
Gerçek cihaz, GPS, Bluetooth ELM327 ve Android Auto kabul testleri ayrıca
gereklidir. Eski TomTom/imzalama anahtarı riski giderilmeden üretim yayını yoktur.

## Paket geçişi

Yeni uygulama kimliği Android'de ayrı bir kurulum oluşturur. Eski uygulama silinmez; özel veriler, tercihler, oturumlar ve izinler kendiliğinden taşınmaz. Manifest, Hilt, kaynak importları, ProGuard ve CI komutları yeni paketle birlikte güncellenmiştir.

Ayrıntılar: [Marka ve paket geçişi](docs/BRAND_MIGRATION.md).

Gerçek harici uygulama paketleri ve çalışan veri kaynağı adresleri, uyumluluğu korumak için kendi tanımlı değerlerini kullanır. Rota, şarj ve menzil tahminleri ölçülmüş araç verisi gibi sunulmaz. Marka değişikliği sürüş ekranında video kısıtlarını kaldırmaz.

## Lisans ve güvenlik

[LICENSE](LICENSE), [NOTICE](NOTICE.md), [üçüncü taraf bildirimleri](THIRD_PARTY_NOTICES.md) ve [API anahtarı güvenliği](SECURITY_TOMTOM.md) belgelerini inceleyin. Kaynak atıfları ve lisans metinleri korunur.

[English](README_EN.md)
