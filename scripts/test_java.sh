#!/usr/bin/env bash
# Runs framework-free Java delivery-policy tests without packaging test sources.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TEST_ROOT="${REPO_ROOT}/tests/android"
SUPPORT_SOURCE="${TEST_ROOT}/com/edom/alarm/core/scheduler/AlarmDeliveryTestSupport.java"
BUILD_DIR="${REPO_ROOT}/build/java-policy-tests"

find_javac() {
  local candidate
  local -a candidates=()

  if [[ -n "${JAVA_HOME:-}" ]]; then
    candidates+=("${JAVA_HOME}/bin/javac")
  fi
  if command -v javac >/dev/null 2>&1; then
    candidates+=("$(command -v javac)")
  fi
  if [[ -x /usr/libexec/java_home ]]; then
    candidate="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    if [[ -n "${candidate}" ]]; then
      candidates+=("${candidate}/bin/javac")
    fi
  fi
  candidates+=(
    "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/javac"
    "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/javac"
  )

  for candidate in "${candidates[@]}"; do
    if [[ -x "${candidate}" ]] && "${candidate}" -version 2>&1 | grep -Eq 'javac 17([.[:space:]]|$)'; then
      printf '%s\n' "${candidate}"
      return 0
    fi
  done

  echo "error: JDK 17 javac was not found; set JAVA_HOME to a JDK 17 installation" >&2
  return 1
}

find_platform_jar() {
  local sdk_root
  local -a sdk_roots=()

  if [[ -n "${ANDROID_HOME:-}" ]]; then
    sdk_roots+=("${ANDROID_HOME}")
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    sdk_roots+=("${ANDROID_SDK_ROOT}")
  fi
  sdk_roots+=("${REPO_ROOT}/../android_sdk" "${HOME:-/tmp}/Library/Android/sdk" "/usr/local/lib/android/sdk")

  for sdk_root in "${sdk_roots[@]}"; do
    if [[ -f "${sdk_root}/platforms/android-34/android.jar" ]]; then
      printf '%s\n' "${sdk_root}/platforms/android-34/android.jar"
      return 0
    fi
  done

  echo "error: Android SDK platform android-34 was not found; set ANDROID_HOME or ANDROID_SDK_ROOT" >&2
  return 1
}

JAVAC="$(find_javac)"
PLATFORM_JAR="$(find_platform_jar)"

# Keep this list explicit: framework-bound Android classes must never be pulled
# into JVM policy tests merely because they exist under core/src.
POLICY_SOURCES=(
  "core/src/com/edom/alarm/core/scheduler/AlarmDeliveryModels.java"
  "core/src/com/edom/alarm/core/scheduler/AlarmScheduleStore.java"
  "core/src/com/edom/alarm/core/scheduler/SynchronizedAlarmScheduleStore.java"
  "core/src/com/edom/alarm/core/scheduler/NextOccurrenceCalculator.java"
  "core/src/com/edom/alarm/core/scheduler/AlarmRegistrationGateway.java"
  "core/src/com/edom/alarm/core/scheduler/AlarmDeliveryPolicy.java"
  "core/src/com/edom/alarm/core/scheduler/DefaultNextOccurrenceCalculator.java"
  "core/src/com/edom/alarm/core/scheduler/AlarmScheduleReconciler.java"
)

JAVA_SOURCES=()
for relative_path in "${POLICY_SOURCES[@]}"; do
  if [[ -f "${REPO_ROOT}/${relative_path}" ]]; then
    JAVA_SOURCES+=("${REPO_ROOT}/${relative_path}")
  fi
done

if [[ ! -f "${SUPPORT_SOURCE}" ]]; then
  echo "error: required test support source is missing: ${SUPPORT_SOURCE}" >&2
  exit 1
fi

while IFS= read -r -d '' source_file; do
  JAVA_SOURCES+=("${source_file}")
done < <(find "${TEST_ROOT}" -type f -name '*.java' -print0 | sort -z)

rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}/classes"

echo "==> Compiling framework-free Java policy sources"
echo "    javac: ${JAVAC}"
echo "    android.jar (compile only): ${PLATFORM_JAR}"
"${JAVAC}" -source 17 -target 17 -encoding UTF-8 -cp "${PLATFORM_JAR}" \
  -d "${BUILD_DIR}/classes" "${JAVA_SOURCES[@]}"

run_test_class() {
  local source_file="$1"
  local package_name
  local class_name
  local simple_name

  package_name="$(sed -nE 's/^[[:space:]]*package[[:space:]]+([A-Za-z0-9_.]+)[[:space:]]*;.*/\1/p' "${source_file}" | head -n 1)"
  simple_name="$(basename "${source_file}" .java)"
  class_name="${package_name:+${package_name}.}${simple_name}"
  echo "--> ${class_name}"
  # Deliberately omit android.jar at runtime: its framework stubs must not run.
  "$(dirname "${JAVAC}")/java" -cp "${BUILD_DIR}/classes" "${class_name}"
}

test_count=0
while IFS= read -r -d '' source_file; do
  if [[ "${source_file}" == "${SUPPORT_SOURCE}" ]]; then
    continue
  fi
  run_test_class "${source_file}"
  ((test_count += 1))
done < <(find "${TEST_ROOT}" -type f -name '*Test.java' -print0 | sort -z)

if ((test_count == 0)); then
  echo "--> AlarmDeliveryTestSupport self-check (no *Test.java classes present)"
  "$(dirname "${JAVAC}")/java" -cp "${BUILD_DIR}/classes" \
    com.edom.alarm.core.scheduler.AlarmDeliveryTestSupport
fi

echo "==> Java policy tests passed (${test_count} test class(es))"
