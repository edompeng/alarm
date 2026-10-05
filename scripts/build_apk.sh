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
BUILD_MODE="debug"
VERSION_NAME="${APP_VERSION_NAME:-}"
VERSION_CODE="${APP_VERSION_CODE:-}"
SIGN_ONLY=false
INPUT_APK=""

export HOME="${HOME:-/tmp}"
export LC_ALL="C.UTF-8"
export LANG="C.UTF-8"

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
    --mode)
      [ "$#" -ge 2 ] || { echo "error: --mode requires an argument" >&2; exit 2; }
      BUILD_MODE="$2"
      shift 2
      ;;
    --version-name)
      [ "$#" -ge 2 ] || { echo "error: --version-name requires an argument" >&2; exit 2; }
      VERSION_NAME="$2"
      shift 2
      ;;
    --version-code)
      [ "$#" -ge 2 ] || { echo "error: --version-code requires an argument" >&2; exit 2; }
      VERSION_CODE="$2"
      shift 2
      ;;
    --sign-only)
      SIGN_ONLY=true
      shift 1
      ;;
    --input)
      [ "$#" -ge 2 ] || { echo "error: --input requires a path" >&2; exit 2; }
      INPUT_APK="$2"
      shift 2
      ;;
    --release)
      BUILD_MODE="release"
      shift 1
      ;;
    --debug)
      BUILD_MODE="debug"
      shift 1
      ;;
    *)
      echo "error: unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [ -z "${VERSION_NAME}" ]; then
  if [ -f "${SCRIPT_DIR}/generate_version.sh" ]; then
    VERSION_NAME="$(bash "${SCRIPT_DIR}/generate_version.sh")"
  else
    VERSION_NAME="1.0.0"
  fi
fi

if [ -z "${VERSION_CODE}" ]; then
  if [ -f "${SCRIPT_DIR}/generate_version.sh" ]; then
    VERSION_CODE="$(bash "${SCRIPT_DIR}/generate_version.sh" --code)"
  else
    VERSION_CODE="1"
  fi
fi

if [ "${SIGN_ONLY}" = true ] && { [ -z "${INPUT_APK}" ] || [ -z "${OUTPUT_PATH}" ]; }; then
  echo "error: --sign-only requires both --input <apk> and --output <apk>" >&2
  exit 2
fi

if [ -n "${ALARM_RELEASE_KEYSTORE:-}" ]; then
  BUILD_MODE="release"
fi

# Resolve ANDROID_HOME / ANDROID_SDK_ROOT
if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    ANDROID_HOME="${ANDROID_SDK_ROOT}"
  elif [ -d "/usr/local/lib/android/sdk" ]; then
    ANDROID_HOME="/usr/local/lib/android/sdk"
  elif [ -d "/Users/edom/code/android/android_sdk" ]; then
    ANDROID_HOME="/Users/edom/code/android/android_sdk"
  elif [ -n "${HOME:-}" ] && [ -d "${HOME}/Library/Android/sdk" ]; then
    ANDROID_HOME="${HOME}/Library/Android/sdk"
  fi
fi

if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "${ANDROID_HOME}" ]; then
  echo "error: Android SDK not found; set ANDROID_HOME or ANDROID_SDK_ROOT" >&2
  exit 1
fi

if [ -d "${ANDROID_HOME}/build-tools/34.0.0" ]; then
  BUILD_TOOLS="${ANDROID_HOME}/build-tools/34.0.0"
elif [ -d "${ANDROID_HOME}/build-tools" ]; then
  BUILD_TOOLS="$(ls -d "${ANDROID_HOME}/build-tools/"* 2>/dev/null | sort -V | tail -n 1)"
else
  echo "error: Android build-tools not found under ${ANDROID_HOME}/build-tools" >&2
  exit 1
fi

JAVAC_BIN="javac"
if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/javac" ]; then
  JAVAC_BIN="${JAVA_HOME}/bin/javac"
fi

KEYTOOL_BIN="keytool"
if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/keytool" ]; then
  KEYTOOL_BIN="${JAVA_HOME}/bin/keytool"
fi

