FROM node:20-alpine
RUN apk add --no-cache ca-certificates curl unzip \
 && curl -fL --retry 3 -o /tmp/xray.zip https://github.com/XTLS/Xray-core/releases/download/v26.9.30/Xray-linux-64.zip \
 && mkdir -p /opt/xray \
 && unzip -q /tmp/xray.zip -d /opt/xray \
 && chmod +x /opt/xray/xray \
 && rm -f /tmp/xray.zip
WORKDIR /app
COPY server.mjs /app/server.mjs
CMD ["node","server.mjs"]
