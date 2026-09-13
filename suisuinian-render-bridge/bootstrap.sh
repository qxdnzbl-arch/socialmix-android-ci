#!/usr/bin/env bash
set -euo pipefail

: "${SUI_PAYLOAD_KEY:?SUI_PAYLOAD_KEY is required}"
rm -rf work public
mkdir -p work public
base64 -d suiyuan-v468.tar.gz.enc.b64 > /tmp/suiyuan-v468.tar.gz.enc
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -in /tmp/suiyuan-v468.tar.gz.enc -out /tmp/suiyuan-v468.tar.gz \
  -pass env:SUI_PAYLOAD_KEY
tar -xzf /tmp/suiyuan-v468.tar.gz -C work
rm -f /tmp/suiyuan-v468.tar.gz /tmp/suiyuan-v468.tar.gz.enc
cd work
bash build-on-render.sh
