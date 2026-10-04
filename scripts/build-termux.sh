#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

fail() { echo "ERROR: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || fail "Missing '$1'. Install it in Termux first."; }

[ -n "${PREFIX:-}" ] || fail "PREFIX is not set. Run this script inside Termux."
need cmake
need ninja
need clang++
need llvm-readelf
need patchelf
need aapt2
need aidl
need java
need python3

if [ -x ./gradlew ]; then
  GRADLE_CMD=(./gradlew)
else
  need gradle
  GRADLE_CMD=(gradle)
fi

[ -f local.properties ] || fail "local.properties is missing. It must contain sdk.dir=<your Android SDK>."
SDK_DIR="$(sed -n 's/^sdk.dir=//p' local.properties | tail -n 1)"
[ -n "$SDK_DIR" ] || fail "local.properties does not contain sdk.dir=."
[ -d "$SDK_DIR/platforms" ] || fail "Android SDK not found at '$SDK_DIR'."

mkdir -p "$HOME/.gradle"
AAPT_LINE="android.aapt2FromMavenOverride=$PREFIX/bin/aapt2"
if grep -q '^android.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties" 2>/dev/null; then
  sed -i "s#^android.aapt2FromMavenOverride=.*#$AAPT_LINE#" "$HOME/.gradle/gradle.properties"
else
  echo "$AAPT_LINE" >> "$HOME/.gradle/gradle.properties"
fi

BUILD_TOOLS_DIR="$(find "$SDK_DIR/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1)"
[ -n "$BUILD_TOOLS_DIR" ] || fail "No Android SDK build-tools directory found under $SDK_DIR/build-tools."
if [ ! -L "$BUILD_TOOLS_DIR/aidl" ] || [ "$(readlink "$BUILD_TOOLS_DIR/aidl" 2>/dev/null || true)" != "$PREFIX/bin/aidl" ]; then
  if [ -e "$BUILD_TOOLS_DIR/aidl" ] && [ ! -e "$BUILD_TOOLS_DIR/aidl.google-original" ]; then
    mv "$BUILD_TOOLS_DIR/aidl" "$BUILD_TOOLS_DIR/aidl.google-original"
  else
    rm -f "$BUILD_TOOLS_DIR/aidl"
  fi
  ln -s "$PREFIX/bin/aidl" "$BUILD_TOOLS_DIR/aidl"
fi

OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
rm -rf "$ROOT/.termux-native"
mkdir -p "$OUT"

cmake \
  -S "$ROOT/app/src/main/cpp" \
  -B "$ROOT/.termux-native" \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_CXX_COMPILER="$PREFIX/bin/clang++" \
  -DCMAKE_MAKE_PROGRAM="$PREFIX/bin/ninja" \
  -DINPUTMAPPER_TERMUX=ON \
  -DCMAKE_LIBRARY_OUTPUT_DIRECTORY="$OUT"

cmake --build "$ROOT/.termux-native" --verbose
SO="$OUT/libuinput_jni.so"
[ -f "$SO" ] || fail "Native build completed without producing $SO"

patchelf --remove-rpath "$SO"
READELF="$(llvm-readelf -d "$SO")"
if echo "$READELF" | grep -Eq 'RPATH|RUNPATH'; then
  echo "$READELF" >&2
  fail "libuinput_jni.so still contains RPATH/RUNPATH after cleanup."
fi
if echo "$READELF" | grep -q '/data/data/com.termux'; then
  fail "libuinput_jni.so references the Termux prefix at runtime."
fi
if echo "$READELF" | grep -q 'libc++_shared.so'; then
  echo "$READELF" >&2
  fail "libuinput_jni.so still depends on libc++_shared.so."
fi

python3 scripts/check_native_boundary.py
"${GRADLE_CMD[@]}" :app:assembleDebug -PtermuxPrebuiltNative=true --stacktrace

APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n 1)"
[ -n "$APK" ] || fail "Gradle reported success but no debug APK was found."

if ! unzip -l "$APK" | grep -q 'lib/arm64-v8a/libuinput_jni.so'; then
  fail "APK does not contain lib/arm64-v8a/libuinput_jni.so"
fi

echo
echo "BUILD SUCCESS: $APK"
