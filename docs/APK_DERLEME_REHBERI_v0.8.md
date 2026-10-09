# ID.4 Media Hub v0.8 — APK derleme rehberi

**Bu ZIP APK değildir.** Gerçek Android SDK/Gradle derlemesi yapılana kadar `app-debug.apk` ortaya çıkmaz. Araçta çalışması da ancak POCO F5 ve Volkswagen ID.4 testleriyle doğrulanabilir.

## Yöntem 1: GitHub Actions (bilgisayara Android Studio kurmak gerekmez)

1. Kendi GitHub hesabında **özel (private) bir depo** oluştur. Proje klasörünün içindeki bütün dosyaları, özellikle gizli görünebilen `.github/workflows/android-debug-apk.yml` dosyasını depoya ekle.
2. Deponun **Actions** sekmesinden **ID4 Media Hub - debug APK** iş akışını aç. Gerekirse ilk çalıştırmada Actions izinlerini kendi hesabından etkinleştir.
3. **Run workflow** düğmesine bas. İş akışı JDK 17, Android SDK 36, Gradle 8.13 kurup `:app:testDebugUnitTest :app:assembleDebug` çalıştırır.
4. Çalışma başarılıysa sayfanın **Artifacts** bölümündeki `ID4-Media-Hub-v0.8-debug` dosyasını indir ve ZIP'i çıkar. İçindeki `app-debug.apk` gerçek debug APK'sıdır; yanında `APK_SHA256.txt` kontrol toplamı bulunur.
5. İş akışı hata verirse `Compile, run Android JVM tests and build APK` adımındaki hata günlüğünü kontrol et. Hata düzeltilmeden dosyanın çalıştığı varsayılmamalıdır.

Önemli: Bu GitHub iş akışı hazırlandı, ancak henüz gerçek GitHub sunucusunda çalıştırılmadı. İlk derlemede API/bağımlılık hatası çıkması mümkündür. Proje dosyaları yayın anahtarı, SportzX APK'sı, Netflix içeriği veya kullanıcı IPTV erişim bilgisi içermez.

## Yöntem 2: Windows üzerinde Android Studio

1. ZIP'i aç, **Android Studio > Open** menüsünden `ID4_Media_Hub_v0_8` klasörünü seç.
2. JDK 17 (Android Studio'nun uygun gömülü JDK'sı), Android SDK Platform 36, Gradle 8.13 gerekir. İlk açılışta Sync işlemini tamamla.
3. **Build > Build APK(s)** ile derle veya kurulu `gradle` komutu varsa `scripts/build_windows.cmd` dosyasını çalıştır.
4. Hatasız derlemeden sonra APK yolu `app/build/outputs/apk/debug/app-debug.apk` olur.
5. Telefonunda USB hata ayıklama açıkken `adb install -r app/build/outputs/apk/debug/app-debug.apk` komutuyla kurulumu yapabilirsin.

## Telefon / ID.4 doğrulama sırası

- Telefonda ID.4 Media Hub açılıyor mu? Varsayılan radyo listesi geliyor mu?
- Sesli radyo bağlantısı açılıp durdurulabiliyor mu? Telefon ekranı kapandığında ses kesiliyor mu?
- Android Auto medya listesinde uygulama görünüyor mu? Çalmayı güvenli park ortamında test et.
- Direksiyon kumandaları, radyo değiştirme, sesli arama ve bağlantı kopup tekrar bağlandığında oynatma davranışını doğrula.
- Telefon içi IPTV video, HLS, Netflix ve SportzX kısayolları **Android Auto ön ekranında video açmaz**. Sürüş güvenliği kilitleri veya aracın firmware'i değiştirilmez.

## Sürüm kontrolü ve hata ayıklama

Uygulama paket adı `tr.id4.mediahub`, sürüm `0.8` (`versionCode=9`). Android Auto sürümü için `scripts/diagnostics_windows.cmd` kullanılabilir. Günlükleri paylaşmadan önce diğer uygulamalara ait kişisel bilgileri çıkar. Bu doküman cihaz üzerinde doğrulanmış bir kurulumu iddia etmez.
