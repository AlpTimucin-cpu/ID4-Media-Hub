# Elektrikli Yol Arkadaşım

Elektrikli araç yolculukları için geliştirilen bağımsız Android rota, şarj, medya ve telemetri uygulaması.

**Güncel durum:** Aktif geliştirme dalında APK GitHub Actions üzerinden oluşturulur. Başarılı CI testi, araç ekranında fiziksel test veya trafik/OBD bağlantısının gerçek cihazda doğrulandığı anlamına gelmez.

## Öne çıkanlar

- **Rota ve harita:** Tek Leaflet haritası, gerçek OSRM yol geometrisi, alternatif rota seçimi ve trafiksiz süre uyarısı.
- **Batarya ve enerji:** Geçerli, taze OBD verileri; veri yoksa tahminin/varsayımın açıkça işaretlenmesi.
- **GPS ilerleme:** Son 30 saniyeye ait güvenilir konumdan rota izdüşümü; araç simgesi GPS yokken gizlenir.
- **Şarj istasyonları:** Türkiye'de AC/DC istasyonları ve rota boyunca şarj durağı adayları.
- **Canlı trafik:** Kullanıcıya ait API anahtarıyla akış katmanı; doğrulanmamış veriyi canlı diye göstermez.
- **Medya:** Var olan medya çekirdeği ve resmî servis bağlantıları korunur; sürüş güvenliği ve telif kısıtları değiştirilmez.

## Kurulum ve geliştirici belgeleri

[**En yeni APK derlemesini görüntüle**](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/actions/workflows/build-mola-native-id4.yml)

[**Aktif geliştirme dalını aç**](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/tree/feature/mola-native-id4)

[**Gerçek rota, Android/OBD ve GPS entegrasyon kılavuzu**](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/blob/feature/mola-native-id4/docs/ELEKTRIKLI_YOL_ARKADASIM_ENTEGRASYON.md)

## Uyumluluk ve lisans

Bu proje üçüncü taraf resmî bir araç veya medya uygulaması değildir. Bağımsız marka **Elektrikli Yol Arkadaşım** olarak kullanılır. Gerekli açık kaynak ve veri atıfları [LICENSE](LICENSE), [NOTICE.md](NOTICE.md) ve [SECURITY_TOMTOM.md](SECURITY_TOMTOM.md) belgelerinde korunur.

Geriye dönük uyumluluk için bazı teknik paket/dosya adları hâlâ eski iç kimlikleri taşır; bunlar kullanıcıya sunulan ürünün adı değildir.
