# TomTom API Anahtarı Güvenliği

## Durum (26 Ekim 2026)

**Eski sürümler için inceleme gerekir.** `build-mola-native-id4.yml` daha önce `secrets.ID4_TOMTOM_API_KEY` değerini derleme adımına iletiyordu ve `patch_mola.py` bunu `assets/id4/traffic-config.js` içine yazabiliyordu. GitHub Actions artifact'ları indirilebilir olduğundan, ilgili secret tanımlıysa eski APK'dan çıkarılmış olabilir.

**Yeni çözüm:** Paylaşılan anahtar build ortamına verilmez. `patch_mola.py` `traffic-config.js` içine daima boş anahtar yazar, eski `ID4_TOMTOM_API_KEY` ortam değişkeni doluysa build'i reddeder. GitHub Actions final APK'daki config dosyasını doğrular.

## Anahtar şifreli saklama (26 Ekim 2026)

Kullanıcının yüklediği `Id4ApiKeyStore.java` temel alınarak Android Keystore'da AES-256-GCM ile saklama eklendi. Android 23 altındaki cihazlarda **düz metin/Base64 geri dönüş yolu yoktur**; anahtar kaydı güvenli değilse başarısız olur.

Mevcut `Id4NavigatorActivity` yerel Android anahtar penceresi korunmuştur. Eski `id4_traffic_keys/tomtom` tercihi uygulama ilk kez anahtara ihtiyaç duyduğunda **bir defalık** şifreli depoya aktarılır ve düz metin kopyası silinir. Keystore anahtarı sıfırlanır veya bozulursa kullanıcı anahtarı yeniden girmek zorundadır.

Yüklenen `Id4KeyBridge.java` ve `id4-tomtom-key.js` bu sürüme **bilerek eklenmedi**. `getTomTomKey()` gibi bir `@JavascriptInterface` çağrısı bütün WebView frame'lerine erişilebilir olabileceği için gereksiz erişim alanı yaratır. TomTom katmanını kullanan mevcut JavaScript, anahtarı yalnızca yerel Android kodundan sayfa yüklenince alır; anahtarın trafik URL'sinde görünmesi ise ayrı ve devam eden bir risktir. Bu çözüm yalnızca **cihazda saklama güvenliğini** artırır; tarayıcı çalışma zamanında mutlak gizlilik sağlamaz.

## Uygulamada kişisel anahtar

1. Kullanıcı Elektrikli Yol Arkadaşım içinde **Harita servis anahtarı** ekranını açar.
2. Kendisine ait TomTom API anahtarını girer.
3. Anahtar Android'in uygulamaya özel `SharedPreferences` alanında tutulur; WebView'a gerektiğinde çalışma sırasında aktarılır. Tarayıcı kaynak isteklerinde anahtar görülebilir: bu, tam gizleme değildir.
4. Anahtar asla GitHub deposuna, `BuildConfig` içine veya paylaşılan APK varlıklarına yazılmaz.

Tek anahtarı herkesle paylaşmak yerine, dağıtılan uygulamada **her kullanıcı kendi anahtarını** kullanmalıdır. Çok kullanıcılı güvenilir dağıtım için hız sınırı, kimlik doğrulama ve maliyet kontrolü olan sunucu proxy'si ayrıca değerlendirilebilir.

## Eski sürümler için yapılacaklar

- TomTom Developer panelinde eski anahtarı **yenile / iptal et**. Bu işlemi ChatGPT doğrudan gerçekleştiremez.
- Kullanım miktarlarını ve kota/harcama sınırlarını denetle.
- Önceden yayımlanmış APK/Actions artifact'larını kontrol et. Eski anahtar çıkarsa ele geçirilmiş kabul et.
- Git geçmişini bir secret tarayıcısıyla tara ve şüpheli sonuçları doğrula. Geçmiş temizliği gerekecekse ayrı bir yedek ve plan kullan; doğrudan force-push yapma.
- Repository Settings → Code security üzerinden secret scanning ve push protection özelliklerini, hesap/plan sunuyorsa, aç.
- Genel Action izinlerini salt-okunur tut ve `pull_request_target` ile güvenilmeyen PR koduna secret verme.

## APK paket denetimi

```bash
python3 - <<'PY'
from zipfile import ZipFile
from pathlib import Path

apk=next(Path('mola/dist').glob('*.apk'))
with ZipFile(apk) as z:
    config=z.read('assets/id4/traffic-config.js').decode('utf-8')
    assert config.strip() == 'window.ID4_TRAFFIC_CONFIG={tomtomKey:""};'
print('PASS: APK traffic-config.js has no embedded TomTom key')
PY
```

Bu test, yalnızca belirlenen config dosyasına anahtar gömülmediğini doğrular; eski APK'ların güvenli olduğunu veya bütün ikili içeriğin tarandığını kanıtlamaz. Özellikle eski build'leri ayrıca inceleyin.
