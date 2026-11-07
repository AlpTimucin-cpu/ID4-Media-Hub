# EV Cockpit — Faz 0: doğrulanmış temel denetimi

Tarih: 7 Kasım 2026. Durum: **GÜVENLİK VE CI ÇIKIŞ KAPISI AÇIK DEĞİL.**
Bu doküman GitHub dosyaları, PR karşılaştırmaları ve gerçek GitHub Actions kayıtlarından hazırlandı.
Sadece Faz 0; başka faz kodlanmadı. Tam Git geçmişi/eskiden üretilmiş APK'lar incelenmedi.

## 1. Kritik / yüksek / orta / düşük bulgular

| Şiddet | Bulgu | Kanıt / karar |
| --- | --- | --- |
| KRİTİK | Geçmiş geçici/indirilebilir imzalama anahtarı akışları | PR #19 bunları yalnızca taslak dalda kapatıyor; `main` hâlâ eski workflow'ları içeriyor. Gerçek anahtarın ihlal durumu bilinmiyor. Üretim yayını yasak. |
| KRİTİK | Eski TomTom/keystore geçmiş sızıntısı olasılığı | Eski Git commit'leri ve Release APK'lar doğrulanmadı. İptal/rotasyon ve özel olay incelemesi sahibin görevi. |
| YÜKSEK | `main` ile kokpit birbirine karıştırılıyor | `main/settings.gradle.kts`: `ID4MediaHub` ve yalnızca `:app`; `feature/ev-cockpit/settings.gradle.kts`: `EvCockpit` ve `:app`, `:evlite`, `:nativecockpit`. Gerçek Compose kokpit `:nativecockpit` sadece feature dalında; `media-engine/` Gradle modülü değil. |
| YÜKSEK | Android CI tüm hedef ürünü kanıtlamıyor | PR #17–#22 dalında `:app` derleniyor; `:nativecockpit` o dalda yok. Yeşil `:app` testi bile gerçek kokpit kabulü değildir. |
| YÜKSEK | Son CI lint başarısız | PR #22 koşusu 42359322139: **11 lint hatası, 69 uyarı**; ilk hata AndroidManifest.xml:18 `MissingIntentFilterForMediaSearch`. Faz 0'da sadece doğrulanan eksik intent eklenmiştir; diğer lint hataları ayrıca değerlendirilmelidir. |
| YÜKSEK | Politika dışı medya kısayolu | `main` kaynaklı `SportzX` kısayolu üç ayrı UI bölümünde ve Manifest sorgusunda bulundu. Bu Faz 0 PR'da kısayol kaldırıldı; yayımlanmış eski APK'lar değişmez. |
| YÜKSEK | `minSdk=26` hedefi gerçek değil | `main/:app`, `feature/:app`, `feature/:evlite`, `feature/:nativecockpit` kontrol edilen build dosyalarında `minSdk = 28`. 26'ya indirmek ancak Android 8 API uyumluluğu ve cihaz/emülatör testlerinden sonra önerilebilir. |
| ORTA | Sürüm/dağıtım kanıtı | `main` PR zincirinde Gradle 8.14.3 SHA sabit, JDK 17, SDK 36, AGP 8.11.1/Kotlin 2.2.20. `feature/ev-cockpit` wrapper URL 8.14.3, ancak dağıtım SHA256 satırı yok; doğrulama ve standardizasyon gerekli. |
| ORTA | APK ve Git leak kontrolleri salt sınırlandırılmış | PR #18, SHA pinli Gitleaks ve APK ZIP-üye taraması ekliyor. PR taraması eski/özel kalıpları garanti etmez; otomatik tüm-geçmiş taraması ve geçmiş binary incelemesi ayrı. |
| ORTA | Kök tarihî ZIP | PR #21, doğrulanmamış `EV_Cockpit_Legacy_Source_v0.8.zip` dosyasını güncel ağaçtan kaldırıyor. Git geçmişinde kalıyor. İçeriği incelenmeden GitHub Releases'e koymak güvenilirlik ve dağıtım riski; bu PR onu yayınlamaz. |
| ORTA | Depo açıklaması/topic yönetimi | Bağlı araçlarla GitHub repo metadata güncelleme doğrulanmadı; sahibin GitHub sayfasından düzenlemesi gerekiyor. |
| DÜŞÜK | README ve CODEOWNERS | PR #16 doğru dal başına modül/build komutlarını belgeliyor; #21 güvenlik/bakım sahipliği getiriyor. CODEOWNERS tek başına zorunlu inceleme yaratmaz. |

