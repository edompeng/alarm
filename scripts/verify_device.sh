#!/usr/bin/env bash
# ==============================================================================
# verify_device.sh: Caller-supplied physical-device delivery verification matrix
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <authorized Samsung or iQOO/Vivo serial>" >&2
    exit 2
fi

SERIAL="$1"
if [ -n "${ADB:-}" ]; then
    ADB="${ADB}"
elif command -v adb > /dev/null 2>&1; then
    ADB="$(command -v adb)"
else
    ADB="/opt/homebrew/bin/adb"
fi
PACKAGE_NAME="com.edom.alarm"
APK_PATH="${REPO_ROOT}/bazel-bin/app/alarm_debug_apk.apk"
BASELINE_DIR="${REPO_ROOT}/build/verification/alarm-delivery/baseline-device"
SAFE_SERIAL="${SERIAL//[^A-Za-z0-9_.-]/_}"
BASELINE_FILE="${BASELINE_DIR}/${SAFE_SERIAL}-$(date +%Y%m%dT%H%M%S).txt"
DELIVERY_WAIT_SECONDS="${ALARM_DELIVERY_WAIT_SECONDS:-0}"
BASELINE_ONLY="${ALARM_DELIVERY_BASELINE_ONLY:-0}"

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
UNVERIFIED_TESTS=0
BUILD_READY=0

mkdir -p "${BASELINE_DIR}"
{
    echo "Smart Alarm physical-device delivery baseline"
    echo "serial=${SERIAL}"
    echo "started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "required_hardware=Samsung One UI or iQOO/Vivo OriginOS"
    echo "component_injection_rule=diagnostic only; never acceptance evidence"
} > "${BASELINE_FILE}"

record_case() {
    local state="$1"
    local name="$2"
    local detail="$3"
    printf '%s | %s | %s\n' "${state}" "${name}" "${detail}" | tee -a "${BASELINE_FILE}"
}

run_step() {
    local name="$1"
    shift
    if [ "${BASELINE_ONLY}" = "1" ]; then
        case "${name}" in
            "Checking physical device connectivity and authorization"|\
            "Verifying device hardware and Android version >= 11"|\
            "Compiling and signing debug APK"|\
            "Installing debug APK on physical device"|\
            "Granting POST_NOTIFICATIONS and SCHEDULE_EXACT_ALARM privileges") ;;
            *)
                record_case "SKIPPED" "${name}" "ALARM_DELIVERY_BASELINE_ONLY=1"
                return 0
                ;;
        esac
    fi
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -n "[TEST ${TOTAL_TESTS}] ${name}... "
    if "$@"; then
        echo "PASSED"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo "FAILED"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
}

run_delivery_case() {
    local name="$1"
    shift
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -n "[DELIVERY ${TOTAL_TESTS}] ${name}... "
    if "$@"; then
        echo "PASSED"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        record_case "PASS" "${name}" "real registered-alarm evidence retained"
    else
        local result=$?
        if [ "${result}" -eq 2 ]; then
            echo "UNVERIFIED"
            UNVERIFIED_TESTS=$((UNVERIFIED_TESTS + 1))
            record_case "UNVERIFIED" "${name}" "prerequisite, OEM behavior, or controllable occurrence unavailable"
        else
            echo "FAILED"
            FAILED_TESTS=$((FAILED_TESTS + 1))
            record_case "FAIL" "${name}" "real registered-alarm assertion failed"
        fi
    fi
}

run_diagnostic() {
    local name="$1"
    shift
    if [ "${BASELINE_ONLY}" = "1" ]; then
        record_case "SKIPPED" "${name}" "ALARM_DELIVERY_BASELINE_ONLY=1 excludes injected diagnostics"
        return 0
    fi
    if "$@"; then
        echo "[DIAGNOSTIC] ${name}: injected component execution only"
        record_case "DIAGNOSTIC" "${name}" "not acceptance evidence"
    else
        echo "[DIAGNOSTIC] ${name}: unavailable"
        record_case "DIAGNOSTIC" "${name}" "not acceptance evidence"
    fi
}

echo "========================================================================"
echo " Starting Smart Alarm Physical Device Verification Pipeline"
echo " Target Device: ${SERIAL}"
echo "========================================================================"

if [ "$("${ADB}" -s "${SERIAL}" get-state 2>/dev/null || true)" != "device" ]; then
    record_case "UNVERIFIED" "Physical-device delivery matrix" "serial is unavailable or unauthorized"
    echo "==> UNVERIFIED: device ${SERIAL} is unavailable; baseline: ${BASELINE_FILE}"
    exit 2
