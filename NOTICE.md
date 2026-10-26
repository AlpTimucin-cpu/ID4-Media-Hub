# Üçüncü Taraf Bildirimleri (NOTICE)

**ID.4 Yol Arkadaşım** bağımsız bir deney projesidir. Volkswagen, Mola, Google, TomTom, OpenStreetMap Foundation veya burada adı geçen diğer kuruluşlar tarafından onaylanmamıştır; markalar ilgili sahiplerine aittir.

Bu projenin Mola/Fermata 2.2.2 tabanından derlenen **ID.4 önizleme APK'sı**, pinlenmiş GPL-3.0 açık kaynak tabanından türetilir. Türev kaynak ve karşılık gelen değişiklikler GPL-3.0 yükümlülüklerine tabidir (bkz. [LICENSE](LICENSE)). Depodaki bağımsız medya geçmişi ve üçüncü taraf bağımlılıklar ayrı telif/lisans hükümleri içerebilir; mevcut telif sahipliği bu bildirimle değiştirilmez.

> Bu tablo bilgilendirme amaçlıdır, hukuki danışmanlık değildir. `[doğrula]` işaretli satırları kendi kaynağında/şartlarında kontrol et; sürümler ve şartlar zamanla değişir.

| Bileşen / veri | Kullanım | Lisans / şart | Yapman gereken |
|---|---|---|---|
| Fermata / Mola 2.2.2 (açık kaynak) | Taban medya uygulaması | GPL-3.0, pinli commit `3f45ad3557a25822c2a3a9f152f32ef82f57e005` LICENSE dosyasında doğrulandı | Repoda `LICENSE`; APK dağıtırken karşılık gelen tam kaynak kodunu erişilebilir tut, değişiklikleri belirt |
| OpenStreetMap verisi | Harita, hız kamerası noktaları | ODbL 1.0 | Haritada "© OpenStreetMap contributors" görünsün; lisans bağlantısını koru |
| OSM Foundation karo sunucusu | Harita karoları | [Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/) | User-Agent/Referer, toplu indirme/önbellek doldurma yok |
| OSM France karoları | Yedek karo kaynağı | [Kullanım şartları](https://www.openstreetmap.fr/usage/) | Koşullara uygun ve sınırlı kullan |
| TomTom Orbis | Trafik, harita | TomTom API ve harita şartları; kullanıcıya özel anahtar | "© TomTom" atfı `[doğrula]`; anahtarı repoya veya APK'ya gömme; bkz. [SECURITY_TOMTOM.md](SECURITY_TOMTOM.md) |
| Natural Earth 110m | Çevrimdışı ülke sınırları | Kamu malı | Nezaketen atıf yapılabilir |
| Türkiye il merkezleri verisi | Çevrimdışı yedek harita | MIT (proje README bilgisi) `[doğrula]` | Kaynak ve lisans metnini dosyanın yanında tut |
| Leaflet | Harita kütüphanesi | BSD-2-Clause | Dağıtılan kopyada lisans metni kalsın |
| MapLibre GL ve Leaflet adaptörü | Vektör harita | Dağıtılan varlık lisans dosyaları | `MAPLIBRE_LICENSE.txt` ve `MAPLIBRE_LEAFLET_LICENSE.txt` paketlemeyi koru |
| OSRM | Rota hesabı | Yazılım BSD-2-Clause; demo sunucunun ayrıca kullanım politikası var | Yoğun kullanımda ayrı sunucu |
| Photon | Adres arama | Yazılım Apache-2.0; veri OSM/ODbL | Genel sunucu politikasına uy |
| Open-Meteo | Hava tahmini | Veri CC BY 4.0; ücretsiz API ticari olmayan kullanıma bağlı olabilir `[doğrula]` | Atıf ve güncel şartlar |
| EPDK istasyon kaydı | Şarj istasyonu listesi | Kamuya açık veri, yeniden kullanım şartları `[doğrula]` | Snapshot tarihini ve veri kaynağını göster |

## Kapsam dışında bırakılanlar

Bu proje üçüncü taraf ticari uygulamaların (ör. radar/uyarı uygulamaları) veritabanlarını, premium haklarını, sunucu erişimini, markalarını veya kodunu içermez ve içermemelidir.

Ayrıntılı geçmiş medya bağımlılıkları için [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) belgesine de bakın.
