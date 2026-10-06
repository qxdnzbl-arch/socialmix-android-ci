#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk_path="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/workspace/android-sdk}}"
sdk_jar="$sdk_path/platforms/android-35/android.jar"
build_tools="$sdk_path/build-tools/35.0.0"
test -f "$sdk_jar"
mkdir -p build/classes build/dex build/test-classes build/test-dex out
manifest_path="app/src/main/AndroidManifest.xml"
if [ -n "${APP_VERSION_CODE:-}" ]; then
  cp app/src/main/AndroidManifest.xml build/AndroidManifest.xml
  python3 - <<'PY'
from pathlib import Path
import os,re
p=Path('build/AndroidManifest.xml')
s=p.read_text()
s=re.sub(r'android:versionCode="[^"]+"', f'android:versionCode="{os.environ["APP_VERSION_CODE"]}"', s, count=1)
s=re.sub(r'android:versionName="[^"]+"', f'android:versionName="{os.environ.get("APP_VERSION_NAME","1."+os.environ["APP_VERSION_CODE"])}"', s, count=1)
p.write_text(s)
PY
  manifest_path="build/AndroidManifest.xml"
fi
"$build_tools/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$build_tools/aapt2" link -o build/base.apk -I "$sdk_jar" --manifest "$manifest_path" --java build/generated build/resources.zip
javac -encoding UTF-8 -source 8 -target 8 -classpath "$sdk_jar" -d build/classes $(find app/src/main/java -name '*.java')
jar cf build/main-classes.jar -C build/classes .
"$build_tools/d8" --min-api 26 --lib "$sdk_jar" --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$build_tools/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [ ! -f build/pocket-signing.jks ]; then
  keytool -genkeypair -keystore build/pocket-signing.jks -storepass pocket-build -keypass pocket-build -alias pocket -dname 'CN=Suishoucun,OU=Personal App,O=nzbl' -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$build_tools/apksigner" sign --ks build/pocket-signing.jks --ks-pass pass:pocket-build --out out/Suishoucun.apk build/aligned.apk
"$build_tools/apksigner" verify --verbose out/Suishoucun.apk
if [ -d test/src ]; then
  javac -encoding UTF-8 -source 8 -target 8 -classpath "$sdk_jar:build/classes" -d build/test-classes $(find test/src -name '*.java')
  "$build_tools/d8" --min-api 26 --lib "$sdk_jar" --classpath build/main-classes.jar --output build/test-dex $(find build/test-classes -name '*.class')
  "$build_tools/aapt2" link -o build/test-base.apk -I "$sdk_jar" --manifest test/AndroidManifest.xml
  cp build/test-base.apk build/test-unsigned.apk
  (cd build/test-dex && zip -q ../test-unsigned.apk classes.dex)
  "$build_tools/zipalign" -f 4 build/test-unsigned.apk build/test-aligned.apk
  "$build_tools/apksigner" sign --ks build/pocket-signing.jks --ks-pass pass:pocket-build --out out/Suishoucun-tests.apk build/test-aligned.apk
fi
python3 - <<'PY' > out/SHA256.txt
from pathlib import Path
import hashlib
apk = Path('out/Suishoucun.apk')
print(hashlib.sha256(apk.read_bytes()).hexdigest() + '  ' + str(apk))
PY
