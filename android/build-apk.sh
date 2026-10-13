#!/bin/sh
# build-apk.sh — no-gradle APK build for minis-companion (runs on a Linux box
# with: JDK(javac+keytool), /opt/android-sdk-lite (build-tools 34 + android-34
# platform), zip). Usage: build-apk.sh <src-dir> <out.apk> <auth-user> <auth-pass>
set -e
SRC="$1"; OUT="$2"; AUSER="$3"; APASS="$4"; APIN="$5"
[ -n "$APIN" ] || { echo "usage: build-apk.sh <src-dir> <out.apk> <user> <pass> <pin>"; exit 1; }
PIN_SHA=$(printf 'mc:%s' "$APIN" | sha256sum | cut -d' ' -f1)
SDK=/opt/android-sdk-lite
BT=$(ls -d $SDK/build-tools/* | head -1)
PLATFORM=$(ls $SDK/platforms/*/android.jar | head -1)
[ -x "$BT/aapt2" ] && [ -f "$PLATFORM" ] || { echo "SDK not ready"; exit 1; }
W=$(mktemp -d)
cp -r "$SRC/app/src/main/." "$W/"
cd "$W"
sed -i "s|@AUTH_USER@|$AUSER|; s|@AUTH_PASS@|$APASS|" java/site/zhangyue/miniscompanion/MainActivity.java
mkdir -p classes dexout gen
find java -name '*.java' > srcs.txt
javac -encoding UTF-8 -source 8 -target 8 -nowarn \
  -bootclasspath "$PLATFORM:$BT/core-lambda-stubs.jar" -d classes @srcs.txt 2>&1 | grep -v "warning\|deprecat" || true
"$BT/d8" --release --lib "$PLATFORM" --min-api 24 --output dexout $(find classes -name '*.class')
"$BT/aapt2" compile --dir res -o res.zip
"$BT/aapt2" link -o base.apk -I "$PLATFORM" --manifest AndroidManifest.xml res.zip --java gen
(cd dexout && zip -q -j ../base.apk classes.dex)
"$BT/zipalign" -f 4 base.apk aligned.apk
KS=$SDK/minis-companion.keystore
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -alias companion -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass companion123 -keypass companion123 -dname "CN=minis-companion" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:companion123 --key-pass pass:companion123 --out "$OUT" aligned.apk
"$BT/apksigner" verify --print-certs "$OUT" | head -3
echo "BUILT: $OUT ($(du -h "$OUT" | cut -f1))"
rm -rf "$W"
