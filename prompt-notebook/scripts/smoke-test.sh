#!/usr/bin/env bash
set -euo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
adb install -r "$APK"
adb shell am force-stop com.nzbl.promptbox
adb shell am start -W -n com.nzbl.promptbox/.MainActivity
sleep 4

dump_ui() {
  rm -f /tmp/window.xml
  adb shell rm -f /data/local/tmp/window.xml >/dev/null 2>&1 || true
  for i in 1 2 3 4 5; do
    adb shell uiautomator dump /data/local/tmp/window.xml || true
    if adb shell test -s /data/local/tmp/window.xml; then
      adb pull /data/local/tmp/window.xml /tmp/window.xml >/dev/null
      return 0
    fi
    sleep 2
  done
  echo "UI dump failed"
  adb shell dumpsys window windows | tail -120 || true
  return 1
}

tap_node() {
  local needle="$1"
  dump_ui
  python3 - "$needle" <<'PY'
import re,sys,subprocess,xml.etree.ElementTree as ET
needle=sys.argv[1]
root=ET.parse('/tmp/window.xml').getroot()
for n in root.iter('node'):
    text=n.attrib.get('text','')
    desc=n.attrib.get('content-desc','')
    if needle == text or needle == desc:
        b=n.attrib.get('bounds','')
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',b)
        if not m:
            continue
        x1,y1,x2,y2=map(int,m.groups())
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        sys.exit(0)
print(open('/tmp/window.xml', encoding='utf-8').read())
raise SystemExit('node not found: '+needle)
PY
  sleep 1
}

dump_ui
grep -q "提示词盒" /tmp/window.xml
grep -q "新建提示词" /tmp/window.xml

tap_node "新建提示词"
tap_node "标题输入框"
adb shell input text SmokeTest
tap_node "提示词输入框"
adb shell input text HelloPrompt
adb shell input keyevent 4
sleep 1
tap_node "保存提示词"
sleep 2

dump_ui
grep -q "SmokeTest" /tmp/window.xml
grep -q "HelloPrompt" /tmp/window.xml

PREFS=$(adb shell run-as com.nzbl.promptbox cat shared_prefs/prompt_box_prefs.xml)
echo "$PREFS" | grep -q "SmokeTest"
echo "$PREFS" | grep -q "HelloPrompt"

adb shell am force-stop com.nzbl.promptbox
adb shell am start -W -n com.nzbl.promptbox/.MainActivity
sleep 3
dump_ui
grep -q "SmokeTest" /tmp/window.xml

tap_node "复制提示词"
adb shell dumpsys activity activities | grep -q "com.nzbl.promptbox/.MainActivity"

echo "SMOKE_TEST_OK"
