#!/usr/bin/env bash
# ==============================================================================
# generate_version.sh: Generates dynamic release version based on tag + date/time.
# Format: <tag>-<YYYYMMDDHHMM> (e.g. v1.0.0-202610051305)
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
      echo "Generates dynamic release version formatted as: <base_tag>-<YYYYMMDDHHMM>"
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

# If base tag already contains timestamp (e.g. 1.0.0-202610051305), reuse directly
if [[ "${CLEAN_BASE}" =~ -[0-9]{8} ]]; then
  VERSION="${CLEAN_BASE}"
else
  # Use China Standard Time (Asia/Shanghai) for consistent date/time representation
  DATE_TIME="$(TZ='Asia/Shanghai' date +'%Y%m%d%H%M')"
  VERSION="${CLEAN_BASE}-${DATE_TIME}"
fi

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
    echo "${CLEAN_BASE%%-*}"
    ;;
  version|*)
    echo "${VERSION}"
    ;;
esac
