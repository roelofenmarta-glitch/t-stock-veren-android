# T-Stock Veren V10.5 TEST

Dit is de laatste complete testversie op basis van de originele V10-operatorflow.

## Nieuw in V10.5
- Werkgebied kiezen in web en Android: **Hoofdlocatie** of **Paganelstraat**.
- Locaties, zones en stellingen aan een werkgebied koppelen.
- Locatieadvies, scans, voorraad, offline cache en tellingen strikt per werkgebied.
- Telling wordt aan één werkgebied gekoppeld en blokkeert locaties uit een ander gebied.
- Webkop toont alleen C-logo en T-Stock Veren; het werkgebied staat in een compacte selector.
- Professionele login, dynamische rollen, beveiligde serverinstellingen en gescheiden labelbeheer uit V10.4 blijven aanwezig.
- Labels kunnen geen logo, C, Condoor of C+T gebruiken.

## Bestaande installatie bijwerken
```bash
mkdir -p /opt/magazijnbeheer/v10.5-test
cd /opt/magazijnbeheer/v10.5-test
unzip -o /root/t-stock-veren-v10.5-test-update.zip
cd t-stock-veren-v10.5-test-update
chmod +x scripts/*.sh
./scripts/upgrade-to-v10-5-test.sh
```

Het script maakt eerst een PostgreSQL-back-up. Gebruik nooit `docker compose down -v`.

## Nieuwe installatie
```bash
mkdir -p /opt/magazijnbeheer/v10.5-test-full
cd /opt/magazijnbeheer/v10.5-test-full
unzip -o /root/t-stock-veren-v10.5-test-full-install.zip
cd t-stock-veren-v10.5-test-full
chmod +x scripts/*.sh
./scripts/install-new-v10.sh
```

Webapp: `http://IP-VAN-DE-DOCKER-VM:8080`

## Android
Upload de inhoud van het Android-bronpakket naar GitHub en start:

`Actions → Android APK V10.5 TEST → Run workflow`

Artifact: `T-Stock-Veren-V10.5-TEST`

De testapp heeft een aparte app-ID en kan naast een bestaande productieapp staan.
