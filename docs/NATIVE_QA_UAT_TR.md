# Elektrikli Yol Arkadaşım — Native Kokpit QA / UAT
Tarih: 29.10.2026 · Dal: `feature/mola-native-id4`

## Kapsam ve üretime hazır olma kararı
Bu geçiş **şimdilik production-ready değildir**. Derleme ve JVM birim testlerinin başarılı olması,
donanımda TTS/OBD, Android Auto, PiP ve üçüncü taraf medya uyumluluğunu doğrulamaz.

### Statik kod incelemesi
- **TTS:** `NavigationViewModel.onCleared()` -> `guide.close()` -> `tts.stop()`, `tts.shutdown()`, `ducking.close()`. `MainActivity.onPause()` -> `setForeground(false)` -> `guide.stop()`. Android Auto `EyaCarNavigationScreen.onStop()` seslendirimi durdurur; `onDestroy()` ses motorunu kapatır. Activity her duraksadığında TTS'yi shutdown etmek, yeniden başlamada motoru kullanılamaz kılardı; bu nedenle sadece stop yapılır.
- **AudioFocus:** `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`; konuşma bittiğinde veya iptalinde odak bırakılır. QA düzeltmesi `AudioFocusLease` ile tek sahipliği ve stale callback korumasını test edilebilir kılar. Üçüncü taraf oyuncunun gerçekten ses kısması **garanti değildir**.
- **PiP:** Manifest `supportsPictureInPicture=true` ve `OwnVideoPip` yalnızca *uygulamamızın sahip olduğu* aktif video için çalışabilir. Dış Netflix/Disney+/Prime PiP özelliği diğer uygulamaya aittir. Kodda `ParkedStreamingDialog(verifiedParked=false)` olduğu için Native Kokpit video başlatma butonları **devre dışı**. Araç hareketli video kilidi/DRM devre dışı bırakılamaz. Harici uygulamaya geçildiğinde `MainActivity.onPause()` GPS/TTS ve OBD akışını durdurur; arkada navigasyon sürdürülmez. Bu bir yaşam döngüsü kısıtıdır, çökme kanıtı değildir.
- **OBD:** ELM327 oturumu `ATZ, ATE0, ATL0` ile başlar, salt-okunur sorguları üç denemeyle sınırlar. Kopma ve zaman aşımı bağlantıyı kapatır. QA düzeltmesi: `close()` edilen oturum `initialize()` ile yeniden açılamaz. Cihaz denetleyicisinin hata halinde `ObdConnectionState(phase=ERROR, error=...)` yayımlaması ayrı bağlantı testinde doğrulanmalıdır.
- **Enerji:** `EvBaselineEnergyEstimator` 110 km/sa üstünde logaritmik hız katsayısı, sıcaklıkta klima etkisi uygular. Eksik girdide tarafsız çarpan kullanılır, NaN ve bayat OBD reddedilir. Çarpanlar ölçülmüş fiziksel model değil, kalibrasyon varsayımıdır.

## JVM otomatik test matrisi
| Test dosyası | Sınıf / senaryo | Beklenen |
|---|---|---|
| `EvBaselineEnergyEstimatorTest.kt` | 65 km/sa, 40°C, klima açık | hız 1.00, klima 1.08, 19.44 kWh / 100 km |
| aynı | 150 km/sa, -5°C, klima açık | hız = 1 + .12·ln(3), klima = 1.24 |
| aynı | hız/sıcaklık eksik; NaN/Infinity | varsayılan 1.00/1.00, geçersiz girdiye Unavailable |
| `Elm327SessionTest.kt` | BT kopma bir kez | reconnect, ELM init yeniden |
| aynı | sürekli BT kopma / kontağın kapanması | 3 denemede IOException, soket kapalı |
| aynı | close sonrası initialize | IllegalStateException, oturum tekrar açılmaz |
| `AudioFocusLeaseTest.kt` | fake Mola oynatıcı + geçici TTS focus | taklit ses kısılır ve eski seviyesine döner |
| aynı | focus reddedildi / geç callback | medya değişmez; close sonrası callback yok sayılır |

