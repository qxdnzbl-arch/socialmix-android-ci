#!/usr/bin/env bash
set -euo pipefail

APK="dualphone/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.qxdnzbl.shuangjichuan.offline"
ACT="$PKG/com.qxdnzbl.shuangjichuan.MainActivity"

echo no | avdmanager create avd -n peer2 -k "system-images;android-31;google_apis;x86_64" --device "pixel_6" --force
"$ANDROID_HOME/emulator/emulator" -avd peer2 -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim -camera-back none -port 5556 >/tmp/peer2.log 2>&1 &

ok=0
for i in $(seq 1 75); do
  boot="$(adb -s emulator-5556 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$boot" == "1" ]]; then ok=1; break; fi
  sleep 2
done
[[ "$ok" == "1" ]]

for dev in emulator-5554 emulator-5556; do
  adb -s "$dev" install -r "$APK"
  adb -s "$dev" shell pm clear "$PKG"
  adb -s "$dev" shell pm grant "$PKG" android.permission.BLUETOOTH_ADVERTISE || true
  adb -s "$dev" shell pm grant "$PKG" android.permission.BLUETOOTH_CONNECT || true
  adb -s "$dev" shell pm grant "$PKG" android.permission.BLUETOOTH_SCAN || true
  adb -s "$dev" shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
  adb -s "$dev" shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION || true
  adb -s "$dev" shell svc data disable || true
  adb -s "$dev" shell svc wifi enable || true
  adb -s "$dev" shell settings put secure location_mode 3 || true
  adb -s "$dev" shell cmd location set-location-enabled true || true
  adb -s "$dev" shell svc bluetooth enable || true
  adb -s "$dev" shell cmd bluetooth_manager enable || true
  adb -s "$dev" logcat -c
done

adb -s emulator-5556 shell am start -n "$ACT" --ez ciNearbyOnly true
sleep 3
adb -s emulator-5554 shell am start -n "$ACT" --ez ciNearbyOnly true --ez ciSend true --ez ciSendFile true

passed=0
for i in $(seq 1 90); do
  adb -s emulator-5556 logcat -d -s DualPhoneUi:I "*:S" >/tmp/receiver.log 2>/dev/null || true
  if grep -q "messages=2" /tmp/receiver.log; then passed=1; break; fi
  sleep 1
done

cat /tmp/receiver.log || true
echo "=== sender nearby ==="
adb -s emulator-5554 logcat -d -s DualNearby:V "*:S" || true
echo "=== receiver nearby ==="
adb -s emulator-5556 logcat -d -s DualNearby:V "*:S" || true

grep -q "connected " <(adb -s emulator-5554 logcat -d -s DualNearby:I "*:S" || true)
grep -q "connected " <(adb -s emulator-5556 logcat -d -s DualNearby:I "*:S" || true)
[[ "$passed" == "1" ]]
adb -s emulator-5556 exec-out screencap -p > nearby-receiver.png