# Resolves the keystore, alias and passwords used for signing. Release builds use
# ALARM_RELEASE_KEYSTORE when provided (CI secrets) and otherwise fall back to a
# local scripts/release.keystore so both paths stay byte-for-byte compatible.
resolve_signing_material() {
  if [ "${BUILD_MODE}" = "release" ]; then
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
      SIGNING_MODE="release (configured keystore)"
    else
      KEYSTORE="${SCRIPT_DIR}/release.keystore"
      if [ ! -f "${KEYSTORE}" ]; then
        echo "--> Generating standalone release keystore..."
        "${KEYTOOL_BIN}" -genkeypair -v -keystore "${KEYSTORE}" \
          -storepass androidrelease -alias alarmreleasekey -keypass androidrelease \
          -keyalg RSA -keysize 2048 -validity 10000 \
          -dname "CN=Smart Alarm Release,O=Edom,C=CN" 2>/dev/null
        echo "WARNING: a throwaway release keystore was generated at ${KEYSTORE}." >&2
        echo "         Signed APKs from different machines/CI runs will NOT upgrade each other." >&2
        echo "         Configure ALARM_RELEASE_KEYSTORE/ALARM_RELEASE_KEY_ALIAS/" >&2
        echo "         ALARM_RELEASE_KEYSTORE_PASSWORD/ALARM_RELEASE_KEY_PASSWORD to reuse one key." >&2
      fi
      KEY_ALIAS="alarmreleasekey"
      KS_PASS="pass:androidrelease"
      KEY_PASS="pass:androidrelease"
      SIGNING_MODE="release (local keystore)"
    fi
    DEFAULT_APK="${OUTPUT_DIR}/alarm_release_apk.apk"
  else
    KEYSTORE="${SCRIPT_DIR}/debug.keystore"
    if [ ! -f "${KEYSTORE}" ]; then
      echo "--> Generating debug keystore..."
      "${KEYTOOL_BIN}" -genkeypair -v -keystore "${KEYSTORE}" \
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
}

sign_apk() {
  local input_apk="$1"
  local output_apk="$2"
  rm -f "${output_apk}"
  "${BUILD_TOOLS}/apksigner" sign \
    --ks "${KEYSTORE}" \
    --ks-pass "${KS_PASS}" \
    --key-pass "${KEY_PASS}" \
    --ks-key-alias "${KEY_ALIAS}" \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "${output_apk}" \
    "${input_apk}"
  "${BUILD_TOOLS}/apksigner" verify "${output_apk}" > /dev/null
}

if [ -f "${ANDROID_HOME}/platforms/android-34/android.jar" ]; then
  PLATFORM_JAR="${ANDROID_HOME}/platforms/android-34/android.jar"
elif [ -d "${ANDROID_HOME}/platforms" ]; then
  PLATFORM_JAR="$(ls -d "${ANDROID_HOME}/platforms/android-"*/android.jar 2>/dev/null | sort -V | tail -n 1)"
else
  echo "error: android.jar not found under ${ANDROID_HOME}/platforms" >&2
  exit 1
fi

OUTPUT_DIR="${REPO_ROOT}/bazel-bin/app"
BUILD_TMP="$(mktemp -d "${TMPDIR:-/tmp}/alarm_build.XXXXXX")"
trap 'rm -rf "${BUILD_TMP}"' EXIT

if [ -n "${OUTPUT_PATH}" ]; then
  OUTPUT_PARENT="$(dirname "${OUTPUT_PATH}")"
  mkdir -p "${OUTPUT_PARENT}"
  OUTPUT_PATH="$(cd "${OUTPUT_PARENT}" && pwd)/$(basename "${OUTPUT_PATH}")"
fi

# Signing-only mode: re-sign an existing APK with the resolved signing material.
# Used by package_releases.sh so every ABI artifact shares the universal signer.
if [ "${SIGN_ONLY}" = true ]; then
  [ -f "${INPUT_APK}" ] || {
    echo "error: input APK does not exist: ${INPUT_APK}" >&2
    exit 1
  }
  resolve_signing_material
  echo "==> Signing APK (${SIGNING_MODE})"
  echo "    Input:  ${INPUT_APK}"
  echo "    Output: ${OUTPUT_PATH}"
  sign_apk "${INPUT_APK}" "${OUTPUT_PATH}"
  echo "==> APK successfully signed: ${OUTPUT_PATH}"
  exit 0
fi

echo "==> Building Smart Alarm APK (${BUILD_MODE} mode)"
echo "    ANDROID_HOME: ${ANDROID_HOME}"
echo "    BUILD_TOOLS:  ${BUILD_TOOLS}"
echo "    VERSION_NAME: ${VERSION_NAME}"
echo "    VERSION_CODE: ${VERSION_CODE}"

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
  --version-code "${VERSION_CODE}" \
  --version-name "${VERSION_NAME}" \
  --replace-version \
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
"${JAVAC_BIN}" -encoding UTF-8 -cp "${PLATFORM_JAR}" \
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
resolve_signing_material
FINAL_APK="${OUTPUT_PATH:-${DEFAULT_APK}}"
sign_apk app_aligned.apk "${FINAL_APK}"

APK_SIZE=$(wc -c < "${FINAL_APK}" | tr -d ' ')
echo "==> APK successfully created: ${FINAL_APK}"
echo "    Signing mode: ${SIGNING_MODE}"
echo "    Size: ${APK_SIZE} bytes ($((APK_SIZE / 1024)) KB)"
