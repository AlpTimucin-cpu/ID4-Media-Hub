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

## Derleme

Java 17, Android SDK 36 ve Gradle 8.14.3 kullanılır.

```bash
gradle :nativecockpit:testDebugUnitTest :nativecockpit:assembleDebug
```

Test APK yolu: `nativecockpit/build/outputs/apk/debug/nativecockpit-debug.apk`.

## Paket geçişi

Yeni uygulama kimliği Android'de ayrı bir kurulum oluşturur. Eski uygulama silinmez; özel veriler, tercihler, oturumlar ve izinler kendiliğinden taşınmaz. Manifest, Hilt, kaynak importları, ProGuard ve CI komutları yeni paketle birlikte güncellenmiştir.

Ayrıntılar: [Marka ve paket geçişi](docs/BRAND_MIGRATION.md).

Gerçek harici uygulama paketleri ve çalışan veri kaynağı adresleri, uyumluluğu korumak için kendi tanımlı değerlerini kullanır. Rota, şarj ve menzil tahminleri ölçülmüş araç verisi gibi sunulmaz. Marka değişikliği sürüş ekranında video kısıtlarını kaldırmaz.

## Lisans ve güvenlik

[LICENSE](LICENSE), [NOTICE](NOTICE.md), [üçüncü taraf bildirimleri](THIRD_PARTY_NOTICES.md) ve [API anahtarı güvenliği](SECURITY_TOMTOM.md) belgelerini inceleyin. Kaynak atıfları ve lisans metinleri korunur.

[English](README_EN.md)
