# v0.8 doğrulama raporu — 9 Ekim 2026

- **Kaynak XML ve Media3 mimari kontrolleri:** geçti (`python tests/architecture_checks.py`).
- **Çevrimdışı Kotlin/JVM testleri:** M3uParser, M3uExporter, RadioQueuePlanner, StreamProbe, TrustedSources, LibraryPage geçti.
- **Gradle'a eklenen dört JUnit yöntemi:** Kotlin kaynakları test amaçlı minimal JUnit API ile derlendi ve yöntemler çalıştırıldığında geçti. Bu, gerçek Android Gradle testi yerine geçmez.
- **Android Studio/Gradle assembleDebug:** çalıştırılamadı. Bu ortamda Android SDK ve Gradle yok, indirme sunucularının DNS çözümlemesi başarısız.
- **APK dosyası:** üretilmedi. Sahte/placeholder APK yok.
- **POCO F5 üzerinde kurulum ve ID.4 üzerinde Android Auto:** test edilmedi.
- **GitHub Actions:** derleme ve APK + SHA256 artifakt iş akışı yazıldı, gerçek GitHub ortamında henüz çalıştırılmadı.

Resmî sürüm referansları: https://developer.android.com/jetpack/androidx/releases/media3 , https://developer.android.com/build/releases/agp-8-11-0-release-notes , https://developer.android.com/media/media3/session/serve-content
