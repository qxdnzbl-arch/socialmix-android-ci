#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${PORT:?PORT is required}"
: "${VPN_UUID:?VPN_UUID is required}"
: "${VPN_PATH:?VPN_PATH is required}"
cat > config.json <<EOF
{
  "log": {"loglevel": "warning"},
  "inbounds": [{
    "listen": "0.0.0.0",
    "port": ${PORT},
    "protocol": "vless",
    "settings": {
      "clients": [{"id": "${VPN_UUID}"}],
      "decryption": "none"
    },
    "streamSettings": {
      "network": "ws",
      "security": "none",
      "wsSettings": {"path": "${VPN_PATH}"}
    }
  }],
  "outbounds": [
    {"protocol": "freedom", "tag": "direct"},
    {"protocol": "blackhole", "tag": "block"}
  ]
}
EOF
exec ./xray run -config config.json
