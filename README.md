# Dizi

Kişisel dizi takip uygulaması (Android 8+). Yeni sezon duyurulunca ve yeni sezon başladığı gün bildirim gönderir.

## Kurulum
1. GitHub Releases'tan son `Dizi.apk`'yı indirip kur.
2. TMDB token CI secret'ından (`TMDB_TOKEN`) APK'ya gömülü; Ayarlar'daki alan yalnız değiştirmek için.
3. İstersen Serializd kullanıcı adını yaz → **Serializd'den içe aktar** (izlenen + izlenmekte olan diziler, profil herkese açık olmalı).

## Nasıl çalışır
- Liste telefonda saklanır. Dizi eklemek için `+`, silmek için satırı kaydır.
- Günde bir kez arka planda TMDB'den her dizinin sezon sayısı ve sıradaki bölümü kontrol edilir. ↻ ile hemen kontrol edilir.
- Serializd içe aktarma resmi olmayan bir API kullanır (`serializd.onrender.com`); bozulursa diziler `+` ile elle eklenebilir.

## Derleme
Yerelde JDK/SDK gerekmez: `main`'e her push'ta GitHub Actions test + lint + imzalı APK üretip Release yayınlar.
İmza anahtarı `.local/` altındadır (git'e girmez), secret'lar: `DIZI_KEYSTORE_BASE64`, `DIZI_KEYSTORE_PASSWORD`.
