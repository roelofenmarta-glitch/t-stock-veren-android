# Android V10.5.1 TEST

- Productie-app: `nl.tstock.veren`, updatekanaal `stable`.
- Beta-app: `nl.tstock.veren.beta`, updatekanaal `beta`.
- Losse testapp: `nl.tstock.veren.test104`, updatekanaal `test`.
- Testversie: `10.5.1-test`, versionCode `10500`.
- Appicoon: gecombineerd C+T-logo.
- Android Auto Backup is uitgeschakeld voor betrouwbare lokale testdata.
- Serverinstellingen zijn na de eerste configuratie alleen met beheerlogin te wijzigen.
- De lokale cache wordt pas vervangen nadat een volledige synchronisatie succesvol is ontvangen.
- Offline profiel standaard: `Paganelstraat`.

## Eerste offline ingebruikname

1. Installeer de testapp.
2. Stel online het serveradres in.
3. Log in.
4. Kies `Synchronisatie` en bouw het Paganelstraat-pakket op.
5. Controleer het aantal locaties, bundels en wachtende mutaties.
6. Zet daarna netwerk uit en voer de offline praktijktest uit.

Een nieuw geïnstalleerde app kan niet offline beginnen voordat minimaal één succesvolle online synchronisatie is uitgevoerd.
