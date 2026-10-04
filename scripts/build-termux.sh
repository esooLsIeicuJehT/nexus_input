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
need curl
need unzip

WRAPPER_PROPS="$ROOT/gradle/wrapper/gradle-wrapper.properties"
[ -f "$WRAPPER_PROPS" ] || fail "Missing $WRAPPER_PROPS; cannot determine the pinned Gradle version."
DIST_URL="$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPS" | tail -n 1 | sed 's#\\:#:#g')"
[ -n "$DIST_URL" ] || fail "distributionUrl is missing from $WRAPPER_PROPS"
DIST_FILE="${DIST_URL##*/}"
case "$DIST_FILE" in
  gradle-*-bin.zip) ;;
  *) fail "Unexpected Gradle distribution '$DIST_FILE'. Expected gradle-<version>-bin.zip." ;;
esac
PINNED_GRADLE_VERSION="${DIST_FILE#gradle-}"
PINNED_GRADLE_VERSION="${PINNED_GRADLE_VERSION%-bin.zip}"

if [ -f "$ROOT/gradlew" ] && [ -f "$ROOT/gradle/wrapper/gradle-wrapper.jar" ]; then
  chmod +x "$ROOT/gradlew"
  GRADLE_CMD=("$ROOT/gradlew")
  GRADLE_SOURCE="repository wrapper"
else
  BOOTSTRAP_ROOT="$HOME/.gradle/nexus-input-bootstrap"
  ZIP_PATH="$BOOTSTRAP_ROOT/$DIST_FILE"
  DIST_DIR="$BOOTSTRAP_ROOT/gradle-$PINNED_GRADLE_VERSION"
  GRADLE_BIN="$DIST_DIR/bin/gradle"
  mkdir -p "$BOOTSTRAP_ROOT"

  if [ ! -x "$GRADLE_BIN" ]; then
    if [ ! -f "$ZIP_PATH" ]; then
      echo "Gradle wrapper files are incomplete; downloading pinned Gradle $PINNED_GRADLE_VERSION"
      echo "Source: $DIST_URL"
      TMP_ZIP="$ZIP_PATH.part"
      rm -f "$TMP_ZIP"
      curl -fL --retry 3 --retry-delay 2 -o "$TMP_ZIP" "$DIST_URL" || fail "Unable to download pinned Gradle distribution."
      mv "$TMP_ZIP" "$ZIP_PATH"
    fi
    rm -rf "$DIST_DIR"
    unzip -q "$ZIP_PATH" -d "$BOOTSTRAP_ROOT" || fail "Unable to unpack $ZIP_PATH"
  fi

  [ -x "$GRADLE_BIN" ] || fail "Pinned Gradle binary was not created at $GRADLE_BIN"
  GRADLE_CMD=("$GRADLE_BIN")
  GRADLE_SOURCE="pinned distribution bootstrap"
fi

ACTUAL_GRADLE_VERSION="$("${GRADLE_CMD[@]}" --version | sed -n 's/^Gradle //p' | head -n 1)"
[ "$ACTUAL_GRADLE_VERSION" = "$PINNED_GRADLE_VERSION" ] || fail "Gradle version mismatch: expected $PINNED_GRADLE_VERSION, got '${ACTUAL_GRADLE_VERSION:-unknown}'."

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

[ -x "$PREFIX/bin/aidl" ] || fail "Termux AIDL is not executable at $PREFIX/bin/aidl"
[ -L "$BUILD_TOOLS_DIR/aidl" ] || fail "SDK build-tools AIDL override was not created."
[ "$(readlink "$BUILD_TOOLS_DIR/aidl")" = "$PREFIX/bin/aidl" ] || fail "SDK build-tools AIDL does not point to Termux AIDL."

echo "Using Gradle: $PINNED_GRADLE_VERSION ($GRADLE_SOURCE)"
echo "Using SDK: $SDK_DIR"
echo "Using build-tools: $BUILD_TOOLS_DIR"
echo "Using Termux AIDL: $PREFIX/bin/aidl"
ls -l "$BUILD_TOOLS_DIR/aidl"

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

# AGP 9.1 can otherwise reuse native packaging state across Termux runs. Remove only
# native packaging intermediates, then rebuild the merge stages with configuration-cache
# reuse disabled so the freshly-created jniLibs tree is discovered in this invocation.
rm -rf \
  app/build/intermediates/merged_jni_libs \
  app/build/intermediates/merged_native_libs \
  app/build/intermediates/stripped_native_libs \
  app/build/intermediates/packaged_native_libs

"${GRADLE_CMD[@]}" \
  :app:mergeDebugJniLibFolders \
  :app:mergeDebugNativeLibs \
  -PtermuxPrebuiltNative=true \
  --rerun-tasks \
  --no-configuration-cache \
  --stacktrace

MERGED_SO="$(find app/build/intermediates -type f -name 'libuinput_jni.so' -path '*merged_native_libs*' -print -quit 2>/dev/null || true)"
if [ -z "$MERGED_SO" ]; then
  echo "Source JNI library:" >&2
  ls -lh "$SO" >&2 || true
  echo "Native merge outputs:" >&2
  find app/build/intermediates -type f -path '*native*' -name '*.so' -print >&2 2>/dev/null || true
  fail "Gradle did not carry libuinput_jni.so into the merged native-libs stage."
fi

echo "Merged JNI verified: $MERGED_SO"

rm -f app/build/outputs/apk/debug/*.apk
"${GRADLE_CMD[@]}" \
  :app:assembleDebug \
  -PtermuxPrebuiltNative=true \
  --no-configuration-cache \
  --stacktrace

APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n 1)"
[ -n "$APK" ] || fail "Gradle reported success but no debug APK was found."

if ! unzip -l "$APK" | grep -q 'lib/arm64-v8a/libuinput_jni.so'; then
  echo "APK native entries:" >&2
  unzip -l "$APK" | grep 'lib/' >&2 || true
  fail "APK does not contain lib/arm64-v8a/libuinput_jni.so"
fi

echo
echo "BUILD SUCCESS: $APK"
