#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RES="$ROOT/android/app/src/main/res"
rm -rf "$RES/layout" "$RES/menu" "$RES/xml" "$RES/values-night"
rm -f "$RES/drawable/ic_refresh.xml" "$RES/drawable/ic_scan.xml" "$RES/drawable/ic_server.xml"
echo "Oude V10.1 WebView-resources zijn verwijderd."
