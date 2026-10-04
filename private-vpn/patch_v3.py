#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "v2rayng-src")

# Package/name/icon.
app_gradle = root / "V2rayNG/app/build.gradle.kts"
s = app_gradle.read_text()
s = s.replace('applicationId = "com.v2ray.ang"', 'applicationId = "com.nzbl.privatevpn"')
s = s.replace('"v2rayNG_${variant.versionName}-fdroid_${abi}.apk"', '"PrivateVPN_${variant.versionName}-fdroid_${abi}.apk"')
s = s.replace('"v2rayNG_${variant.versionName}_${abi}.apk"', '"PrivateVPN_${variant.versionName}_${abi}.apk"')
app_gradle.write_text(s)

for rel in [
    "V2rayNG/app/src/main/res/values/strings.xml",
    "V2rayNG/app/src/fdroid/res/values/strings.xml",
    "V2rayNG/app/src/dev/res/values/strings.xml",
    "V2rayNG/app/src/pre_release/res/values/strings.xml",
]:
    p = root / rel
    t = p.read_text()
    t = t.replace("v2rayNG (F-Droid)", "私人VPN")
    t = t.replace("v2rayNG (DEV)", "私人VPN")
    t = t.replace("v2rayNG (PR)", "私人VPN")
    t = t.replace(">v2rayNG<", ">私人VPN<")
    p.write_text(t)

icon = root / "V2rayNG/app/src/main/res/drawable/ic_private_vpn.xml"
icon.parent.mkdir(parents=True, exist_ok=True)
icon.write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#FFFFFF"
        android:pathData="M54,8 L91,22 L86,60 C82,79 69,92 54,100 C39,92 26,79 22,60 L17,22 Z"/>
    <path android:fillColor="#111111"
        android:pathData="M54,27 C44,27 36,35 36,45 C36,52 40,58 46,61 L46,77 L62,77 L62,61 C68,58 72,52 72,45 C72,35 64,27 54,27 Z M54,37 C59,37 62,40 62,45 C62,50 59,53 54,53 C49,53 46,50 46,45 C46,40 49,37 54,37 Z"/>
</vector>
""")
manifest = root / "V2rayNG/app/src/main/AndroidManifest.xml"
m = manifest.read_text()
m = m.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/ic_private_vpn"')
manifest.write_text(m)

# Two placeholder nodes. Railway is imported first and becomes default.
asset = root / "V2rayNG/app/src/main/assets/nzbl_nodes.txt"
asset.write_text(
    "vless://00000000-0000-4000-8000-000000000002@railway-placeholder.invalid:443?encryption=none&security=tls&sni=railway-placeholder.invalid&type=ws&host=railway-placeholder.invalid&path=%2Frailway-placeholder#NZBL-FAST-Railway-SG\n"
    "vless://00000000-0000-4000-8000-000000000001@render-placeholder.invalid:443?encryption=none&security=tls&sni=render-placeholder.invalid&type=ws&host=render-placeholder.invalid&path=%2Frender-placeholder#NZBL-BACKUP-Render-SG\n"
)

# Refresh bundled nodes once on upgrade, preserving all non-NZBL user configs.
app = root / "V2rayNG/app/src/main/java/com/v2ray/ang/AngApplication.kt"
a = app.read_text()
imports = [
    "import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID",
    "import com.v2ray.ang.handler.AngConfigManager",
    "import com.v2ray.ang.handler.MmkvManager",
]
anchor_import = "import com.v2ray.ang.AppConfig.ANG_PACKAGE"
if anchor_import not in a:
    raise SystemExit("ANG_PACKAGE import anchor missing")
for imp in reversed(imports):
    if imp not in a:
        a = a.replace(anchor_import, anchor_import + "\n" + imp)

anchor_body = """        SettingsManager.initApp(this)
        SettingsManager.setNightMode()
"""
if anchor_body not in a:
    raise SystemExit("SettingsManager body anchor missing")

body = """        SettingsManager.initApp(this)
        SettingsManager.setNightMode()

        val nzblBundleVersion = "3"
        if (MmkvManager.decodeSettingsString("nzbl_bundle_version", "") != nzblBundleVersion) {
            MmkvManager.decodeAllServerList().toList().forEach { guid ->
                val remarks = MmkvManager.decodeServerConfig(guid)?.remarks.orEmpty()
                if (remarks.startsWith("NZBL-")) {
                    MmkvManager.removeServer(guid)
                }
            }

            runCatching {
                assets.open("nzbl_nodes.txt").bufferedReader().useLines { lines ->
                    lines.map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { node ->
                            AngConfigManager.importBatchConfig(
                                node,
                                DEFAULT_SUBSCRIPTION_ID,
                                true
                            )
                        }
                }
                MmkvManager.encodeSettings("nzbl_bundle_version", nzblBundleVersion)
            }
        }
"""
a = a.replace(anchor_body, body)
for required in imports:
    if required not in a:
        raise SystemExit("required import missing: " + required)
if "nzbl_bundle_version" not in a:
    raise SystemExit("bundle refresh patch missing")
app.write_text(a)

print("NZBL v3 fast-node patch applied")
