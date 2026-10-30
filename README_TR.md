# Elektrikli Yol Arkadaşım — Türkçe Proje Rehberi

**Elektrikli sürüşe uygun rota, şarj, medya ve araç verileri.**

Bu sayfa güncel uygulamanın Türkçe giriş belgesidir. Kapsam, kullanım sınırları, kaynak koddan üretilen ekran önizlemeleri ve APK kurulum bilgileri için [ana README](README.md) dosyasını kullan.

## Temel kullanım

Uygulamayı açıp **Rota** bölümünde başlangıç ve hedefi seç. OSRM yolu çizilir; alternatifler varsa listelenir. Haritada gösterilen hava tahminleri model verisidir ve yalnız doğrulanmış kaynaklardan alınır.

**İstasyon** bölümünde istasyonları soket türüne göre inceleyebilir, güzergâh yakınındaki duraklara bakabilirsin. Güncel istasyon doluluğu veya fiyatı yetkili veri servisiyle doğrulanmadıkça kesin değer olarak gösterilmez.

**Araç Verileri** bölümündeki SoC, sıcaklık, voltaj ve diğer göstergeler yalnız gerçekten alınmış, kalibrasyonu doğrulanmış ve taze OBD verisine dayanır. Eşleşmiş Bluetooth adaptörü tek başına bu ölçümlerin mevcut olduğunu kanıtlamaz; ölçüm yoksa gösterge boş kalır.

**Yol ilerlemesi**, son 30 saniyede alınmış ve doğruluğu uygun GPS noktasının hesaplanan gerçek rotaya mesafesinden hesaplanır. Yol dışı veya eski GPS ile uydurma yüzde üretilmez. İlerleme çubuğundaki araç sembolü bileşen sınırları içinde kalır.

## Güvenlik

Araç hareket halindeyken sürücüyü oyalayan video veya tarayıcı içeriği açmaya yönelik kısıtlar kaldırılmaz. Harici medya servisleri kendi abonelik, DRM ve kullanım koşullarını uygular. Servis anahtarlarını kaynak koda ekleme.

## Geliştirici notları

[Harita, OBD ve GPS entegrasyonu](docs/ELEKTRIKLI_YOL_ARKADASIM_ENTEGRASYON.md) kılavuzun projeye uygulanmış mimarisini açıklar. Örnek koddaki sabit hız, batarya veya menzil rakamları canlı ölçüm değildir ve kullanılmaz.

Üçüncü taraf lisansları ve telif atıfları [LICENSE](LICENSE) ve [NOTICE.md](NOTICE.md) belgelerinde korunur. Güncel sürümün gerçek cihazda test edilmesi gereklidir.