fi

# Step 1: Physical Device Online & Authorized
test_device_online() {
    local state
    state=$("${ADB}" -s "${SERIAL}" get-state 2>/dev/null || echo "offline")
    echo -n "(State: ${state}) "
    [ "${state}" = "device" ]
}
run_step "Checking physical device connectivity and authorization" test_device_online

# Step 2: Query Device Hardware & OS Info
test_device_info() {
    local brand model release sdk
    brand=$("${ADB}" -s "${SERIAL}" shell getprop ro.product.manufacturer | tr -d '\r')
    model=$("${ADB}" -s "${SERIAL}" shell getprop ro.product.model | tr -d '\r')
    release=$("${ADB}" -s "${SERIAL}" shell getprop ro.build.version.release | tr -d '\r')
    sdk=$("${ADB}" -s "${SERIAL}" shell getprop ro.build.version.sdk | tr -d '\r')
    echo -n "(${brand} ${model}, Android ${release}, API ${sdk}) "
    [ "${sdk}" -ge 30 ]
}
run_step "Verifying device hardware and Android version >= 11" test_device_info

# Step 3: Build Release APK
test_build_apk() {
    if bash "${SCRIPT_DIR}/build_apk.sh" > /dev/null && [ -f "${APK_PATH}" ]; then
        BUILD_READY=1
        return 0
    fi
    return 1
}
run_step "Compiling and signing debug APK" test_build_apk

# Step 4: Install Release APK
test_install_apk() {
    [ "${BUILD_READY}" -eq 1 ] || return 1
    # Direct ADB installation must either succeed or report its normal failure;
    # this script never taps a package installer or supplies account credentials.
    "${ADB}" -s "${SERIAL}" install -r -d "${APK_PATH}" | grep -q "Success"
}
run_step "Installing debug APK on physical device" test_install_apk

# Step 5: Grant Runtime Permissions & Exact Alarm
test_grant_permissions() {
    [ "${BUILD_READY}" -eq 1 ] || return 1
    "${ADB}" -s "${SERIAL}" shell pm grant "${PACKAGE_NAME}" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
    "${ADB}" -s "${SERIAL}" shell cmd appops set "${PACKAGE_NAME}" SCHEDULE_EXACT_ALARM allow 2>/dev/null || true
}
run_step "Granting POST_NOTIFICATIONS and SCHEDULE_EXACT_ALARM privileges" test_grant_permissions

# Step 6: Cold Launch MainActivity
test_launch_main() {
    "${ADB}" -s "${SERIAL}" shell input keyevent 224 > /dev/null 2>&1 || true # Wake up screen
    local result
    result=$("${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.MainActivity" 2>&1)
    echo "${result}" | grep -q "Starting:" || return 1
    sleep 1
    "${ADB}" -s "${SERIAL}" shell dumpsys window 2>/dev/null | grep -q "${PACKAGE_NAME}/.ui.MainActivity"
}
run_step "Launching MainActivity and asserting top-level window focus" test_launch_main

# Step 7: OriginOS / Vivo Whitelist Intent Verification
test_oem_intent() {
    local resolve
    resolve=$("${ADB}" -s "${SERIAL}" shell pm resolve-activity -a android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS 2>/dev/null || echo "")
    echo "${resolve}" | grep -qiE "priority|match"
}
run_step "Verifying battery optimization & OEM whitelist intent resolution" test_oem_intent

# Step 8: Statutory Holiday Generator Script Output
test_holiday_generator() {
    python3 "${REPO_ROOT}/scripts/generate_holiday_config.py" --year 2026 --output /tmp/holiday_device_test.json > /dev/null
    python3 -c "
import json
data = json.load(open('/tmp/holiday_device_test.json'))
assert data['year'] == 2026
assert len(data['holidays']) > 10
assert len(data['workdays']) > 0
overlap = set(data['holidays']).intersection(set(data['workdays']))
assert len(overlap) == 0
"
}
run_step "Executing and validating scripts/generate_holiday_config.py" test_holiday_generator

