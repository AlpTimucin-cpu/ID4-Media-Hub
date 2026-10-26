# Elektrikli Yol Arkadaşım

**Elektrikli araçlar için rota, şarj planlaması, canlı trafik katmanı, güvenilir telemetri ve medya.**

Türkiye'deki elektrikli yolculuklar için geliştirilen bağımsız Android uygulaması. Koyu lacivert, turkuaz ve elektrik yeşili arayüzle tek ekranda güzergâh, batarya ölçümleri, şarj durakları ve yol üzeri hava durumunu bir araya getirir.

> **Geliştirme sürümüdür.** Aşağıdaki ekran görüntüleri yazılımın HTML/CSS tarayıcı önizlemeleridir; fiziksel araç testi veya doğrulanmış canlı trafik anlamına gelmez. Kullanıcıya gösterilen sayılar gerçek kaynaklardan gelmedikçe ölçüm gibi gösterilmez.

## Uygulama önizlemesi

![Elektrikli Yol Arkadaşım — Kokpit](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/raw/refs/heads/feature/mola-native-id4/docs/screenshots/cockpit-browser-preview.png)

| Telefon rota görünümü | Kompakt yatay kokpit |
|---|---|
| ![Telefon görünümü](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/raw/refs/heads/feature/mola-native-id4/docs/screenshots/route-phone-browser-preview.png) | ![Yatay görünüm](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/raw/refs/heads/feature/mola-native-id4/docs/screenshots/cockpit-compact-browser-preview.png) |

## Özellikler

| Bölüm | İşlev |
|---|---|
| **Rota** | Açık yol geometrisine dayalı gerçek güzergâh, alternatif yollar ve tahmini süre |
| **Şarj** | Türkiye istasyonları, AC/DC türü, güzergâh üzeri durak adayları ve model maliyeti |
| **Canlı trafik** | Kullanıcının kendi servis anahtarıyla çalışan trafik çizgileri; başarısızsa açık hata durumu |
| **Araç verileri** | Doğrulanmış, taze OBD verileri; ölçüm yoksa boş değer |
| **Yolculuk** | Gerçek GPS ile rota ilerlemesi ve sürüş odaklı arayüz |
| **Medya** | Açık kaynak medya çekirdeği ve resmî servis yönlendirmeleri; hareket/güvenlik kısıtları korunur |

## APK ve teknik belgeler

**[En yeni APK derleme ve test sonucunu aç](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/actions/workflows/build-mola-native-id4.yml)** → başarılı çalışmanın **Artifacts** bölümünden `Elektrikli-Yol-Arkadasim-APK` ZIP dosyasını indirip içindeki APK'yı kur.

En güncel Android entegrasyon kodu ve testler [**aktif geliştirme dalında**](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/tree/feature/mola-native-id4) tutulur. [Harita, OBD ve gerçek GPS entegrasyon rehberi](https://github.com/AlpTimucin-cpu/ID4-Media-Hub/blob/feature/mola-native-id4/docs/ELEKTRIKLI_YOL_ARKADASIM_ENTEGRASYON.md) kaynak uygulamaya özelleştirilmiştir.

**Telif ve lisanslar:** Uygulamanın açık kaynak medya tabanı ve üçüncü taraf bileşenleri için [lisans](LICENSE), [attribution](NOTICE.md) ve [güvenlik notları](SECURITY_TOMTOM.md) geçerlidir. Telif ve kaynak proje bilgileri ürünün görünen marka adından ayrı olarak korunur.

**Dağıtım notu:** Kaynak kodun kamusal görünürlüğü, fiziksel araç kullanımı veya üçüncü taraf servislerin ticari şartları açısından ayrıca değerlendirilmelidir.
