# XqTvApp - Stalker Portal IPTV Player (Kotlin)

Profesyonel Stalker/Minist portal player. Portal URL + MAC ile giriş yapar, handshake olur, kanal listesini çeker, ExoPlayer ile oynatır.

## Kurulum
1. Bu repoyu GitHub'a pushla
2. Actions sekmesi > Build APK > Run workflow
3. Artifact olarak `xqtvapp-debug-apk` indir

## Kullanım
- Portal URL: `http://host:port/c/` formatında gir (sonunda / olsun)
- MAC: `00:1A:79:XX:XX:XX` formatında gir
- Bağlan > kanal seç > oynat

## Güvenlik
- MAC ve portal cihaza DataStore ile kaydedilir, koda gömülmez.
- Token asla repoya yazılmaz.

## Teknik
Kotlin, Compose Material3, Media3 ExoPlayer, OkHttp, Coil, DataStore, Navigation.
MinSdk 24, Target 34.
