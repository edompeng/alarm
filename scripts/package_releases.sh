#!/usr/bin/env bash
# ==============================================================================
# package_releases.sh: Builds and packages release bundles for mainstream platforms
# (Android Universal, ARM64, ARMv7, x86_64, Linux, macOS).
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DIST_DIR="${REPO_ROOT}/dist"

VERSION="${APP_VERSION_NAME:-}"
VERSION_CODE="${APP_VERSION_CODE:-}"
BUILD_ANDROID=true
BUILD_CLI=true
GENERATE_CHECKSUMS=true

while [[ $# -gt 0 ]]; do
  case "$1" in
    --cli-only)
      BUILD_ANDROID=false
      BUILD_CLI=true
      shift
      ;;
    --android-only)
      BUILD_ANDROID=true
      BUILD_CLI=false
      shift
      ;;
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
      echo "Unknown option: $1"
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

mkdir -p "${DIST_DIR}"

echo "================================================================"
echo "==> Packaging Smart Alarm Releases (v${VERSION})"
echo "    Output Directory: ${DIST_DIR}"
echo "    Build Android:    ${BUILD_ANDROID}"
echo "    Build CLI:        ${BUILD_CLI}"
echo "================================================================"

if [ "${BUILD_ANDROID}" = true ]; then
  if [ -z "${BUILD_TOOLS}" ] || [ ! -d "${BUILD_TOOLS}" ]; then
    echo "Error: Android build-tools not found. Ensure ANDROID_HOME is set." >&2
    exit 1
  fi

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
    
    # Sign with release keystore
    local KEYSTORE="${SCRIPT_DIR}/release.keystore"
    "${BUILD_TOOLS}/apksigner" sign \
      --ks "${KEYSTORE}" \
      --ks-pass "pass:androidrelease" \
      --key-pass "pass:androidrelease" \
      --ks-key-alias "alarmreleasekey" \
      --v1-signing-enabled true \
      --v2-signing-enabled true \
      --v3-signing-enabled true \
      --out "${target_apk}" \
      "${TMP_DIR}/aligned.apk"
      
    "${BUILD_TOOLS}/apksigner" verify "${target_apk}"
    echo "    Created: $(basename "${target_apk}") ($(wc -c < "${target_apk}" | tr -d ' ') bytes)"
  }

  # 2. Produce Mainstream Android ABIs
  package_abi_apk "arm64-v8a"
  package_abi_apk "armeabi-v7a"
  package_abi_apk "x86_64"
fi

if [ "${BUILD_CLI}" = true ]; then
  # 3. Desktop / CLI Bundle for Current Host OS
  if command -v bazel >/dev/null 2>&1; then
    BAZEL_BIN="bazel"
  elif [ -x "/opt/homebrew/bin/bazel" ]; then
    BAZEL_BIN="/opt/homebrew/bin/bazel"
  else
    BAZEL_BIN="bazel"
  fi

  echo "--> Building native CLI binary..."
  "${BAZEL_BIN}" build --copt="-DSMART_ALARM_VERSION=\"${VERSION}\"" //cli:alarm_cli

  CLI_BIN="${REPO_ROOT}/bazel-bin/cli/alarm_cli"
  OS_TYPE="$(uname -s | tr '[:upper:]' '[:lower:]')"
  ARCH_TYPE="$(uname -m)"

  if [ "${OS_TYPE}" = "darwin" ]; then
    BUNDLE_NAME="SmartAlarm-v${VERSION}-macos-${ARCH_TYPE}"
    BUNDLE_DIR="${DIST_DIR}/${BUNDLE_NAME}"
    mkdir -p "${BUNDLE_DIR}/bin" "${BUNDLE_DIR}/config" "${BUNDLE_DIR}/include"
    
    cp "${CLI_BIN}" "${BUNDLE_DIR}/bin/alarm-cli"
    chmod +x "${BUNDLE_DIR}/bin/alarm-cli"
    cp "${REPO_ROOT}/core/src/assets/holidays_2026.json" "${BUNDLE_DIR}/config/"
    cp "${REPO_ROOT}/README.md" "${BUNDLE_DIR}/"
    cp -r "${REPO_ROOT}/core/src/adapter" "${BUNDLE_DIR}/include/"
    
    tar -czf "${DIST_DIR}/${BUNDLE_NAME}.tar.gz" -C "${DIST_DIR}" "${BUNDLE_NAME}"
    rm -rf "${BUNDLE_DIR}"
    echo "--> Created macOS package: ${DIST_DIR}/${BUNDLE_NAME}.tar.gz"
  elif [ "${OS_TYPE}" = "linux" ]; then
    BUNDLE_NAME="SmartAlarm-v${VERSION}-linux-${ARCH_TYPE}"
    BUNDLE_DIR="${DIST_DIR}/${BUNDLE_NAME}"
    mkdir -p "${BUNDLE_DIR}/bin" "${BUNDLE_DIR}/config" "${BUNDLE_DIR}/include"
    
    cp "${CLI_BIN}" "${BUNDLE_DIR}/bin/alarm-cli"
    chmod +x "${BUNDLE_DIR}/bin/alarm-cli"
    cp "${REPO_ROOT}/core/src/assets/holidays_2026.json" "${BUNDLE_DIR}/config/"
    cp "${REPO_ROOT}/README.md" "${BUNDLE_DIR}/"
    cp -r "${REPO_ROOT}/core/src/adapter" "${BUNDLE_DIR}/include/"
    
    tar -czf "${DIST_DIR}/${BUNDLE_NAME}.tar.gz" -C "${DIST_DIR}" "${BUNDLE_NAME}"
    rm -rf "${BUNDLE_DIR}"
    echo "--> Created Linux package: ${DIST_DIR}/${BUNDLE_NAME}.tar.gz"
  fi
fi

# Clean up any leftover .idsig files from apksigner
rm -f "${DIST_DIR}"/*.idsig

if [ "${GENERATE_CHECKSUMS}" = true ]; then
  # 4. Generate SHA256 Checksums
  echo "--> Generating SHA256SUMS.txt..."
  (
    cd "${DIST_DIR}"
    rm -f SHA256SUMS.txt
    if command -v sha256sum >/dev/null 2>&1; then
      sha256sum SmartAlarm-v*.apk SmartAlarm-v*.tar.gz > SHA256SUMS.txt
    elif command -v shasum >/dev/null 2>&1; then
      shasum -a 256 SmartAlarm-v*.apk SmartAlarm-v*.tar.gz > SHA256SUMS.txt
    fi
  )
fi

echo "================================================================"
echo "==> Packaging complete in ${DIST_DIR}:"
ls -lh "${DIST_DIR}"
echo "================================================================"
