#!/usr/bin/env bash
set -euo pipefail
ROOT="$PWD"
rm -rf /tmp/xray-core
git clone --depth 1 --branch v26.9.30 https://github.com/XTLS/Xray-core.git /tmp/xray-core
cd /tmp/xray-core
go build -trimpath -ldflags="-s -w" -o "$ROOT/vpn-render/xray" ./main
