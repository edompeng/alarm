#!/usr/bin/env bash
# ==============================================================================
# build_apk.sh: Compiles, links, dexes, aligns, and signs the Smart Alarm APK.
# The default artifact is debug-signed. A release artifact requires an explicit
# release keystore and passwords supplied through environment variables.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTPUT_PATH=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --repo-root)
      [ "$#" -ge 2 ] || { echo "error: --repo-root requires a path" >&2; exit 2; }
      REPO_ROOT="$(cd "$2" && pwd)"
      shift 2
      ;;
    --output)
      [ "$#" -ge 2 ] || { echo "error: --output requires a path" >&2; exit 2; }
      OUTPUT_PATH="$2"
      shift 2
      ;;
    *)
      echo "error: unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

ANDROID_HOME="${ANDROID_HOME:-/Users/edom/code/android/android_sdk}"
BUILD_TOOLS="${ANDROID_HOME}/build-tools/34.0.0"
PLATFORM_JAR="${ANDROID_HOME}/platforms/android-34/android.jar"

OUTPUT_DIR="${REPO_ROOT}/bazel-bin/app"
BUILD_TMP="$(mktemp -d "${TMPDIR:-/tmp}/alarm_build.XXXXXX")"
trap 'rm -rf "${BUILD_TMP}"' EXIT

if [ -n "${OUTPUT_PATH}" ]; then
  OUTPUT_PARENT="$(dirname "${OUTPUT_PATH}")"
  mkdir -p "${OUTPUT_PARENT}"
  OUTPUT_PATH="$(cd "${OUTPUT_PARENT}" && pwd)/$(basename "${OUTPUT_PATH}")"
fi

echo "==> Building Smart Alarm APK"
echo "    ANDROID_HOME: ${ANDROID_HOME}"
echo "    BUILD_TOOLS:  ${BUILD_TOOLS}"

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
JAVA_FILES=()
while IFS= read -r -d '' source_file; do
  JAVA_FILES+=("${source_file}")
done < <(find "${REPO_ROOT}/app/src" "${REPO_ROOT}/core/src" -name "*.java" -print0 | sort -z)
javac -cp "${PLATFORM_JAR}" \
  -d "${BUILD_TMP}/classes" \
  "${BUILD_TMP}/gen/com/edom/alarm/R.java" \
  "${JAVA_FILES[@]}"

# 4. Dex bytecode with D8
echo "--> Converting bytecode to DEX with D8..."
CLASS_FILES=()
while IFS= read -r -d '' class_file; do
  CLASS_FILES+=("${class_file}")
done < <(find "${BUILD_TMP}/classes" -name "*.class" -print0 | sort -z)
"${BUILD_TOOLS}/d8" \
  --lib "${PLATFORM_JAR}" \
  --min-api 30 \
  --output "${BUILD_TMP}" \
  "${CLASS_FILES[@]}"

# 5. Package classes.dex into APK
echo "--> Packaging DEX into APK..."
cd "${BUILD_TMP}"
cp app_unaligned.apk app_with_dex.apk
zip -u -q app_with_dex.apk classes.dex

# 6. Zipalign
echo "--> Running zipalign..."
"${BUILD_TOOLS}/zipalign" -v -p 4 app_with_dex.apk app_aligned.apk > /dev/null

# 7. Keystore & Signing with apksigner
if [ -n "${ALARM_RELEASE_KEYSTORE:-}" ]; then
  : "${ALARM_RELEASE_KEY_ALIAS:?set ALARM_RELEASE_KEY_ALIAS for release signing}"
  : "${ALARM_RELEASE_KEYSTORE_PASSWORD:?set ALARM_RELEASE_KEYSTORE_PASSWORD for release signing}"
  : "${ALARM_RELEASE_KEY_PASSWORD:?set ALARM_RELEASE_KEY_PASSWORD for release signing}"
  [ -f "${ALARM_RELEASE_KEYSTORE}" ] || {
    echo "error: release keystore does not exist: ${ALARM_RELEASE_KEYSTORE}" >&2
    exit 1
  }
  KEYSTORE="$(cd "$(dirname "${ALARM_RELEASE_KEYSTORE}")" && pwd)/$(basename "${ALARM_RELEASE_KEYSTORE}")"
  KEY_ALIAS="${ALARM_RELEASE_KEY_ALIAS}"
  KS_PASS="env:ALARM_RELEASE_KEYSTORE_PASSWORD"
  KEY_PASS="env:ALARM_RELEASE_KEY_PASSWORD"
  DEFAULT_APK="${OUTPUT_DIR}/alarm_release_apk.apk"
  SIGNING_MODE="release"
else
  KEYSTORE="${SCRIPT_DIR}/debug.keystore"
  if [ ! -f "${KEYSTORE}" ]; then
    keytool -genkeypair -v -keystore "${KEYSTORE}" \
      -storepass android -alias androiddebugkey -keypass android \
      -keyalg RSA -keysize 2048 -validity 10000 \
      -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
  fi
  KEY_ALIAS="androiddebugkey"
  KS_PASS="pass:android"
  KEY_PASS="pass:android"
  DEFAULT_APK="${OUTPUT_DIR}/alarm_debug_apk.apk"
  SIGNING_MODE="debug"
fi

FINAL_APK="${OUTPUT_PATH:-${DEFAULT_APK}}"
rm -f "${FINAL_APK}"
"${BUILD_TOOLS}/apksigner" sign \
  --ks "${KEYSTORE}" \
  --ks-pass "${KS_PASS}" \
  --key-pass "${KEY_PASS}" \
  --ks-key-alias "${KEY_ALIAS}" \
  --out "${FINAL_APK}" \
  app_aligned.apk

APK_SIZE=$(stat -f%z "${FINAL_APK}")
echo "==> APK successfully created: ${FINAL_APK}"
echo "    Signing mode: ${SIGNING_MODE}"
echo "    Size: ${APK_SIZE} bytes ($((APK_SIZE / 1024)) KB)"
