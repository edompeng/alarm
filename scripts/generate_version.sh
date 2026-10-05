#!/usr/bin/env bash
# ==============================================================================
# generate_version.sh: Generates dynamic release version based on tag + date/time.
# Format: <tag>-<YYYYMMDDHHMMSS> (e.g. v1.0.0-20261005130530), China Standard Time.
# Every invocation stamps the live time so each push publishes a distinct release.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

OUTPUT_MODE="version"
INPUT_TAG=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      OUTPUT_MODE="tag"
      shift
      ;;
    --code)
      OUTPUT_MODE="code"
      shift
      ;;
    --base)
      OUTPUT_MODE="base"
      shift
      ;;
    --help|-h)
      echo "Usage: $0 [base_tag] [--tag|--code|--base]"
      echo "Generates dynamic release version formatted as: <base_tag>-<YYYYMMDDHHMMSS>"
      exit 0
      ;;
    *)
      if [ -z "${INPUT_TAG}" ]; then
        INPUT_TAG="$1"
      fi
      shift
      ;;
  esac
done

# Resolve base tag if not provided
if [ -z "${INPUT_TAG}" ]; then
  INPUT_TAG="${BASE_TAG:-}"
fi

if [ -z "${INPUT_TAG}" ]; then
  if [ "${GITHUB_REF_TYPE:-}" = "tag" ] && [ -n "${GITHUB_REF_NAME:-}" ]; then
    INPUT_TAG="${GITHUB_REF_NAME}"
  else
    INPUT_TAG="$(git -C "${REPO_ROOT}" describe --tags --abbrev=0 2>/dev/null || echo "")"
  fi
fi

if [ -z "${INPUT_TAG}" ]; then
  INPUT_TAG="v1.0.0"
fi

# Clean leading 'v'
CLEAN_BASE="${INPUT_TAG#v}"

# Drop a trailing build timestamp so a previous release tag (e.g. 1.0.0-202610051310)
# contributes only its base version (1.0.0) to the new version.
BASE_VERSION="$(printf '%s' "${CLEAN_BASE}" | sed -E 's/-[0-9]{8,}$//')"
if [ -z "${BASE_VERSION}" ]; then
  BASE_VERSION="1.0.0"
fi

# Always stamp the live build time (China Standard Time, second precision) so every
# push produces a new version/tag instead of reusing the previous release timestamp.
DATE_TIME="$(TZ='Asia/Shanghai' date +'%Y%m%d%H%M%S')"
VERSION="${BASE_VERSION}-${DATE_TIME}"

VERSION_TAG="v${VERSION#v}"
VERSION_CODE="$(date +%s)"

# If running in GitHub Actions with GITHUB_OUTPUT available
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "version=${VERSION}" >> "${GITHUB_OUTPUT}"
  echo "version_tag=${VERSION_TAG}" >> "${GITHUB_OUTPUT}"
  echo "version_code=${VERSION_CODE}" >> "${GITHUB_OUTPUT}"
fi

case "${OUTPUT_MODE}" in
  tag)
    echo "${VERSION_TAG}"
    ;;
  code)
    echo "${VERSION_CODE}"
    ;;
  base)
    echo "${BASE_VERSION}"
    ;;
  version|*)
    echo "${VERSION}"
    ;;
esac
