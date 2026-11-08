# AnimeRoll for Android

Jetpack Compose app (phone, tablet and Android TV launcher) for [AnimeRoll Server](https://github.com/KuroZetsubou/AnimeRoll-Server).
Media3 / ExoPlayer playback with ASS subtitles rendered by libass, multi-track audio, offline downloads,
passkeys through Credential Manager.

## Build

Requirements: JDK 17+ and the Android SDK (API 36). minSdk is 28.

```bash
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

The application id is `com.kurozetsubou.animeroll`; change `namespace` / `applicationId` in
`app/build.gradle.kts` if you publish your own build.

On first launch enter the address of your server (`https://your-domain`) and sign in.

## Passkeys

Passkeys need your server on HTTPS and the signing certificate of the app registered on it:

- `AnimeRoll__Android__Sha256CertFingerprints__0`: SHA-256 fingerprint of the signing certificate
  (`keytool -list -v -keystore <keystore>`); the server publishes it at `/.well-known/assetlinks.json`.
- `AnimeRoll__Passkey__Origins__N`: `android:apk-key-hash:<base64url hash>` of the same certificate.

Use a release keystore of your own for distribution and never commit it.

## License

© 2026 KuroZetsubou. Licensed under [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International](LICENSE)
(CC BY-NC-SA 4.0): you may share and adapt it for non-commercial purposes, with credit, and you must distribute your
changes under the same license. It is provided "as is", without warranty, and no patent rights are granted.
This is a source-available license, not an OSI-approved open source license.