## Telefon/araç üzerinde UAT
**Ön şartlar:** test APK'sının SHA-256 değerini karşılaştır; Türkçe TTS dil paketini yükle; Android konum iznini aç; telefonu ve aracı güvenli şekilde park et; yalnız kendi Bluetooth OBD adaptörünü kullan. Yol testlerinde ikinci bir kişi gözlem ve kayıt yapmalı; sürücü ekranla etkileşime girmemeli.

1. Native APK'yı kur ve uygulamayı aç. Açılışta ANR/crash olmadığını kontrol et.
2. Konum iznini reddet: harita doğru hata göstermeli; sahte GPS göstermemeli. Ardından hassas konum izni ver ve GPS'in güncellendiğini doğrula.
3. `SES` ekranından `Türkçe ses testi` çalıştır: 10 defa tekrarla; ses motoru boşa düşmemeli ve bekleme sırasında ses odağı tutulmamalı.
4. Park halinde, telefon üzerinde izin verilen bir ses medyası (Mola) çal; SES testi başlat. Müzik Android sisteminin seçimine göre kısılmalı/durmalı ve konuşma sonunda normale dönmeli. Dönmüyorsa FAIL.
5. Gerçek OSRM rota oluştur; rota çizgisini ve manevraları doğrula. Güvenli gerçek yol koşullarında yalnız yolcu gözlemiyle yaklaşık 200 m / 50 m anonslarını takip et. Uygulamanın mock GPS reddi gerçek sürüş testinin yerine geçmez.
6. TTS sırasında uygulamayı arka plana al; konuşma durmalı, ses odağı bırakılmalı. 10 kez arka plan/öne alma ve 5 kez ekran döndürme sonrası bellek/ANR kontrolü yap.
7. Park halindeyken OBD adaptörünü eşleştir ve bağlan. Doğrulanmış ham `010D` sorgusu dışında ekranda olmayan veri uydurulmamalı.
8. OBD bağlıyken adaptörün bağlantısını kontrollü kes veya test adaptörünün gücünü kapat. Üç başarısız denemede UI ERROR görmeli ve eski veri canlı gibi kalmamalı. Adaptör yeniden açıldığında açık kullanıcı işlemiyle reconnect et.
9. `Medya` bölümünde güvenilir Park verisi bulunmuyorsa Netflix/Disney+/Prime düğmelerinin devre dışı kaldığını doğrula. Beklenen davranış budur.
10. Telefonun **kendi Netflix/Disney+/Prime** uygulamasındaki PiP'i, o uygulama destekliyorsa ayrıca dene. Bu, `Elektrikli Yol Arkadaşım`ın PiP testi değildir. Native Kokpit'in harici uygulamayı PiP'e zorlaması beklenmemeli.
11. Uygulamanın kendi oynatıcısı Park bilgisiyle doğrulandığında (gelecekte), yalnız ona ait PiP geçişi/giriş/çıkışı sınanmalı; şimdiki sürümde bu uçtan uca akış yok.
12. Android Auto DHU ve gerçek araç oturumlarında TTS sahipliği çakışmasını, bağlantı kesilmesi/yeniden bağlanma ve 13.000+ istasyon harita kaydırma akıcılığını ayrıca test et.

### ADB gözlem komutları (Windows PowerShell)
```powershell
$adb = "C:\Users\Alp\Desktop\platform-tools\adb.exe"
& $adb logcat -c
& $adb logcat -v time | Select-String "FATAL EXCEPTION|ANR|AudioFocus|TextToSpeech|Bluetooth|MapLibre"
```

### UAT kabul ölçütleri
- Sıfır FATAL EXCEPTION / ANR; TTS lifecycle testleri sırasında sürekli artan bellek kullanımı veya tutulmuş odağın olmaması.
- OBD kopma sonrası uygulamanın kilitlenmemesi; hata görünmesi; geçersiz/bayat ölçümün canlı sunulmaması.
- Enerji sonuçlarının `MODEL/TAHMİN` biçiminde gösterilmesi; canlı SoC yalnız doğrulanmış OBD verisinden.
- Harici medya + PiP için olmayan yeteneklerin 'çalışıyor' olarak sunulmaması.
- JUnit + Gradle assembleDebug başarılı olsa bile donanım UAT tamamlanmadan production-ready ilan edilmez.
