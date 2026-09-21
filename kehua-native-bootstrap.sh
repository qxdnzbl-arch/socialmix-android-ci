#!/usr/bin/env bash
set -euo pipefail
cat kehua_native_bundle_00.b64 kehua_native_bundle_01.b64 kehua_native_bundle_02.b64 kehua_native_bundle_03.b64 | tr -d '\n\r ' | base64 -d > kehua_native_bundle.tar.gz
echo "6b2f9e3a3d99c66fd0d60fffdca44305496dcf258f1a41ca22c1aec1f5bc1344  kehua_native_bundle.tar.gz" | sha256sum -c -
rm -rf kehua-native
tar -xzf kehua_native_bundle.tar.gz
test -f kehua-native/android/app/src/main/java/com/kehua/revival/MainActivity.java
test -f kehua-native/backend/src/server.js
test -f kehua-native/ios/KehuaApp/KehuaApp.swift
echo "native source unpacked"