# Step 9: Killed-Process Wakeup on OriginOS (FLAG_INCLUDE_STOPPED_PACKAGES)
test_killed_app_wakeup() {
    # 1. Force stop package completely (entering FLAG_STOPPED state)
    "${ADB}" -s "${SERIAL}" shell am force-stop "${PACKAGE_NAME}"
    sleep 1

    # 2. Assert package is in stopped state
    local pkg_dump
    pkg_dump=$("${ADB}" -s "${SERIAL}" shell dumpsys package "${PACKAGE_NAME}")
    echo "${pkg_dump}" | grep -q "stopped=true"

    # 3. Simulate direct AlarmManager Activity trigger with FLAG_INCLUDE_STOPPED_PACKAGES (0x20)
    # This matches am.setAlarmClock() with PendingIntent.getActivity()
    "${ADB}" -s "${SERIAL}" shell am start \
        -a com.edom.alarm.ACTION_ALARM_TRIGGER \
        --el extra_alarm_id 1001 \
        -f 0x10000020 \
        -n "${PACKAGE_NAME}/.ui.RingingActivity" > /dev/null
    sleep 2

    # 4. Assert RingingActivity woke up over lockscreen and acquired focus
    local top_activity
    top_activity=$("${ADB}" -s "${SERIAL}" shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" | tr -d '\r')
    echo "${top_activity}" | grep -q "RingingActivity"

    # 5. Dismiss ringing activity
    "${ADB}" -s "${SERIAL}" shell input keyevent 4 # KEYCODE_BACK
    sleep 1
}
run_diagnostic "Force-stop plus injected RingingActivity sequence" test_killed_app_wakeup

# Step 10: Vacation Calendar & Clear Skips Verification
test_vacation_and_clear_skips() {
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.MainActivity" --activity-clear-top > /dev/null
    sleep 1
    # Long press first alarm card to open context menu
    "${ADB}" -s "${SERIAL}" shell input swipe 400 600 400 600 1200
    sleep 1
    # Tap first item (Vacation Mode)
    "${ADB}" -s "${SERIAL}" shell input tap 400 470
    sleep 1

    # Dump UI hierarchy to check Clear Skips button
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_device_cal.xml > /dev/null 2>&1 || true
    local dump
    dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_device_cal.xml 2>/dev/null || echo "")
    "${ADB}" -s "${SERIAL}" shell input keyevent 4 # BACK
    sleep 0.5

    echo "${dump}" | grep -q "com.edom.alarm:id/btn_clear_calendar"
}
run_step "Testing Vacation Calendar UI and 'Clear Skips' button on device" test_vacation_and_clear_skips

# Samsung One UI and iQOO/Vivo OriginOS acceptance matrix. A package/component
# injection is diagnostic only; PASS requires an existing AlarmManager operation
# and receiver-to-service evidence after the selected exit state.
capture_alarm_dump() {
    "${ADB}" -s "${SERIAL}" shell dumpsys alarm 2>/dev/null | tr -d '\r' \
        > "${BASELINE_DIR}/${SAFE_SERIAL}-dumpsys-alarm.txt"
    "${ADB}" -s "${SERIAL}" shell dumpsys activity intents 2>/dev/null | tr -d '\r' \
        >> "${BASELINE_DIR}/${SAFE_SERIAL}-dumpsys-alarm.txt"
}

has_real_registered_alarm() {
    [ "${BUILD_READY}" -eq 1 ] || return 2
    capture_alarm_dump
    local dump_file="${BASELINE_DIR}/${SAFE_SERIAL}-dumpsys-alarm.txt"
    grep -q "${PACKAGE_NAME}" "${dump_file}" && \
        grep -q "com.edom.alarm.ACTION_ALARM_TRIGGER" "${dump_file}" && \
        grep -q "AlarmTriggerReceiver" "${dump_file}"
}

test_supported_oem() {
    local manufacturer
    manufacturer=$("${ADB}" -s "${SERIAL}" shell getprop ro.product.manufacturer | tr -d '\r' | tr '[:upper:]' '[:lower:]')
    printf '%s\n' "${manufacturer}" > "${BASELINE_DIR}/${SAFE_SERIAL}-manufacturer.txt"
    case "${manufacturer}" in
        *samsung*|*vivo*|*iqoo*) return 0 ;;
        *) return 2 ;;
    esac
}

test_shared_preferences_json_fixture() {
    [ "${BUILD_READY}" -eq 1 ] || return 2
    local fixture
    fixture=$("${ADB}" -s "${SERIAL}" shell \
        "run-as ${PACKAGE_NAME} cat shared_prefs/smart_alarm_prefs.xml" 2>/dev/null || true)
    [ -n "${fixture}" ] || return 2
    printf '%s\n' "${fixture}" > "${BASELINE_DIR}/${SAFE_SERIAL}-shared-prefs.xml"
    echo "${fixture}" | grep -q "alarms_list"
}

