#!/usr/bin/env bash
# ==============================================================================
# build_apk.sh: Compiles, links, dexes, aligns, and signs the Smart Alarm APK
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

ANDROID_HOME="${ANDROID_HOME:-/Users/edom/code/android/android_sdk}"
BUILD_TOOLS="${ANDROID_HOME}/build-tools/34.0.0"
PLATFORM_JAR="${ANDROID_HOME}/platforms/android-34/android.jar"

OUTPUT_DIR="${REPO_ROOT}/bazel-bin/app"
BUILD_TMP="/tmp/alarm_build_$(id -u)"

echo "==> Building Smart Alarm APK"
echo "    ANDROID_HOME: ${ANDROID_HOME}"
echo "    BUILD_TOOLS:  ${BUILD_TOOLS}"

rm -rf "${BUILD_TMP}"
mkdir -p "${BUILD_TMP}/compiled_res" "${BUILD_TMP}/gen" "${BUILD_TMP}/classes" "${OUTPUT_DIR}"

# 1. Compile Resources with AAPT2
echo "--> Compiling resources with AAPT2..."
"${BUILD_TOOLS}/aapt2" compile --dir "${REPO_ROOT}/app/res" -o "${BUILD_TMP}/compiled_res.zip"

# 2. Link Resources and Generate R.java
echo "--> Linking resources and generating R.java..."
"${BUILD_TOOLS}/aapt2" link -I "${PLATFORM_JAR}" \
  --manifest "${REPO_ROOT}/app/AndroidManifest.xml" \
  --min-sdk-version 30 \
  --target-sdk-version 34 \
  --java "${BUILD_TMP}/gen" \
  -o "${BUILD_TMP}/app_unaligned.apk" \
  "${BUILD_TMP}/compiled_res.zip" \
  --auto-add-overlay

# 3. Compile Java Sources
echo "--> Compiling Java sources..."
JAVA_FILES=$(find "${REPO_ROOT}/app/src" "${REPO_ROOT}/core/src" -name "*.java")
javac -cp "${PLATFORM_JAR}" \
  -d "${BUILD_TMP}/classes" \
  "${BUILD_TMP}/gen/com/edom/alarm/R.java" \
  ${JAVA_FILES}

# 4. Dex bytecode with D8
echo "--> Converting bytecode to DEX with D8..."
CLASS_FILES=$(find "${BUILD_TMP}/classes" -name "*.class")
"${BUILD_TOOLS}/d8" \
  --lib "${PLATFORM_JAR}" \
  --min-api 30 \
  --output "${BUILD_TMP}" \
  ${CLASS_FILES}

# 5. Package classes.dex into APK
echo "--> Packaging DEX into APK..."
cd "${BUILD_TMP}"
cp app_unaligned.apk app_with_dex.apk
zip -u -q app_with_dex.apk classes.dex

# 6. Zipalign
echo "--> Running zipalign..."
"${BUILD_TOOLS}/zipalign" -v -p 4 app_with_dex.apk app_aligned.apk > /dev/null

# 7. Keystore & Signing with apksigner
KEYSTORE="${SCRIPT_DIR}/debug.keystore"
if [ ! -f "${KEYSTORE}" ]; then
  keytool -genkeypair -v -keystore "${KEYSTORE}" \
    -storepass android -alias androiddebugkey -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
fi

FINAL_APK="${OUTPUT_DIR}/alarm_release_apk.apk"
"${BUILD_TOOLS}/apksigner" sign \
  --ks "${KEYSTORE}" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --out "${FINAL_APK}" \
  app_aligned.apk

APK_SIZE=$(stat -f%z "${FINAL_APK}")
echo "==> APK successfully created: ${FINAL_APK}"
echo "    Size: ${APK_SIZE} bytes ($((APK_SIZE / 1024)) KB)"
