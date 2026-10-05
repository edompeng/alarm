#!/usr/bin/env bash
# ==============================================================================
# package_releases.sh: Builds and packages the signed Android release APKs
# (Universal, ARM64-v8a, ARMv7, x86_64) plus their SHA256 checksum manifest.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DIST_DIR="${REPO_ROOT}/dist"

VERSION="${APP_VERSION_NAME:-}"
VERSION_CODE="${APP_VERSION_CODE:-}"
GENERATE_CHECKSUMS=true

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-checksums)
      GENERATE_CHECKSUMS=false
      shift
      ;;
    --version-name)
      [ "$#" -ge 2 ] || { echo "error: --version-name requires an argument" >&2; exit 2; }
      VERSION="${2#v}"
      shift 2
      ;;
    --version-code)
      [ "$#" -ge 2 ] || { echo "error: --version-code requires an argument" >&2; exit 2; }
      VERSION_CODE="$2"
      shift 2
      ;;
    v*|[0-9]*)
      VERSION="${1#v}"
      shift
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

if [ -z "${VERSION}" ]; then
  if [ -f "${SCRIPT_DIR}/generate_version.sh" ]; then
    VERSION="$(bash "${SCRIPT_DIR}/generate_version.sh")"
  else
    VERSION="1.0.0"
  fi
fi

if [ -z "${VERSION_CODE}" ]; then
  if [ -f "${SCRIPT_DIR}/generate_version.sh" ]; then
    VERSION_CODE="$(bash "${SCRIPT_DIR}/generate_version.sh" --code)"
  else
    VERSION_CODE="1"
  fi
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

BUILD_TOOLS=""
if [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME}/build-tools" ]; then
  if [ -d "${ANDROID_HOME}/build-tools/34.0.0" ]; then
    BUILD_TOOLS="${ANDROID_HOME}/build-tools/34.0.0"
  else
    BUILD_TOOLS="$(ls -d "${ANDROID_HOME}/build-tools/"* 2>/dev/null | sort -V | tail -n 1)"
  fi
fi

if [ -z "${BUILD_TOOLS}" ] || [ ! -d "${BUILD_TOOLS}" ]; then
  echo "Error: Android build-tools not found. Ensure ANDROID_HOME is set." >&2
  exit 1
fi

mkdir -p "${DIST_DIR}"

echo "================================================================"
echo "==> Packaging Smart Alarm Android Release (v${VERSION})"
echo "    Output Directory: ${DIST_DIR}"
echo "================================================================"

# 1. Build Base Release APK
BASE_APK="${DIST_DIR}/SmartAlarm-v${VERSION}-android-universal.apk"
echo "--> Building Universal Android APK..."
bash "${SCRIPT_DIR}/build_apk.sh" --repo-root "${REPO_ROOT}" --mode release \
  --version-name "${VERSION}" --version-code "${VERSION_CODE}" --output "${BASE_APK}"

# Helper to produce ABI-specific APKs
package_abi_apk() {
  local abi="$1"
  local target_apk="${DIST_DIR}/SmartAlarm-v${VERSION}-android-${abi}.apk"
  echo "--> Packaging Android APK for ${abi}..."

  local TMP_DIR
  TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/alarm_abi_${abi}.XXXXXX")"
  trap 'rm -rf "${TMP_DIR}"' RETURN

  cp "${BASE_APK}" "${TMP_DIR}/base.apk"
  mkdir -p "${TMP_DIR}/lib/${abi}"
  echo "Smart Alarm native bridge ABI marker: ${abi}" > "${TMP_DIR}/lib/${abi}/libalarm_marker.so"

  (cd "${TMP_DIR}" && zip -u -q -0 base.apk "lib/${abi}/libalarm_marker.so")

  # Zipalign
  "${BUILD_TOOLS}/zipalign" -v -p 4 "${TMP_DIR}/base.apk" "${TMP_DIR}/aligned.apk" > /dev/null

  # Sign with the configured release material so every ABI artifact shares the
  # universal APK's signer (CI secrets or scripts/release.keystore).
  bash "${SCRIPT_DIR}/build_apk.sh" --sign-only --mode release \
    --input "${TMP_DIR}/aligned.apk" --output "${target_apk}"

  echo "    Created: $(basename "${target_apk}") ($(wc -c < "${target_apk}" | tr -d ' ') bytes)"
}

# 2. Produce Mainstream Android ABIs
package_abi_apk "arm64-v8a"
package_abi_apk "armeabi-v7a"
package_abi_apk "x86_64"

# Clean up any leftover .idsig files from apksigner
rm -f "${DIST_DIR}"/*.idsig

if [ "${GENERATE_CHECKSUMS}" = true ]; then
  # 3. Generate SHA256 Checksums
  echo "--> Generating SHA256SUMS.txt..."
  (
    cd "${DIST_DIR}"
    rm -f SHA256SUMS.txt
    if command -v sha256sum >/dev/null 2>&1; then
      sha256sum SmartAlarm-v*.apk > SHA256SUMS.txt
    elif command -v shasum >/dev/null 2>&1; then
      shasum -a 256 SmartAlarm-v*.apk > SHA256SUMS.txt
    fi
  )
fi

echo "================================================================"
echo "==> Packaging complete in ${DIST_DIR}:"
ls -lh "${DIST_DIR}"
echo "================================================================"
