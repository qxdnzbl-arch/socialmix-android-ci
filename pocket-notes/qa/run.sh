#!/usr/bin/env bash
set -euo pipefail
cd pocket-notes
mkdir -p out/qa
adb install -r out/Suishoucun.apk
adb install -r out/Suishoucun-tests.apk
adb shell settings put secure show_ime_with_hard_keyboard 1
adb push out/reference-fixture.png /sdcard/Download/reference-fixture.png
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/reference-fixture.png
adb shell am instrument -w com.nzbl.pocket.test/com.nzbl.pocket.Acceptance | tee out/instrumentation.txt
adb pull /sdcard/Android/data/com.nzbl.pocket/files/qa/ out/qa/
python3 - <<'PY'
from pathlib import Path
import json
p=list(Path('out/qa').rglob('acceptance.json'))
assert len(p)==1, 'Missing completed acceptance report'
r=json.loads(p[0].read_text())
assert r['passed'] is True
assert len(r['checks'])>=35
print('ANDROID ACCEPTANCE PASSED',len(r['checks']))
PY
# Force-stop and relaunch verifies that the actual installed app reads saved bytes.
adb shell am force-stop com.nzbl.pocket
adb shell am start -n com.nzbl.pocket/.MainActivity
sleep 1
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml out/qa/relaunch.xml
python3 - <<'PY'
import xml.etree.ElementTree as E
r=E.parse('out/qa/relaunch.xml')
texts=[n.get('text','') for n in r.iter('node')]
assert any('下次理发' in t for t in texts), texts
assert '随手存' in texts, texts
PY
adb shell wm size 720x1280
adb shell wm density 320
adb shell am force-stop com.nzbl.pocket
adb shell am start -n com.nzbl.pocket/.MainActivity
sleep 1
adb shell screencap -p /sdcard/small-screen.png
adb pull /sdcard/small-screen.png out/qa/small-screen.png
adb shell uiautomator dump /sdcard/small.xml
adb pull /sdcard/small.xml out/qa/small-screen.xml
python3 - <<'PY'
import xml.etree.ElementTree as E
import re
r=E.parse('out/qa/small-screen.xml')
for title in ['分类','＋  记一条']:
 nodes=[n for n in r.iter('node') if n.get('text')==title]
 assert nodes, title
 box=list(map(int,re.findall(r'\d+',nodes[0].get('bounds'))))
 assert 0<=box[0]<box[2]<=720 and 0<=box[1]<box[3]<=1280, (title,box)
print('SMALL SCREEN BOUNDS PASSED')
PY
adb shell wm size reset
adb shell wm density reset
adb logcat -d -s AndroidRuntime:E > out/qa/crash-log.txt
if grep -q 'FATAL EXCEPTION' out/qa/crash-log.txt; then cat out/qa/crash-log.txt; exit 1; fi
