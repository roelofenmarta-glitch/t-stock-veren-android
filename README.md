# T-Stock Veren V10.2 Android

Native Android-app voor **T-Stock Magazijnbeheer Veren V10.2**.

## Functies

- Native Jetpack Compose-interface, geen WebView als hoofdscherm.
- Barcode- en QR-scanner.
- Inboeken met V10 locatieadvies en locatiecontrole.
- Verplaatsen, uitboeken en voorraad zoeken.
- SQLite-cache en offline mutatiewachtrij.
- Synchronisatie met de V10.2-server.
- Updatecontrole en APK-download vanaf de eigen server.

## GitHub

Upload de inhoud van deze map rechtstreeks naar de hoofdmap van een GitHub-repository. De structuur moet zijn:

```text
.github/workflows/android-apk.yml
android/settings.gradle.kts
android/build.gradle.kts
android/app/build.gradle.kts
```

Start daarna:

`Actions → Android APK V10.2 → Run workflow`

De artifactnaam is `T-Stock-Veren-V10.2-Android`.

## Vaste ondertekening

Voor updates over een bestaande installatie moet iedere APK dezelfde ondertekeningssleutel gebruiken. Maak de sleutel met:

```bash
chmod +x scripts/create-android-keystore.sh
./scripts/create-android-keystore.sh
```

Voeg de getoonde waarden toe aan GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Zonder deze secrets wordt een debug-APK gebouwd, bedoeld voor testen.

## Server

De Android-app vereist de mobiele API van het complete V10.2-serverpakket. Vul bij de eerste start bijvoorbeeld in:

`http://192.168.2.126:8080`

## V10.2.1 buildcorrectie

Wanneer V10.2 in een bestaande V10.1 GitHub-repository is geüpload, kunnen oude WebView-resources blijven staan (`activity_main.xml` en `main_menu.xml`). De workflow verwijdert deze bestanden nu automatisch vóór de Gradle-build.

Lokaal kan dezelfde opschoning worden uitgevoerd met:

```bash
./scripts/cleanup-old-webview-files.sh
```

## V10.2.2 buildcorrectie

Deze versie voegt de ontbrekende Compose-import toe in `MainActivity.kt`:

```kotlin
import androidx.activity.compose.setContent
```

Daarmee wordt `setContent { ... }` correct herkend door de Kotlin-compiler. De eerdere fout
`Unresolved reference: setContent` is hiermee verholpen.
