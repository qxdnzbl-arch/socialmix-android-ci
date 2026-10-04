import http from "node:http";
import { writeFileSync } from "node:fs";
import { spawn } from "node:child_process";

const required = ["VPN_UUID","REALITY_PRIVATE_KEY","REALITY_SHORT_ID"];
for (const key of required) {
  if (!process.env[key]) {
    console.error("Missing required env:", key);
    process.exit(1);
  }
}

const healthPort = Number(process.env.PORT || 8080);
const xrayPort = Number(process.env.XRAY_PORT || 10000);
const serverName = process.env.REALITY_SERVER_NAME || "www.microsoft.com";
const dest = process.env.REALITY_DEST || (serverName + ":443");

const config = {
  log: { loglevel: "warning" },
  inbounds: [{
    listen: "0.0.0.0",
    port: xrayPort,
    protocol: "vless",
    settings: {
      clients: [{
        id: process.env.VPN_UUID,
        flow: "xtls-rprx-vision"
      }],
      decryption: "none"
    },
    streamSettings: {
      network: "tcp",
      security: "reality",
      realitySettings: {
        show: false,
        dest,
        xver: 0,
        serverNames: [serverName],
        privateKey: process.env.REALITY_PRIVATE_KEY,
        shortIds: [process.env.REALITY_SHORT_ID]
      }
    },
    sniffing: {
      enabled: true,
      destOverride: ["http","tls","quic"],
      routeOnly: true
    }
  }],
  outbounds: [
    { protocol: "freedom", tag: "direct" },
    { protocol: "blackhole", tag: "block" }
  ],
  routing: {
    domainStrategy: "IPIfNonMatch",
    rules: [
      { ip: ["geoip:private"], outboundTag: "block" }
    ]
  }
};

writeFileSync("/tmp/xray-config.json", JSON.stringify(config));

const xray = spawn("/opt/xray/xray", ["run","-config","/tmp/xray-config.json"], {
  stdio: "inherit"
});

xray.on("exit", (code, signal) => {
  console.error("xray exited", {code, signal});
  process.exit(code ?? 1);
});

const serverHttp = http.createServer((req, res) => {
  if (req.url === "/health") {
    res.writeHead(200, {"content-type":"text/plain"});
    res.end("ok");
    return;
  }
  res.writeHead(200, {"content-type":"application/json"});
  res.end(JSON.stringify({ok:true, service:"private-vpn-reality"}));
});

serverHttp.listen(healthPort, "0.0.0.0", () => {
  console.log("health listening", healthPort, "xray", xrayPort);
});

const shutdown = () => {
  try { xray.kill("SIGTERM"); } catch {}
  try { serverHttp.close(() => process.exit(0)); } catch { process.exit(0); }
};
process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
