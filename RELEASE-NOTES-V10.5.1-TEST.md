# T-Stock Veren V10.5.1 TEST

Hotfix op V10.5.1 TEST.

## Hersteld

- Locatiekaart laadt weer voor het gekozen werkgebied.
- Backend leest `workAreaKey` correct uit de aanvraag.
- Fouten en een leeg werkgebied worden zichtbaar gemeld in plaats van een leeg scherm.
- Android TEST-productflavor heet intern `internal`; Gradle accepteert deze naam wel.
- GitHub Actions bouwt `assembleInternalDebug` of `assembleInternalRelease`.
- Testapp blijft herkenbaar als **T-Stock Veren TEST** en gebruikt een aparte app-ID.

## Ongewijzigd

- Hoofdlocatie en Paganelstraat blijven strikt gescheiden.
- Bestaande database, voorraad, bundels, locaties en gebruikers blijven behouden.
- Gebruik voor een bestaande installatie het hotfix-updatescript en nooit `docker compose down -v`.