test_manifest_and_identity() {
    [ "${BUILD_READY}" -eq 1 ] || return 2
    local package_dump
    package_dump=$("${ADB}" -s "${SERIAL}" shell dumpsys package "${PACKAGE_NAME}" 2>/dev/null || true)
    [ -n "${package_dump}" ] || return 2
    printf '%s\n' "${package_dump}" > "${BASELINE_DIR}/${SAFE_SERIAL}-package.txt"
    echo "${package_dump}" | grep -q "AlarmTriggerReceiver" && \
        echo "${package_dump}" | grep -q "AlarmRingingService" && \
        has_real_registered_alarm && \
        grep -q "alarm://trigger/" "${BASELINE_DIR}/${SAFE_SERIAL}-dumpsys-alarm.txt"
}

test_capability_probes() {
    [ "${BUILD_READY}" -eq 1 ] || return 2
    {
        echo "sdk=$("${ADB}" -s "${SERIAL}" shell getprop ro.build.version.sdk | tr -d '\r')"
        "${ADB}" -s "${SERIAL}" shell cmd appops get "${PACKAGE_NAME}" SCHEDULE_EXACT_ALARM 2>&1 || true
        "${ADB}" -s "${SERIAL}" shell cmd appops get "${PACKAGE_NAME}" USE_FULL_SCREEN_INTENT 2>&1 || true
        "${ADB}" -s "${SERIAL}" shell dumpsys notification --noredact 2>&1 | grep "${PACKAGE_NAME}" || true
    } > "${BASELINE_DIR}/${SAFE_SERIAL}-capabilities.txt"
    [ -s "${BASELINE_DIR}/${SAFE_SERIAL}-capabilities.txt" ]
}

test_real_receiver_to_service_handoff() {
    test_supported_oem || return 2
    has_real_registered_alarm || return 2
    [ "${DELIVERY_WAIT_SECONDS}" -gt 0 ] || return 2
    "${ADB}" -s "${SERIAL}" logcat -c
    sleep "${DELIVERY_WAIT_SECONDS}"
    "${ADB}" -s "${SERIAL}" logcat -d -v threadtime > \
        "${BASELINE_DIR}/${SAFE_SERIAL}-receiver-service-logcat.txt"
    grep -q "AlarmTriggerReceiver" "${BASELINE_DIR}/${SAFE_SERIAL}-receiver-service-logcat.txt" && \
        grep -q "AlarmRingingService" "${BASELINE_DIR}/${SAFE_SERIAL}-receiver-service-logcat.txt"
}

test_real_oem_delivery_after_exit_state() {
    local state_name="$1"
    test_supported_oem || return 2
    has_real_registered_alarm || return 2
    [ "${DELIVERY_WAIT_SECONDS}" -gt 0 ] || return 2

    "${ADB}" -s "${SERIAL}" logcat -c
    case "${state_name}" in
        background) "${ADB}" -s "${SERIAL}" shell input keyevent 3 > /dev/null ;;
        process-kill) "${ADB}" -s "${SERIAL}" shell am kill "${PACKAGE_NAME}" > /dev/null ;;
        recents-removal)
            local task_id
            task_id=$("${ADB}" -s "${SERIAL}" shell dumpsys activity activities 2>/dev/null | \
                sed -nE "s/.*Task\\{#([0-9]+).*${PACKAGE_NAME}.*/\\1/p" | head -n 1)
            [ -n "${task_id}" ] || return 2
            "${ADB}" -s "${SERIAL}" shell cmd activity remove-task "${task_id}" > /dev/null
            ;;
        doze)
            [ "${ALLOW_DEVICE_IDLE_TEST:-0}" = "1" ] || return 2
            "${ADB}" -s "${SERIAL}" shell cmd deviceidle force-idle > /dev/null
            "${ADB}" -s "${SERIAL}" shell dumpsys deviceidle get deep | grep -q "IDLE" || return 1
            ;;
        *) return 1 ;;
    esac

    sleep "${DELIVERY_WAIT_SECONDS}"
    "${ADB}" -s "${SERIAL}" logcat -d -v threadtime > \
        "${BASELINE_DIR}/${SAFE_SERIAL}-${state_name}-logcat.txt"
    grep -q "AlarmTriggerReceiver" "${BASELINE_DIR}/${SAFE_SERIAL}-${state_name}-logcat.txt" && \
        grep -q "AlarmRingingService" "${BASELINE_DIR}/${SAFE_SERIAL}-${state_name}-logcat.txt"
}

