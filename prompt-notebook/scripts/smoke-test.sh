#!/usr/bin/env bash
set -euo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
adb install -r "$APK"
adb shell am force-stop com.nzbl.promptbox
adb shell monkey -p com.nzbl.promptbox 1 >/dev/null
sleep 2

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null
}

tap_node() {
  local needle="$1"
  dump_ui
  python3 - "$needle" <<'PY'
import re,sys,subprocess,xml.etree.ElementTree as ET
needle=sys.argv[1]
root=ET.parse('/tmp/window.xml').getroot()
for n in root.iter('node'):
    if needle in (n.attrib.get('text',''), n.attrib.get('content-desc','')):
        b=n.attrib.get('bounds','')
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',b)
        if not m:
            continue
        x1,y1,x2,y2=map(int,m.groups())
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        sys.exit(0)
raise SystemExit('node not found: '+needle)
PY
  sleep 1
}

tap_node "新建提示词"
tap_node "标题输入框"
adb shell input text SmokeTest
tap_node "提示词输入框"
adb shell input text HelloPrompt
tap_node "保存提示词"
sleep 1

dump_ui
grep -q "SmokeTest" /tmp/window.xml

adb shell am force-stop com.nzbl.promptbox
adb shell monkey -p com.nzbl.promptbox 1 >/dev/null
sleep 2
dump_ui
grep -q "SmokeTest" /tmp/window.xml

tap_node "复制提示词"

echo "SMOKE_TEST_OK"
