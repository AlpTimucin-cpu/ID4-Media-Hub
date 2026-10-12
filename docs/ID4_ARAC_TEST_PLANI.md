# ID.4 Media Hub v0.25 — Araç Üzerinde Doğrulama Planı

**Hedef:** 2024 VW ID.4 Pure + kablosuz Android Auto + POCO F5 Android 15. Ek cihaz veya root gerektirmez.

## v0.25 — Kanal rehberi kullanımı

1. Kitaplık → M3U listesini içe aktar. Başlıkta XMLTV kaynak adresi varsa **M3U program rehberi bulundu** bildirimi çıkmalı; onay vermeden ekstra ağ isteği yapılmamalı.
2. Kaynak kendi rehberini bildirmiyorsa Kitaplık → TV Program Rehberi bölümüne HTTPS XMLTV/XMLTV.GZ adresini elle gir.
3. TV → Kanallar → 📺 Şimdi yayında kategorisi: sadece geçerli program eşlemesi olan kanalları göster.
4. Program başlığının yanı sıra önce/sonra ve ilerleme çubuğunu kontrol et. Arama kutusuna program başlığını yaz.
5. Uygulama ekranı, video akışı ve Android Auto radyo sesini birbirinden bağımsız doğrula. Gerçek ID.4 ekranında video çalıştığı iddia edilmez.

## v0.24 — Ortak Entegrasyon Merkezi

1. Media Hub → Ana Sayfa → **Tüm uygulamaları ve bağlantıları yönet** ekranına gir.
2. Her kaynak için **Dahili**, **Harici Bağlantı**, **Durum Kontrolü** veya **Teknik Referans** sınıfını incele.
3. Kurulu olan AABrowser/Fermata/Screen2Auto/ScreenOnAuto sürümlerinin durumunu kontrol et; bulunmayanlar için yeni kurulum gerekmez.
4. **Raporu kopyala**: içinde yayın adresi, kullanıcı adı, şifre ve API anahtarı olmamalı.
5. Aracın ekranını park halinde test et; uygulamanın telefonda başlatılmasını gerçek ID.4 videosu olarak değerlendirme.

## Önceden doğrulananlar (tekrar kurma)

- Telefonda Shizuku **önceden kurulup ayarlanmış**; tekrar kurulmasına gerek yok.
- AABrowser **ID.4 büyük ekranda** YouTube sayfasını açtı.
- Netflix web sitesi açıldı; Netflix videosu oynatılmadı.
- ID.4 Media Hub'ın **Web Medya** bölümü Android 15 emülatöründe çalıştı; bu, araç ekranı başarısı değildir.

## Yardımcı uygulamalar (POCO F5)

- **Kitaplık → Shizuku / AAAD / Fermata durumunu kontrol et**: zaten kurulu Shizuku'nun API durumunu ve AAAD/Fermata sürümlerini görüntüle. Shizuku'yu yeniden kurma.
- Fermata kuruluysa **Fermata'yı aç** düğmesi telefondaki uygulamayı açmalı. Bu işlem Android Auto aracında video görünürlüğü anlamına gelmez.
- İzin isteme, APK yükleme, hareket/sürüş sinyalini değiştirme veya özel sürüş kısıtlamalarını atlatma yapılmaz.
- Gerçek araç ekranı, radyo ses testi ve park halindeki video denemeleri birbirinden ayrı raporlanır.

## APTV benzeri IPTV ve ScreenOnAuto (yalnızca park halinde)

1. ID.4 Media Hub → Kitaplık → TV Program Rehberi bölümüne kullanıcıya ait HTTPS XMLTV ya da XMLTV.GZ kaynak adresi ekle.
2. EPG yüklendiğinde TV kanalının M3U tvg-id alanının XMLTV channel id ile eşleştiğini kontrol et; TV oynatıcısı Şimdi / Sonra satırını göstermeli.
3. Kayıtlı kanalların M3U yedeğini dışa aktar; tvg-id bilgisinin korunduğunu kontrol et.
4. ScreenOnAuto telefonda resmî Android 15 kurulumuyla yüklüyse Kitaplık'tan Başlat ve Durdur komutlarını yalnızca araç park halindeyken dene.
5. Ekran yansıtma açılmazsa raporda kurulu paket durumu incelenir. Komutun gönderilmesi Android Auto ekranında video oynadığını kanıtlamaz.
6. APTV sadece Apple/CarPlay içindir; bu Android uygulamasında APTV bağlantısı veya kendi CarPlay kodunun kullanımı yoktur.

## Park halinde gerçek araç testi

