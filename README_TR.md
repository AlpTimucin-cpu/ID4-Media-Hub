# EV Cockpit — Derleme ve Dal Durumu

Bağımsız GPL-3.0 lisanslı Android projesi. **`main` ile `feature/ev-cockpit` aynı modül yapısına sahip değildir.** Aşağıdaki bilgiler 7 Kasım 2026 tarihli GitHub dosya ağacından doğrulanmıştır.

| Yol | `main` | `feature/ev-cockpit` |
| --- | --- | --- |
| `:app` | Android medya uygulaması (`tr.id4.mediahub`) | Android medya uygulaması |
| `:evlite` | Yok | Hafif EV araçları |
| `:nativecockpit` | Yok | Kotlin/Compose, rota, salt-okunur OBD, Android Auto |
| `media-engine/` | Yok | İsteğe bağlı medya kaynak entegrasyonu; Gradle modülü değil |

`main/settings.gradle.kts` yalnızca `:app` içerir, proje adı `ID4MediaHub`'dır. `feature/ev-cockpit` dalı `:app`, `:evlite` ve `:nativecockpit` içerir; proje adı `EvCockpit`'tir. Kaynak dosyaları taşınmadan `main` içerisine hayalî modül eklenmemelidir.

## Ön koşullar

Java 17, Android SDK 36 ve Gradle Wrapper 8.14.3. Wrapper dağıtımı resmî SHA-256 ile denetlenir. AGP 8.11.1, Kotlin Gradle Plugin 2.2.20'dir.

## `main` derlemesi (medya uygulaması)

```bash
git switch main
./gradlew --version
./gradlew projects
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --stacktrace
```

Beklenen modül: `:app`. Başarılı derlemeden sonra beklenen dosya: `app/build/outputs/apk/debug/app-debug.apk`.

## `feature/ev-cockpit` derlemesi (asıl kokpit)

```bash
git switch feature/ev-cockpit
./gradlew --version
./gradlew projects
./gradlew :nativecockpit:testDebugUnitTest :nativecockpit:assembleDebug --no-daemon --stacktrace
./gradlew :nativecockpit:tasks --all
```

Beklenen modüller: `:app`, `:evlite`, `:nativecockpit`. Başarılı derlemeden sonra beklenen Debug APK: `nativecockpit/build/outputs/apk/debug/nativecockpit-debug.apk`.

6 Kasım 2026 tarihli [Verified Debug APK çalışması](https://github.com/AlpTimucin-cpu/ev-cockpit/actions/runs/42280552718) başarılıdır. Bu sonuç, telefon veya araçta kusursuz çalışma kanıtı değildir.

## Güvenlik

Yalnızca salt-okunur ELM327 sorguları kullanılır. Doğrulanmamış SoC/DID verileri gerçek ölçüm olarak sunulmaz. API anahtarları APK içine eklenmemelidir. Eski TomTom anahtarıyla ilgili olası geçmiş sızıntısı için [SECURITY_TOMTOM.md](SECURITY_TOMTOM.md) belgesine bakın.

Eski GitHub Release APK'ları sahibin kalıcı imzalama sertifikasıyla doğrulanmış kabul edilmemelidir. Geliştirme dalındaki production release işi güvenlik incelemesi sebebiyle devre dışıdır. Ayrıntılar: [APK derleme kılavuzu](docs/APK_BUILD_GUIDE.md).

Gerçek GPS, OBD, TTS, ses odağı, medya ve Android Auto kabul testleri fiziksel cihaz/araç üzerinde ayrıca yapılmalıdır.
