#!/usr/bin/env bash
set -euo pipefail
rm -rf kehua-native
cat kehua_native_bundle_00.b64 | base64 -d | tar -xzf -
test -f kehua-native/android/app/src/main/java/com/kehua/revival/MainActivity.java
test -f kehua-native/backend/src/server.js
test -f kehua-native/ios/KehuaApp/KehuaApp.swift
echo "native source unpacked"