1. Araç güvenli şekilde **park halinde** olsun.
2. Telefonu ID.4'ün **kendi kablosuz Android Auto** bağlantısına bağla.
3. ID.4 ekranındaki **Android Auto uygulama listesini** aç. ID.4 Media Hub'ın (müzik/radyo) simgesi var mı, not et.
4. Görünüyorsa Media Hub'ın **Tüm radyolar** klasörünü aç; bir yayını seçerek **sesin araç hoparlörlerine aktarılıp aktarılmadığını** kontrol et.
5. Telefonda ID.4 Media Hub → **Kitaplık → Araç / Android Auto bağlantı raporu** menüsünü aç.
6. Raporun **Android Auto kitaplık isteği** satırına bak. Artık zaman damgası görünüyorsa Android Auto radyo kitaplığımıza bir istek gönderdi. **Bu, video desteğinin kanıtı değildir.**
7. Raporun **Android Auto**, **Android WebView**, **Widevine**, **AABrowser** ve **Shizuku** sürümlerini kaydet. Gerekirse raporu **kopyala**.
8. Telefon içindeki **Kitaplık → Ses kitaplığı API bağlantısını kontrol et** testinde hem Media3 hem eski Android MediaBrowser satırını incele. İkisinin de çalışması **telefon** API doğrulamasıdır.
9. **Web Medya → YouTube Web** bölümünü aç ve alt bölümdeki **🚘 AABrowser** düğmesine dokun. ID.4 ekranındaki AABrowser sekmesi aynı URL'yi açıyor mu, kontrol et.
10. Sayfa sadece telefonda açılırsa bunu ayrı sonuç olarak kaydet. AABrowser'ın bağlantıyı alması **araç ekranında video** kanıtı değildir.
11. Araç ekranındaki **uygulama listesinin görüntüsü** ile bağlantı raporunu paylaş. Hesap veya yayın aboneliği bilgilerini paylaşma.

> Video aktarımı yalnızca araç **park halindeyken** denenmelidir. Bu test Shizuku, AAAD veya Android Auto ayarlarının yeniden kurulmasını gerektirmez.

## Olası sonuçları ayırma

| Sonuç | Anlamı | Sonraki adım |
| --- | --- | --- |
| Media Hub AA listesinde var, radyolar açılıyor | Standart sürüş güvenli Android Auto ses hizmeti çalışıyor | IPTV ve web oynatıcısının telefon tarafında geliştirilmesi; park-video entegrasyonu ayrı araştırma |
| Simgesi yok | AA discovery/package/install yöntemi sorunu olabilir | Android Auto sürümü, uygulama listesi ve APK imzasını karşılaştır |
| Simgesi var, kitaplık boş | MediaLibraryService veya katalog sorunu | Bağlantı raporu, ses servisi/istek zamanları ve katalog içeriğini denetle |
| Ses var ancak araçta video yok | Desteklenen Android Auto ses sınıfında normal | Ses ve park video mimarilerini birbirine karıştırma |
| AABrowser görüntü veriyor ancak Media Hub yalnızca ses | AABrowser'ın mevcut araç entegrasyonu farklı | Onaylı park video API'leri ile cihaz uyumluluğunu yeniden değerlendir; sahte kategori veya sürüş kısıtı atlatma yok |
| Web bağlantısı AABrowser'a gönderiliyor ama ID.4 ekranı değişmiyor | Telefon `ACTION_VIEW` isteği araçtaki sekmeyi güncellememiş olabilir | AABrowser'ın yeni sekme/aktif ekran davranışı araçta doğrulanmalı |
| AABrowser araçta yeni HTTPS sayfasını açıyor | Yalnızca sayfa/URL aktarımı doğrulanır | Ayrı bir izinli video örneğinin park halinde oynayıp oynamadığını kontrol et |

## Sınırlar

- Android Auto'da bu uygulamanın video görüntüsünü oynattığı **henüz doğrulanmadı**.
- AABrowser'ın açık kaynak projesindeki car-launcher/navigasyon/oyun ilanları bu medya uygulamasına olduğu gibi kopyalanmıyor.
- Aracın sürüş güvenliği kilitleri devre dışı bırakılmıyor; araç hareket ederken ön ekranda video gösterme hedeflenmiyor.
- Widevine seviyesi Netflix'in site lisansı/DRM politikasını aşmaz; SportzX üçüncü taraf APK'sının akışı uygulamaya otomatik gömülmez.

## Geliştirici notu

`CarConnectionJournal` yalnızca medya kitaplığı erişim türünü ve zamanını saklar; kanal URL, cihaz kimliği veya token saklamaz. `CarCompatibility` raporu anlık Android Auto sürümü ve Widevine/WebView tanılaması sağlar.
