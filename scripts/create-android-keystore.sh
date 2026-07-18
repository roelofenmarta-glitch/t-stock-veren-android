#!/usr/bin/env bash
set -euo pipefail
OUT="${1:-tstock-veren-release.jks}"
ALIAS="${2:-tstockveren}"
if [ -e "$OUT" ]; then echo "Bestand bestaat al: $OUT"; exit 1; fi
read -rsp 'Kies een sterk keystore-wachtwoord: ' STOREPASS; echo
[ ${#STOREPASS} -ge 12 ] || { echo 'Gebruik minimaal 12 tekens.'; exit 1; }
keytool -genkeypair -v -keystore "$OUT" -alias "$ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
  -storepass "$STOREPASS" -keypass "$STOREPASS" -dname "CN=T-Stock Veren, OU=Android, O=T-Stock, C=NL"
echo
echo 'Bewaar de JKS en wachtwoorden veilig. Zonder dezelfde sleutel kan Android een bestaande app niet bijwerken.'
echo 'Maak GitHub secret ANDROID_KEYSTORE_BASE64 met:'
echo "base64 -w0 '$OUT'"
echo 'Andere secrets:'
echo "ANDROID_KEYSTORE_PASSWORD=$STOREPASS"
echo "ANDROID_KEY_ALIAS=$ALIAS"
echo "ANDROID_KEY_PASSWORD=$STOREPASS"
