# ID.4 Yol Arkadaşım — resmî Google / Yandex trafik sağlayıcıları

Bu dalda Mola'nın orijinal medya modüllerine dokunmadan **telefon kokpitinde**
kullanılabilecek bir harita sağlayıcı seçicisi bulunur. `Katmanlar → Trafik`
menüsünden `Google Trafik`, `Yandex Trafik` veya `OSM haritası` seçilir.

## Mevcut durum ve sınırlar

- **Anahtarlar tanımlanmadıysa** Google/Yandex SDK'ları başlatılmaz; OSM
  çalışmaya devam eder. Trafik yoğunluğu, olayları veya hız gecikmesi asla
  tahmin edilip canlı veri olarak gösterilmez.
- Google Maps JavaScript API `TrafficLayer` ile yalnız Google'ın harita
  tabanında trafik gösterilir. Yandex JavaScript API 2.1
  `traffic.provider.Actual` yalnız Yandex harita tabanında çalışır.
  **Sağlayıcıların trafik verileri OSM üzerine kopyalanmaz.**
- Var olan şeffaf araç kokpiti, OSRM güzergâh çizgisi ve yüklenen CCS
  konumları harita değişirken korunur. GPS doğru/güncelse araç konum
  işareti taşınır. OSM haritasına tek dokunuşla dönülebilir.
- Resmî SDK'nın harita veri kapsamı ve erişim izinleri sağlanmış olsa bile
  **rota ETA'sı hâlâ OSRM trafiksiz tahminidir**. Google Routes
  `TRAFFIC_AWARE_OPTIMAL` veya lisanslı Yandex yönlendirme API'si
  ayrıca entegre edilmeden 'trafik nedeniyle +12 dk' gibi bir sayı üretilmez.
- Trafik haritasının araçtaki **Android Auto** görünümü doğrulanmış
  değildir. Android Auto için ayrı, uyumlu Car App / native harita çözümü
  gerekir. Bu sürüm telefonun yatay/dikey Activity/WebView kokpitidir.

## Geliştirici olarak etkinleştirme (isteğe bağlı)

1. [Google Cloud Console](https://console.cloud.google.com/) üzerinde
   **Maps JavaScript API** etkinleştir, ödeme hesabını ve uygun bütçe
   uyarılarını tanımla; yalnız bu API için yetkilendirilmiş **web/API
   kısıtlamalı** istemci anahtarı kullan.
2. [Yandex Maps API Developer Dashboard](https://yandex.com.tr/maps-api/)
   üzerinden Maps JavaScript API lisansını, trafik katmanı yetkisini ve
   gerekiyorsa ödeme koşullarını doğrula. JavaScript v2.1 bu dalın SDK
   adaptörüdür; üretime geçmeden API sürümü/lisansı yeniden kontrol edilmeli.
3. GitHub projesi → **Settings → Secrets and variables → Actions** içinde
   gerektiği takdirde
   `ID4_GOOGLE_MAPS_JS_KEY` ve/veya `ID4_YANDEX_MAPS_JS_KEY`
   repository secret oluştur. İkisini birden zorunlu kılmıyoruz.
4. **WebView referer/origin kısıtını** doğrula:
   WebViewAssetLoader sayfa kaynağı
   `https://appassets.androidplatform.net/assets/id4/navigator.html`
   olarak görünür; Google web anahtarının izinli web sitesi ayarı ve
   Yandex Referer kontrolü gerçek cihazda doğrulanmalıdır.
   Bu durumda anahtar çalışmazsa *anahtar kısıtını kapatıp dağıtmak*
   yerine native Google Maps SDK/Yandex MapKit'e geç.
5. Yeni Actions APK derlemesi başarılı olduğunda gerçek cihazda
   sağlayıcıya geçiş, trafik yoğunluğunun gerçekten gösterilmesi,
   telif/attribution görünürlüğü, rota/şarj noktaları ve OSM'ye geri
   dönüşü test et.

**Önemli güvenlik:** JavaScript SDK anahtarları APK içinde istemci tarafından
okunabilir; 'secret' ortam değişkeni yalnız GitHub deposuna commit etmemeyi
sağlar. Dağıtılan anahtarı gizli parola sanma. Sağlayıcı önerilerine göre
izinli origin / web sitesi, API kapsamı, kota ve harcama sınırları uygula.
Üretimde Android uygulama imzasına bağlanmış *native* harita SDK'sı
tercih edilmeli. Google'ın Android paket+SHA kısıtlı anahtarı doğrudan
Maps JavaScript API için kullanılamaz.

## Doğrulama

`node mola-native/tests/test_traffic_provider.js` testi anahtar yokken
haritayı korumayı, Google resmî `TrafficLayer`, Yandex resmî
`traffic.provider.Actual`, rota çizimi, GPS konumu ve OSM'ye
dönüşü mock SDK'larla sınar. Bu testler üretim API anahtarının
Türkiye'de canlı trafik yüklediğinin **kanıtı değildir**.

## Kaynaklar

- [Google Maps JavaScript TrafficLayer](https://developers.google.com/maps/documentation/javascript/trafficlayer)
- [Google Maps JavaScript API — kullanım ve faturalandırma](https://developers.google.com/maps/documentation/javascript/usage-and-billing)
- [Google anahtar kısıtlama ve WebView güvenliği](https://developers.google.com/maps/api-security-best-practices)
- [Yandex Maps 2.1 gerçek trafik sağlayıcısı](https://yandex.com/dev/jsapi-v2-1/doc/en/v2-1/examples/cases/traffic_provider)
- [Yandex JavaScript API trafik kontrolü](https://yandex.com/dev/jsapi-v2-1/doc/en/v2-1/examples/cases/traffic_control)
- [Yandex WebView/Referer kısıtları](https://yandex.com/maps-api/docs/js-api/limit.html)
- [Google Routes traffic-aware polylines](https://developers.google.com/maps/documentation/routes/traffic_on_polylines)