### Son doğrulanmış CI kanıtı

PR #22, GitHub Actions #42359322139:
- `lintDebug`: 11 hata / 69 uyarı nedeniyle hata.
- `testDebugUnitTest`: başarılı.
- `assembleDebug`: başarılı.
- APK gizli bilgi kontrolü: başarılı.
- Son kalite kapısı: **başarısız**.

Bu kanıt `main` tabanlı medya `:app` ürünüdür, `:nativecockpit` derlemesi değildir.
PR #22 Gitleaks #42359322339: başarılı; bu sadece yeni commit farkı içindir.

## 2. Salt-okunur PR denetimi ve önerilen sıra

| PR | Amaç | Risk / çakışma | Öneri |
| --- | --- | --- | --- |
| #16 | README + 8.14.3 wrapper, gerçek modül komutları | `main` içine native modül taşımıyor | Wrapper SHA ve `./gradlew projects` kontrolü sonrası incele |
| #17 | Pinli Debug CI | `:app` testini kokpit testi saymak yanlış; lint kalite kapısı kırmızı | Gerçek modül/branch kapısını ve lint raporlarını düzeltmeden birleştirme |
| #18 | Gitleaks ve APK anahtar taraması | Otomatik tarama heuristik; tüm eski geçmiş ve Release binary taranmadı | Özel geçmiş inceleme ve TomTom iptaliyle birleştir |
| #19 | Onaylı sertifikalı APK/AAB üretimi | `production-release` koruması henüz doğrulanmadı; `main` içinde `:nativecockpit` yok | İmzalama sırrı gözden geçirilene kadar **taslak tut** |
| #21 | `.gitignore`, ZIP kaldırma, Dependabot, CODEOWNERS, SECURITY | Eski ZIP geçmişte kalır; otomatik import yeniden açılmamalı | Güvenli, ama eski ZIP'i Releases'e incelemeden taşıma |
| #22 | P0–P5 özellik yol haritası | Yalnızca belge; build garantisi vermez | İsteğe bağlı, uygulama kodu değil |

PR'lar iç içe taban dallara sahiptir: `#16 → #17 → #18 → #19 → #21 → #22`.
Onaylı sıralı merge/retarget gerektirir; doğrudan `main` değişikliği yapılmadı.

## 3. Faz 0 düzeltme kapsamı

Bu ayrı faz 0 PR yalnızca:
1. `app/src/main/AndroidManifest.xml` içinde eksik `android.media.action.MEDIA_PLAY_FROM_SEARCH` eylemini servis intent filtresine ekledi; platform ve player davranışı cihazda hâlâ doğrulanmadı.
2. `app/src/main/java/tr/id4/mediahub/MainActivity.kt` içindeki tüm SportzX kısayollarını kaldırdı; normal YouTube/Netflix telefon bağlantılarını korudu.
3. `app/src/main/java/tr/id4/mediahub/SportzXLauncher.kt` dosyasını kaldırdı.
4. `tests/test_phase0_source_policy.py` ile modül dosyaları, kısayol yokluğu, medya intent'i, wrapper SHA ve güncel ZIP yokluğunu statik olarak sınadı.
5. Bu denetim raporunu ekledi.

`main/settings.gradle.kts`, `app/build.gradle.kts`, `nativecockpit` kaynakları, `gradle.properties`, release kimlikleri ve secret'lar değişmedi. `minSdk 26` bu fazda doğrulanmadığı için uygulanmadı.

