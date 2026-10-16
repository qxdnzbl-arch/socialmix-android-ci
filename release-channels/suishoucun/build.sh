#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p public
curl --fail --location --silent --show-error --retry 3 --max-time 120 \
 "https://github.com/qxdnzbl-arch/socialmix-android-ci/releases/download/v102/Suishoucun.apk" \
 -o public/Suishoucun-v102.apk
echo "ff02ede2d8b1b1d7b73d3f91b8006a54a9e247b01b09d8b7a5197a4746be3400  public/Suishoucun-v102.apk" | sha256sum --check
unzip -tqq public/Suishoucun-v102.apk
test "$(stat -c '%s' public/Suishoucun-v102.apk)" -eq 70126
echo 'Verified public release v102 without replacing source, signing key, or user data.'
