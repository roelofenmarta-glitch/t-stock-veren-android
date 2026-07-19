# T-Stock Veren V10.5.1 TEST

## Nieuw
- Verplichte keuze van werkgebied in web en Android.
- Standaardwerkgebieden Hoofdlocatie en Paganelstraat.
- Locaties en hele zones/stellingen aan een werkgebied koppelen.
- Strikte filtering van locatieadvies, scans, voorraad, offline cache en tellingen.
- Een telling wordt altijd aan één werkgebied gekoppeld.
- Webkop toont alleen het C-logo en T-Stock Veren; het actieve werkgebied staat in een compacte selector.
- Android TEST gebruikt een eigen app-ID en bewaart het laatst gekozen werkgebied.

## Veilig upgraden
Het updatescript maakt eerst een PostgreSQL-back-up en verwijdert geen volumes.
Gebruik nooit `docker compose down -v` bij een bestaande installatie.