## 4. Gerçek Gradle proje beklentileri

```text
main ve bu PR'ın taban zinciri:
Root project 'ID4MediaHub'
\--- Project ':app'

feature/ev-cockpit:
Root project 'EvCockpit'
+--- Project ':app'
+--- Project ':evlite'
\--- Project ':nativecockpit'
```

Bu `settings.gradle.kts` ve dosya ağacından türetilmiş **beklenen** liste; gerçek terminal `./gradlew projects` çıktısının yeni Faz 0 commit'inde yeniden üretilmesi gerekir.

Sürüm kataloğu `gradle/libs.versions.toml` iki dalda da doğrulanmış mevcut bir dosya değil; olmayan dosya adına kaynak eklenmedi.

## 5. Doğrulama komutları ve kabul

```bash
git switch audit/phase0-baseline-20261107
./gradlew --version
./gradlew projects
python3 -m unittest discover -s tests -p 'test_phase0_source_policy.py' -v
python3 -m unittest discover -s tests -p 'test_apk_secret_scan.py' -v
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --no-daemon --stacktrace
python3 scripts/check_apk_secrets.py app/build/outputs/apk/debug/app-debug.apk
gh run list -R AlpTimucin-cpu/ev-cockpit --branch audit/phase0-baseline-20261107 --limit 10
```

Beklenen: gerçek dalda yalnızca `:app`; lint + test + assembleDebug ve sızıntı taraması **tamamı başarılı** olursa medya `:app` için Faz 0 teknik kapısı geçilebilir. `nativecockpit` kabulü için ayrı gerçek native dalında lint/test/APK gereklidir. Beklenmedik Kotlin derleme hatası, lint veya kaynak bağımlılığı eksikliği durumunda başarısızlık gizlenmez. `./gradlew :nativecockpit:assembleDebug` bu Faz 0 dalında **Project not found** hatası vermelidir; gerçek kaynak bu dalda yok.

Sırf Debug APK oluştu diye Android Auto veya VW ID.4 Pure üzerinde test edilmiş kabul edilmez. OBD servisleri yalnızca Mode 01 / UDS 0x22 kalmalı; bu PR OBD içeriğine dokunmaz.

## 6. Sahip tarafı kapatılması gereken işler

- TomTom hesabında eski anahtarı devre dışı bırak / kullanımı incele; yeni anahtarı asla GitHub/BuildConfig/APK/issue içine koyma.
- Eski imzalama anahtarı ve sertifika sürekliliği incelemesini tamamla; GitHub `production-release` ortamına zorunlu bağımsız onay kur.
- `main` üzerine native modül taşıma stratejisini ve `minSdk 26` destek kararını ayrıca onayla.
- GitHub repo About alanına açıklama ve konu etiketlerini (örn. `android`, `kotlin`, `android-auto`, `ev`) **kendi hesabından** gir.
- Historik ZIP'in içerik/lisans/gizli veri kontrolü yapılmadan GitHub Releases'e eklenmesine izin verme.
- Debug APK'yı Android cihazda, GPS ve AA DHU üzerinde test edip SHA/Android sürümüyle kanıtla.

## 7. Geri alma

Bu PR taslaktır, `main` doğrudan değişmez. PR'ı kapatmak veya ayrı dalı silmek tüm Faz 0 çalışma ağacı değişikliklerini dışarıda bırakır. Birleştirildikten sonra gereken geri alma yeni, gözden geçirilen `git revert` PR'sıyla yapılmalıdır. Silinen tarihi ZIP ve kısayol eski commit'lerde bulunabilir; bu davranış tarih silme değildir.

## 8. Çıkış kapısı

**Yalnızca faz 0 çıkış kapısı yeşil olduğunda FAZ 1'e geçilir.** Gitleaks, APK kontrolü, `lintDebug`, `testDebugUnitTest` ve `assembleDebug` hepsi ilgili gerçek kokpit dalı için bağımsız şekilde doğrulanmalıdır.
