# Mola → ID.4 Yol Arkadaşım | Göç planı

## Uygulanan ilk aşama
Bu dal Mola 3.2'yi kendi APK'sı içine **gömmez**; Mola'ya kullanıcı onaylı
telefon bağlantısı sağlar. Projedeki IPTV, radyo, M3U, EPG, YouTube, katalog
ve favoriler korunmuştur.

- Mola 3.2'nin APK içindeki paket adı: `me.app.fermataX.auto.mola32.debug`.
- Uygulama kuruluysa kullanıcı onayıyla telefonda açılır.
- TV için kullanıcının seçtiği tek bir HTTPS yayın bağlantısı aktarılır.
- Radyo için seçilen tek istasyonun HTTPS bağlantısı aktarılır.
- APK otomatik indirilmez veya kurulmaz.
- Android Auto video kilitleri ya da araç hareket bilgisini değiştiren
  bir yöntem kullanılmaz.

## Kaynak ve lisans
- Mola kaynak deposu: https://github.com/moris545454/araba-
- Ana proje: https://github.com/AndreyPavlenko/Fermata
- Mola/Fermata: GNU GPL-3.0. Kaynak kodu alınıp tek APK'ya
  birleştirilerek dağıtılırsa ilgili lisans/telif notları korunmalı,
  uygun GPL kaynak kodu erişimi sağlanmalıdır.
- Yüklenen `Mola-3.2.apk` dosyasının, genel GitHub deposunun
  mevcut kaynak kodundan derlendiği **doğrulanamadı**.
- Depodaki 2.2.2 için hazırlanmış güvenlik raporu 3.2'yi kapsamıyor.

## Tek APK'ya gerçek kaynak taşıma için kalan aşamalar
1. Mola 3.2'nin kaynak sürümü ve imzası geliştiricinin yayımladığı
   doğrulanabilir kaynak/release ile eşleştirilmeli.
2. Mola'nın lisanslı TV/oynatıcı kodları, API ve Gradle bağımlılıkları
   analiz edilip mevcut Kotlin uygulamamızın mimarisiyle bütünleştirilmeli.
   Kaynak kod referansı kopyalanırken GPL şartlarına uyulmalı.
3. Aynı APK içinde Android Auto için gerekli servisler ve ayrı telefon
   faaliyetleri çakışmayacak şekilde yeniden düzenlenmeli. Testler
   gerçek cihazda yapılmalı.
4. POCO F5 Android 15 ve Volkswagen ID.4 üzerinde, park halinde
   yayın, ekran izni, geri dönüş, kopma, ses ve hata senaryoları sınanmalı.
5. APK izinleri, internet hedefleri, paket imzası ve güncelleme
   güvenlik denetimleri yeni birleşik çıktıda yeniden doğrulanmalı.

## Aşama farkı
**Şu anda:** Mola ayrı uygulama olarak açılır, medya bağlantısı aktarılır.
**Hedef:** Kaynak kodu doğrulanmış Mola oynatma/Android Auto özellikleri
ID.4 Yol Arkadaşım'ın içinden çalışır. Hedefin gerçekleştiği henüz
iddia edilmez.

## Telefon denemesi
1. Mola 3.2 kuruluysa ID.4 Yol Arkadaşım → Kitaplık → Mola 3.2 aç.
2. TV sekmesinde Mola'ya tek kanal aktarımını dene.
3. Radyo sekmesinde seçilen radyoyu Mola'ya göndermeyi dene.
4. Gerçek ID.4 ekranında video gösterimi ayrıca sınanmalıdır.

Derleme testi ve fiziksel araç testi **yapılmadı**.
