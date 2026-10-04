#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="${DOIT_BUILD_CACHE:-$HOME/.cache/doit-build}"
if [ -z "${DOIT_JDK:-}" ]; then
  for candidate in "$CACHE"/jdk-*/Contents/Home; do
    if [ -x "$candidate/bin/javac" ]; then DOIT_JDK="$candidate"; break; fi
  done
fi
DOIT_JDK="${DOIT_JDK:-$CACHE/jdk-17.0.20.1+1/Contents/Home}"
TOOLS="${DOIT_ANDROID_TOOLS:-$CACHE/sdk/build-tools/android-15}"
PLATFORM="${DOIT_ANDROID_PLATFORM:-$CACHE/sdk/platforms/android-35/android.jar}"
BUILD="$ROOT/build/android"
if [ ! -x "$DOIT_JDK/bin/javac" ] || [ ! -f "$PLATFORM" ] || [ ! -x "$TOOLS/aapt2" ]; then echo 'Missing Android build tools; see README.md.'; exit 1; fi
mkdir -p "$BUILD/classes" "$BUILD/generated" "$BUILD/dex" "$ROOT/dist"
"$TOOLS/aapt2" compile --dir "$ROOT/android/res" -o "$BUILD/resources.zip"
"$TOOLS/aapt2" link -o "$BUILD/resources.apk" -I "$PLATFORM" --manifest "$ROOT/android/AndroidManifest.xml" --java "$BUILD/generated" -A "$ROOT/web" "$BUILD/resources.zip"
find "$ROOT/android/src" "$BUILD/generated" -name '*.java' > "$BUILD/sources.txt"
"$DOIT_JDK/bin/javac" -encoding UTF-8 --release 8 -classpath "$PLATFORM" -d "$BUILD/classes" @"$BUILD/sources.txt"
"$DOIT_JDK/bin/jar" cf "$BUILD/classes.jar" -C "$BUILD/classes" .
"$DOIT_JDK/bin/java" -cp "$TOOLS/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 26 --lib "$PLATFORM" --output "$BUILD/dex" "$BUILD/classes.jar"
cp "$BUILD/resources.apk" "$BUILD/unsigned.apk"
(cd "$BUILD/dex" && zip -q -u "$BUILD/unsigned.apk" classes.dex)
"$TOOLS/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
KEY="$CACHE/doit-local.keystore"
if [ ! -f "$KEY" ]; then "$DOIT_JDK/bin/keytool" -genkeypair -keystore "$KEY" -storepass android -keypass android -alias doit-local -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Do It Local Development' >/dev/null; fi
"$DOIT_JDK/bin/java" -jar "$TOOLS/lib/apksigner.jar" sign --ks "$KEY" --ks-key-alias doit-local --ks-pass pass:android --key-pass pass:android --out "$ROOT/dist/Do-It-0.1.1.apk" "$BUILD/aligned.apk"
"$DOIT_JDK/bin/java" -jar "$TOOLS/lib/apksigner.jar" verify --verbose "$ROOT/dist/Do-It-0.1.1.apk"
echo "$ROOT/dist/Do-It-0.1.1.apk"
