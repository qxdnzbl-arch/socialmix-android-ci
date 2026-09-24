#!/usr/bin/env bash
set -euo pipefail
cat kehua_native_bundle_00.b64 kehua_native_bundle_01.b64 kehua_native_bundle_02.b64 kehua_native_bundle_03.b64 | tr -d '\n\r ' | base64 -d > kehua_native_bundle.tar.gz
gzip -t kehua_native_bundle.tar.gz
tar -tzf kehua_native_bundle.tar.gz >/dev/null
rm -rf kehua-native
tar -xzf kehua_native_bundle.tar.gz
test -f kehua-native/android/app/src/main/java/com/kehua/revival/MainActivity.java
test -f kehua-native/backend/src/server.js
test -f kehua-native/ios/KehuaApp/KehuaApp.swift
grep -R "https://kehua-api-production.up.railway.app" kehua-native/android kehua-native/ios >/dev/null
if grep -R "render.com\|onrender.com" kehua-native/android kehua-native/ios >/dev/null; then
  echo "stale Render backend URL remains in native clients" >&2
  exit 1
fi
echo "native source unpacked and production backend wiring verified"
