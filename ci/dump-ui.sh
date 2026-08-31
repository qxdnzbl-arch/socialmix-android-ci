#!/usr/bin/env bash
set -euo pipefail

remote="$1"
local_file="$2"

for attempt in 1 2 3 4 5 6 7 8; do
  rm -f "$local_file"
  adb shell rm -f "$remote" >/dev/null 2>&1 || true
  if adb shell uiautomator dump "$remote" >/tmp/uia_dump.log 2>&1; then
    if adb pull "$remote" "$local_file" >/tmp/uia_pull.log 2>&1 && [ -s "$local_file" ]; then
      exit 0
    fi
  fi
  sleep 0.4
done

cat /tmp/uia_dump.log || true
cat /tmp/uia_pull.log || true
exit 1
