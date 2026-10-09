# APTV / CarPlay ile ID.4 / Android Auto arasındaki gerçek fark

Kontrol tarihi: 9 Ekim 2026.

- APTV'nin iOS 26.4+ CarPlay sürümü, IPTV ve yerel medya videosunu CarPlay ekranında sunuyor: https://aptv.app/carplay
- APTV'nin kendi TestFlight duyurusunda hareket halinde videonun da çalıştığı, bunun Apple kaynaklı bir hata olabileceği söyleniyor: https://t.me/s/AptvPlayerChannel?before=609
- Apple'ın resmî kullanım kılavuzu CarPlay videosunu sürüş dışına sınırlandırıyor: https://support.apple.com/guide/iphone/play-videos-with-carplay-xykemxkw6he5/ios
- Google Android Auto ve Android Automotive OS farklı platformlardır. Google'ın park uygulamaları kılavuzuna göre Android Auto video kategorisi henüz desteklenmiyor. Media kategorisi ise sürüşte ses içindir: https://developer.android.com/agents/skills/cars/android-for-cars-parked-apps/skill

## ID.4 Media Hub ne yapıyor?

- Telefon üzerinde kişisel/yasal M3U IPTV listesi, medya oynatma ve Netflix kısayolu.
- Android Auto'nun desteklenen ses kütüphanesi arayüzünde radyo; direksiyon ve medya kumandalarına uyum hedefi.
- Android Auto üzerinden Netflix, IPTV veya YouTube videosu sunmaz; hız/sürüş durumu değiştirme veya sürüş sırasında ön ekranda video oynatma kilidi aşma kodu içermez.

## Gerçekçi kullanım seçenekleri

- Araç ekranında sürüş sırasında: ses ve medya denetimleri.
- Araç park halindeyken: telefonda IPTV/video; araç ekranında video desteği başlı başına platform/araç uyumluluğu gerektirir.
- Yolcuya ait bağımsız bir ekran/tablet: araçtaki sürücü ön ekranından bağımsız olduğu sürece yolcu görüntü tüketimi için seçenek olabilir.

## Derleme ve test kanıtı

Projedeki `tests/architecture_checks.py` ve `tests/M3uParserTest.kt` kaynak düzeyindeki kontrollerdir. Gerçek Android Gradle build, telefon kurulumu ve ID.4 testi henüz yapılmamıştır. Bu nedenle bir APK veya araçta çalıştığı iddiası yoktur.