test_force_stop_then_explicit_relaunch() {
    test_supported_oem || return 2
    has_real_registered_alarm || return 2
    "${ADB}" -s "${SERIAL}" shell am force-stop "${PACKAGE_NAME}"
    "${ADB}" -s "${SERIAL}" shell am start -W -n "${PACKAGE_NAME}/.ui.MainActivity" > /dev/null
    has_real_registered_alarm
}

test_capability_revoke_banner_and_restore() {
    [ "${ALLOW_CAPABILITY_REVOCATION:-0}" = "1" ] || return 2
    test_supported_oem || return 2
    has_real_registered_alarm || return 2
    "${ADB}" -s "${SERIAL}" shell cmd appops set "${PACKAGE_NAME}" SCHEDULE_EXACT_ALARM deny
    "${ADB}" -s "${SERIAL}" shell am start -W -n "${PACKAGE_NAME}/.ui.MainActivity" > /dev/null || true
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/alarm_delivery_banner.xml > /dev/null
    local dump
    dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/alarm_delivery_banner.xml 2>/dev/null || true)
    "${ADB}" -s "${SERIAL}" shell cmd appops set "${PACKAGE_NAME}" SCHEDULE_EXACT_ALARM default || true
    echo "${dump}" | grep -Eqi "limited|protection|受限|保护"
}

test_stale_duplicate_expired_and_missed_ack() {
    # Requires a controllable fixture plus an OS-triggered delivery. Direct
    # broadcasts are deliberately excluded from this acceptance result.
    has_real_registered_alarm || return 2
    return 2
}

cleanup_delivery_state() {
    "${ADB}" -s "${SERIAL}" shell cmd deviceidle unforce > /dev/null 2>&1 || true
    "${ADB}" -s "${SERIAL}" shell input keyevent 3 > /dev/null 2>&1 || true
    return 0
}

run_delivery_case "Recognizing Samsung One UI or iQOO/Vivo OriginOS hardware" test_supported_oem
run_delivery_case "Reading SharedPreferences JSON fixture" test_shared_preferences_json_fixture
run_delivery_case "Verifying manifest components and canonical PendingIntent identity" test_manifest_and_identity
run_delivery_case "Probing exact, notification, and full-screen capabilities" test_capability_probes
run_delivery_case "Real registered receiver-to-service handoff" test_real_receiver_to_service_handoff
run_delivery_case "Real registered alarm after backgrounding" test_real_oem_delivery_after_exit_state background
run_delivery_case "Real registered alarm after am kill" test_real_oem_delivery_after_exit_state process-kill
run_delivery_case "Real registered alarm after Recents removal" test_real_oem_delivery_after_exit_state recents-removal
run_delivery_case "Real registered alarm in verified Doze" test_real_oem_delivery_after_exit_state doze
run_delivery_case "Force-stop then explicit relaunch reconciliation" test_force_stop_then_explicit_relaunch
run_delivery_case "Exact-capability revocation, banner, and restoration" test_capability_revoke_banner_and_restore
run_delivery_case "Stale, duplicate, expired, and missed-outcome acknowledgement" test_stale_duplicate_expired_and_missed_ack
run_delivery_case "Cleaning up delivery test state" cleanup_delivery_state

echo "========================================================================"
echo " Physical Device E2E Verification Summary"
echo " Target Device: ${SERIAL}"
echo " Total Tests:   ${TOTAL_TESTS}"
echo " Passed Tests:  ${PASSED_TESTS}"
echo " Failed Tests:  ${FAILED_TESTS}"
echo " Unverified:   ${UNVERIFIED_TESTS}"
echo " Baseline:     ${BASELINE_FILE}"
echo "========================================================================"

if [ "${FAILED_TESTS}" -eq 0 ] && [ "${UNVERIFIED_TESTS}" -eq 0 ]; then
    echo "==> ALL PHYSICAL DEVICE E2E TESTS PASSED SUCCESSFULLY!"
    exit 0
elif [ "${FAILED_TESTS}" -eq 0 ]; then
    echo "==> PHYSICAL DEVICE E2E TESTS INCOMPLETE: UNVERIFIED CASES REMAIN."
    exit 2
else
    echo "==> SOME PHYSICAL DEVICE E2E TESTS FAILED!"
    exit 1
fi
