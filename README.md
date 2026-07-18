# T-Stock Veren Android V10.2.4 TEST

Deze broncode bouwt een **losse testapp** naast de normale T-Stock Veren-app.

- Productie-ID: `nl.tstock.veren`
- Test-ID: `nl.tstock.veren.test`
- Testnaam: **T-Stock Veren TEST**
- Artifact: `T-Stock-Veren-V10.2.4-TEST`

## Correctie locatiebezetting

V10.2.3 vroeg online een vrije locatie aan de server, maar controleerde bij inboeken daarna nogmaals de mogelijk verouderde lokale cache. Daardoor kon dezelfde locatie tegelijk als vrij geadviseerd en als bezet afgekeurd worden.

V10.2.4 TEST behandelt de server als autoriteit wanneer het locatieadvies online is opgehaald. De server voert tijdens synchronisatie nog steeds de definitieve transactionele bezettingscontrole uit.

Daarnaast staat onder **Instellingen → Lokale opslag** de knop **Offline cache opnieuw opbouwen**. Openstaande mutaties worden daarbij niet verwijderd.

## GitHub Actions

Upload de inhoud van dit pakket naar de hoofdmap van de repository en start:

`Actions → Android APK V10.2.4 TEST → Run workflow`

Installeer de APK naast de bestaande app. Publiceer deze test-APK nog niet via het stabiele server-updatekanaal.
