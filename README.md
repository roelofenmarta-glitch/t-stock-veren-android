# T-Stock Veren V10.2.3 Android

Native Android-app voor **T-Stock Magazijnbeheer Veren V10.2 of nieuwer**.

## Functies

- Native Jetpack Compose-interface, geen WebView als hoofdscherm.
- Barcode- en QR-scanner.
- Inboeken met V10-locatieadvies en locatiecontrole.
- Verplaatsen, uitboeken en voorraad zoeken.
- SQLite-cache en offline mutatiewachtrij.
- Automatische synchronisatie bij starten en terugkeren naar de app.
- Offline locatieoverzicht met vrij, bezet en geblokkeerd.
- Zichtbare telling van lokaal opgeslagen locaties en bundels.
- Updatecontrole en APK-download vanaf de eigen server.

## Belangrijk voor offline gebruik

De app kan alleen locaties offline gebruiken nadat minimaal één volledige synchronisatie met de server is voltooid.
Na een goede synchronisatie toont de app bijvoorbeeld:

`Synchronisatie voltooid: 240 locaties offline beschikbaar.`

Een lege of ongeldige serverrespons wist de bestaande locatiecache niet meer. Hierdoor blijven eerder opgeslagen locaties beschikbaar wanneer de server tijdelijk niet bereikbaar is.

## GitHub

Upload de inhoud van deze map rechtstreeks naar de hoofdmap van de GitHub-repository. De structuur moet zijn:

```text
.github/workflows/android-apk.yml
android/settings.gradle.kts
android/build.gradle.kts
android/app/build.gradle.kts
```

Start daarna:

`Actions → Android APK V10.2.3 → Run workflow`

De artifactnaam is `T-Stock-Veren-V10.2.3-Android`.

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

Zonder deze secrets wordt een debug-APK gebouwd, bedoeld voor testen. Een debug-APK kan meestal niet als update over een anders ondertekende APK worden geïnstalleerd.

## Server

De Android-app vereist de mobiele API van het complete V10.2-serverpakket. Vul bij de eerste start bijvoorbeeld in:

`http://192.168.2.126:8080`

Controleer de mobiele server-API met:

```bash
curl -s http://192.168.2.126:8080/api/mobile/bootstrap | head
```

De respons moet onder andere `locations` bevatten. Bij `404` draait nog de oude server zonder mobiele offline API.

## Correcties

### V10.2.1

De GitHub-workflow verwijdert achtergebleven WebView-resources vóór de build.

### V10.2.2

De ontbrekende Compose-import `androidx.activity.compose.setContent` is toegevoegd.

### V10.2.3

- Locaties worden automatisch vernieuwd bij starten en terugkeren naar de app.
- De bestaande locatiecache wordt beschermd tegen lege of ongeldige serverresponses.
- De app toont het aantal offline locaties en bundels.
- Nieuw offline locatieoverzicht met zoekfunctie.
- Duidelijke melding wanneer de server nog geen V10.2 mobiele API heeft.
