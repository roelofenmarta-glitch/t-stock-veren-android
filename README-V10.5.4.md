# T-Stock Veren V10.5.4 TEST – Android

Deze broncode bouwt een aparte Android-testapp met C-logo en betrouwbare offline fallback.

## Repositorystructuur

Plaats direct in de hoofdmap van GitHub:

- `.github/`
- `android/`
- `scripts/`
- `assets/`

## APK bouwen

Ga naar **Actions → Android APK V10.5.4 TEST → Run workflow**.

Zonder signing secrets wordt een test-debug APK gebouwd. Met signing secrets wordt een release APK gebouwd.

## Offline test

1. Open de app terwijl de server bereikbaar is.
2. Kies het werkgebied.
3. Open **Sync** en bouw de offline cache opnieuw op.
4. Controleer het aantal opgeslagen locaties.
5. Schakel wifi en mobiele data uit, of maak de T-Stock-server tijdelijk onbereikbaar.
6. Scan een artikel en kies **Zoek vrije locatie**.
7. Scan de geadviseerde locatie en sla de mutatie offline op.
8. Herstel de verbinding en synchroniseer.
