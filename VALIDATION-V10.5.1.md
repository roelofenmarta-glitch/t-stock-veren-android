# Validatie V10.5.1 TEST

Gecontroleerd:

- Backend JavaScript-syntaxis
- Frontend productiebuild met Vite
- Shellscripts met `bash -n`
- JSON, YAML en Android XML parsing
- ZIP-integriteit
- Android testflavor heet intern `internal` en begint niet met `test`
- GitHub-workflow gebruikt `assembleInternalDebug` / `assembleInternalRelease`
- Locatiekaart-API leest `workAreaKey` en ondersteunt tot 5000 locaties voor de kaart

De Android APK wordt door GitHub Actions gecompileerd; in deze omgeving is geen volledige Android SDK aanwezig.
