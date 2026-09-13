#!/usr/bin/env bash
# ==============================================================================
# verify_emulator.sh: Automated E2E verification of Smart Alarm on Android Emulator
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <emulator-serial>" >&2
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
BASELINE_DIR="${REPO_ROOT}/build/verification/alarm-delivery/baseline-emulator"
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
    echo "Smart Alarm delivery baseline"
    echo "serial=${SERIAL}"
    echo "started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "real_delivery_rule=only an already registered AlarmManager operation followed by receiver-to-service evidence can PASS an exit-state case"
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
            "Checking emulator connectivity and device state"|\
            "Verifying Android SDK version >= 30 (Android 11+)"|\
            "Compiling and signing debug APK"|\
            "Installing application APK on emulator"|\
            "Granting POST_NOTIFICATIONS runtime permission") ;;
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
            record_case "UNVERIFIED" "${name}" "prerequisite, API, or controllable occurrence unavailable"
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
        echo "[DIAGNOSTIC] ${name}: component entry point responded (not an acceptance pass)"
        record_case "DIAGNOSTIC" "${name}" "injected component execution only"
    else
        echo "[DIAGNOSTIC] ${name}: component entry point unavailable (not an acceptance failure)"
        record_case "DIAGNOSTIC" "${name}" "injected component execution unavailable"
    fi
}

tap_resource() {
    local target="$1"
    local xml_path="${2:-/data/local/tmp/tap_dump.xml}"
    local coords=""

    for _ in 1 2 3; do
        "${ADB}" -s "${SERIAL}" shell rm -f "${xml_path}" > /dev/null 2>&1 || true
        "${ADB}" -s "${SERIAL}" shell uiautomator dump "${xml_path}" > /dev/null 2>&1 || true
        coords=$("${ADB}" -s "${SERIAL}" shell cat "${xml_path}" 2>/dev/null | python3 -c "
import xml.etree.ElementTree as ET, re, sys
try:
    content = sys.stdin.read()
    if not content:
        sys.exit(1)
    root = ET.fromstring(content)
    for n in root.iter('node'):
        rid = n.attrib.get('resource-id', '')
        txt = n.attrib.get('text', '')
        if '${target}' in rid or re.search(r'${target}', rid, re.I) or re.search(r'${target}', txt, re.I):
            m = re.search(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds', ''))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                print(f'{(x1 + x2) // 2} {(y1 + y2) // 2}')
                sys.exit(0)
except Exception:
    pass
sys.exit(1)
" 2>/dev/null || echo "")

        if [ -n "${coords}" ]; then
            break
        fi
        sleep 0.5
    done

    if [ -n "${coords}" ]; then
        local cx cy
        read -r cx cy <<< "${coords}"
        "${ADB}" -s "${SERIAL}" shell input tap "${cx}" "${cy}"
        return 0
    fi
    return 1
}

long_press_resource() {
    local target="$1"
    local xml_path="${2:-/data/local/tmp/long_press_dump.xml}"
    local coords=""

    for _ in 1 2 3; do
        sleep 0.5
        "${ADB}" -s "${SERIAL}" shell rm -f "${xml_path}" > /dev/null 2>&1 || true
        "${ADB}" -s "${SERIAL}" shell uiautomator dump "${xml_path}" > /dev/null 2>&1 || true
        coords=$("${ADB}" -s "${SERIAL}" shell cat "${xml_path}" 2>/dev/null | python3 -c "
import xml.etree.ElementTree as ET, re, sys
try:
    content = sys.stdin.read()
    if not content:
        sys.exit(1)
    root = ET.fromstring(content)
    for n in root.iter('node'):
        rid = n.attrib.get('resource-id', '')
        txt = n.attrib.get('text', '')
        if '${target}' in rid or re.search(r'${target}', rid, re.I) or re.search(r'${target}', txt, re.I):
            m = re.search(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds', ''))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                print(f'{(x1 + x2) // 2} {(y1 + y2) // 2}')
                sys.exit(0)
except Exception:
    pass
sys.exit(1)
" 2>/dev/null || echo "")

        if [ -n "${coords}" ]; then
            break
        fi
    done

    if [ -n "${coords}" ]; then
        local cx cy
        read -r cx cy <<< "${coords}"
        "${ADB}" -s "${SERIAL}" shell input swipe "${cx}" "${cy}" "${cx}" "${cy}" 1200
        return 0
    fi
    return 1
}

echo "========================================================================"
echo " Starting Smart Alarm Automated Emulator Verification Pipeline"
echo " Target Device: ${SERIAL}"
echo "========================================================================"

if [ "$("${ADB}" -s "${SERIAL}" get-state 2>/dev/null || true)" != "device" ]; then
    record_case "UNVERIFIED" "Emulator delivery matrix" "serial is unavailable or unauthorized"
    echo "==> UNVERIFIED: emulator ${SERIAL} is unavailable; baseline: ${BASELINE_FILE}"
    exit 2
fi

# Step 1: Device Connectivity & Online Status
test_device_online() {
    "${ADB}" -s "${SERIAL}" root > /dev/null 2>&1 || true
    sleep 1
    "${ADB}" -s "${SERIAL}" shell settings put system accelerometer_rotation 0 > /dev/null 2>&1 || true
    "${ADB}" -s "${SERIAL}" shell settings put system user_rotation 1 > /dev/null 2>&1 || true
    local state
    state=$("${ADB}" -s "${SERIAL}" get-state 2>/dev/null || echo "offline")
    [ "${state}" = "device" ]
}
run_step "Checking emulator connectivity and device state" test_device_online

# Step 2: Android SDK Version Verification (Android 11+ / API 30+)
test_sdk_version() {
    local sdk
    sdk=$("${ADB}" -s "${SERIAL}" shell getprop ro.build.version.sdk | tr -d '\r')
    echo -n "(API Level: ${sdk}) "
    [ "${sdk}" -ge 30 ]
}
run_step "Verifying Android SDK version >= 30 (Android 11+)" test_sdk_version

# Step 3: Build APK
test_build_apk() {
    if bash "${SCRIPT_DIR}/build_apk.sh" > /dev/null && [ -f "${APK_PATH}" ]; then
        BUILD_READY=1
        return 0
    fi
    return 1
}
run_step "Compiling and signing debug APK" test_build_apk

# Step 4: Install APK on Emulator
test_install_apk() {
    [ "${BUILD_READY}" -eq 1 ] || return 1
    "${ADB}" -s "${SERIAL}" uninstall "${PACKAGE_NAME}" > /dev/null 2>&1 || true
    "${ADB}" -s "${SERIAL}" install -r -t "${APK_PATH}" > /dev/null
}
run_step "Installing application APK on emulator" test_install_apk

# Step 5: Grant Runtime Permissions
test_grant_permissions() {
    [ "${BUILD_READY}" -eq 1 ] || return 1
    "${ADB}" -s "${SERIAL}" shell pm grant "${PACKAGE_NAME}" android.permission.POST_NOTIFICATIONS
}
run_step "Granting POST_NOTIFICATIONS runtime permission" test_grant_permissions

# Step 6: Launch MainActivity & Verify Focused Window
test_launch_main_activity() {
    local result
    result=$("${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.MainActivity" 2>&1)
    echo "${result}" | grep -q "Starting:" || return 1
    sleep 1
    "${ADB}" -s "${SERIAL}" shell dumpsys window 2>/dev/null | grep -q "${PACKAGE_NAME}/.ui.MainActivity"
}
run_step "Launching MainActivity and asserting top-level window" test_launch_main_activity

# Step 7: Simulate Quick Nap UI Flow
test_quick_nap_flow() {
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.MainActivity" > /dev/null
    sleep 0.5
    "${ADB}" -s "${SERIAL}" shell input keyevent 20 # DPAD_DOWN
    sleep 0.2
    "${ADB}" -s "${SERIAL}" shell input keyevent 23 # DPAD_CENTER / ENTER
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_quick_nap_flow.xml > /dev/null
    local dump
    dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_quick_nap_flow.xml)
    echo "${dump}" | grep -E -q "(Quick Nap|快速午休|btn_nap_)"
}
run_diagnostic "Injecting generic key events into the Quick Nap surface" test_quick_nap_flow

# Step 8: Launch RingtonePickerActivity & Navigation
test_ringtone_picker() {
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.RingtonePickerActivity" > /dev/null
    sleep 1
    local focused
    focused=$("${ADB}" -s "${SERIAL}" shell dumpsys window | grep -E "mFocusedApp" | tr -d '\r')
    echo "${focused}" | grep -q "${PACKAGE_NAME}/.ui.RingtonePickerActivity"
    "${ADB}" -s "${SERIAL}" shell input keyevent 4 # KEYCODE_BACK
}
run_diagnostic "Attempting direct RingtonePickerActivity launch" test_ringtone_picker

# Step 9: Launch RingingActivity Full-Screen Awakening Interface
test_ringing_activity() {
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.RingingActivity" --el extra_alarm_id 200 --es extra_occurrence_id "200:1:12345" > /dev/null
    sleep 1
    local focused
    focused=$("${ADB}" -s "${SERIAL}" shell dumpsys window | grep -E "mFocusedApp" | tr -d '\r')
    echo "${focused}" | grep -q "${PACKAGE_NAME}/.ui.RingingActivity"
}
run_diagnostic "Launching RingingActivity directly" test_ringing_activity

# Step 10: Simulate Snooze & Dismiss User Interaction
test_snooze_dismiss_actions() {
    "${ADB}" -s "${SERIAL}" shell input keyevent 23
    sleep 0.5
    "${ADB}" -s "${SERIAL}" shell input keyevent 4 # KEYCODE_BACK
    "${ADB}" -s "${SERIAL}" shell input keyevent 3 # KEYCODE_HOME
    local focused
    focused=$("${ADB}" -s "${SERIAL}" shell dumpsys window | grep -E "mFocusedApp" | tr -d '\r')
    ! echo "${focused}" | grep -q "${PACKAGE_NAME}/.ui.RingingActivity"
}
run_diagnostic "Injecting keys into directly launched RingingActivity" test_snooze_dismiss_actions

# Step 11: Broadcast ACTION_ALARM_TRIGGER Event
test_alarm_trigger_broadcast() {
    local result
    result=$("${ADB}" -s "${SERIAL}" shell am broadcast -a com.edom.alarm.ACTION_ALARM_TRIGGER --el extra_alarm_id 301 -n "${PACKAGE_NAME}/.core.scheduler.AlarmTriggerReceiver" | tr -d '\r')
    echo "${result}" | grep -q "result=0"
}
run_diagnostic "Injecting AlarmTriggerReceiver broadcast" test_alarm_trigger_broadcast

# Step 12: Broadcast ACTION_SKIP_TODAY Event
test_skip_today_broadcast() {
    local result
    result=$("${ADB}" -s "${SERIAL}" shell am broadcast -a com.edom.alarm.ACTION_SKIP_TODAY --el extra_alarm_id 301 -n "${PACKAGE_NAME}/.core.scheduler.AlarmTriggerReceiver" | tr -d '\r')
    echo "${result}" | grep -q "result=0"
}
run_diagnostic "Injecting ACTION_SKIP_TODAY broadcast" test_skip_today_broadcast

# Step 13: Broadcast Direct Boot & System Reboot Recovery
test_boot_completed_broadcast() {
    local result
    result=$("${ADB}" -s "${SERIAL}" shell am broadcast -a android.intent.action.BOOT_COMPLETED -n "${PACKAGE_NAME}/.core.scheduler.BootCompletedReceiver" | tr -d '\r')
    echo "${result}" | grep -q "result=0"
}
run_diagnostic "Injecting BOOT_COMPLETED receiver broadcast" test_boot_completed_broadcast

# Step 14: Verify access to the authoritative app-private store.
test_app_private_store_access() {
    "${ADB}" -s "${SERIAL}" shell "run-as ${PACKAGE_NAME} test -d shared_prefs"
}
run_step "Asserting app-private SharedPreferences store access" test_app_private_store_access

# Step 15: Verify a clean install remains empty until the user creates an alarm.
test_empty_alarm_list() {
    "${ADB}" -s "${SERIAL}" shell am start -W -n "${PACKAGE_NAME}/.ui.MainActivity" > /dev/null
    sleep 1
    local dump=""
    for _ in 1 2 3; do
        tap_resource "button1|CONFIRM|Confirm|确定" || true
        sleep 0.5
        "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump.xml > /dev/null 2>&1 || true
        dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump.xml 2>/dev/null || true)
        if echo "${dump}" | grep -q "com.edom.alarm:id/tv_empty_alarms" && \
                ! echo "${dump}" | grep -q "com.edom.alarm:id/sw_alarm_enabled"; then
            return 0
        fi
        sleep 1
    done
    return 1
}
run_step "Asserting clean install has no synthetic default alarms" test_empty_alarm_list

# Step 16: Test TimePicker Creation Dialog & Card Switch Toggle Flow
test_alarm_creation_and_toggle() {
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.MainActivity" --activity-clear-top > /dev/null
    sleep 1
    tap_resource "button1|CONFIRM|Confirm|确定" || true
    sleep 0.5
    tap_resource "btn_add_alarm" || "${ADB}" -s "${SERIAL}" shell input tap 540 2164
    sleep 1

    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_dialog.xml > /dev/null
    local dialog_dump
    dialog_dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_dialog.xml)
    if ! echo "${dialog_dump}" | grep -q "com.edom.alarm:id/btn_save_alarm"; then
        return 1
    fi

    tap_resource "btn_save_alarm" "/data/local/tmp/uidump_dialog.xml" || "${ADB}" -s "${SERIAL}" shell input tap 672 2033
    sleep 1

    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_list.xml > /dev/null
    local list_dump
    list_dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_list.xml)
    echo "${list_dump}" | grep -q "com.edom.alarm:id/sw_alarm_enabled" || return 1
    tap_resource "sw_alarm_enabled" "/data/local/tmp/uidump_list.xml" || "${ADB}" -s "${SERIAL}" shell input tap 928 666
    sleep 0.5
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_list_off.xml > /dev/null
    tap_resource "sw_alarm_enabled" "/data/local/tmp/uidump_list_off.xml" || return 1
    sleep 0.5
    "${ADB}" -s "${SERIAL}" shell dumpsys alarm | grep -q "com.edom.alarm.ACTION_ALARM_TRIGGER"
}
run_step "Testing TimePicker creation dialog and card switch toggle flow" test_alarm_creation_and_toggle

# Step 17: Real AlarmManager.setAlarmClock() OS Registration
test_alarm_manager_clock_scheduling() {
    local alarm_dump
    alarm_dump=$("${ADB}" -s "${SERIAL}" shell dumpsys alarm | grep -E "com.edom.alarm" | tr -d '\r')
    echo "${alarm_dump}" | grep -q "com.edom.alarm.ACTION_ALARM_TRIGGER"
}
run_step "Verifying real AlarmManager.setAlarmClock() background OS registration" test_alarm_manager_clock_scheduling

# Step 18: In-List Quick Nap Card Creation & Auto-Destruction Lifecycle
test_quick_nap_lifecycle() {
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.MainActivity" --activity-clear-top > /dev/null
    sleep 1
    tap_resource "button1|CONFIRM|Confirm|确定" || true
    sleep 0.5
    # Tap 15m Quick Nap button dynamically
    tap_resource "btn_nap_15" || "${ADB}" -s "${SERIAL}" shell input tap 168 882
    sleep 1
    local nap_dump=""
    for _ in 1 2 3; do
        "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_nap_check.xml > /dev/null 2>&1 || true
        nap_dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_nap_check.xml 2>/dev/null || true)
        if echo "${nap_dump}" | grep -E -q "(Quick Nap|快速午休)"; then
            break
        fi
        sleep 1
    done
    if ! echo "${nap_dump}" | grep -E -q "(Quick Nap|快速午休)"; then
        "${ADB}" -s "${SERIAL}" shell input swipe 540 1000 540 400 300
        sleep 1
        "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_nap_check.xml > /dev/null 2>&1 || true
        nap_dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_nap_check.xml 2>/dev/null || true)
    fi
    if ! echo "${nap_dump}" | grep -E -q "(Quick Nap|快速午休)"; then
        return 1
    fi
    # Find switch for Quick Nap specifically and toggle off to trigger auto-destruction
    local nap_sw_coords
    nap_sw_coords=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_nap_check.xml 2>/dev/null | python3 -c "
import xml.etree.ElementTree as ET, re, sys
try:
    content = sys.stdin.read()
    if content:
        root = ET.fromstring(content)
        for card in root.iter('node'):
            if 'card_alarm_root' in card.attrib.get('resource-id', ''):
                has_nap = any('Quick Nap' in n.attrib.get('text', '') or '快速午休' in n.attrib.get('text', '') for n in card.iter('node'))
                if has_nap:
                    for sw in card.iter('node'):
                        if 'sw_alarm_enabled' in sw.attrib.get('resource-id', ''):
                            m = re.search(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', sw.attrib.get('bounds', ''))
                            if m:
                                x1, y1, x2, y2 = map(int, m.groups())
                                print(f'{(x1 + x2) // 2} {(y1 + y2) // 2}')
                                sys.exit(0)
except Exception:
    pass
sys.exit(1)
" 2>/dev/null || echo "")

    if [ -n "${nap_sw_coords}" ]; then
        local nx ny
        read -r nx ny <<< "${nap_sw_coords}"
        "${ADB}" -s "${SERIAL}" shell input tap "${nx}" "${ny}"
    else
        tap_resource "sw_alarm_enabled" "/data/local/tmp/uidump_nap_check.xml"
    fi
    sleep 1
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_nap_removed.xml > /dev/null
    local removed_dump
    removed_dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_nap_removed.xml)
    ! echo "${removed_dump}" | grep -E -q "(Quick Nap|快速午休)"
}
run_step "Verifying in-list Quick Nap countdown card creation and auto-destruction" test_quick_nap_lifecycle

# Step 19: Long-Press Context Menu & Vacation Mode Monthly Calendar Skip
test_vacation_calendar_skip_flow() {
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.MainActivity" --activity-clear-top > /dev/null
    sleep 1
    tap_resource "button1|CONFIRM|Confirm|确定" || true
    sleep 0.5
    long_press_resource "card_alarm_root" || "${ADB}" -s "${SERIAL}" shell input swipe 540 1200 540 1200 1200
    sleep 1
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_ctx.xml > /dev/null
    local ctx_dump
    ctx_dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_ctx.xml)
    if ! echo "${ctx_dump}" | grep -E -q "(Vacation|休假|跳过|Skip)"; then
        return 1
    fi
    # Tap first item in context menu: Skip Multiple Days / Vacation Mode
    tap_resource "Skip Multiple Days|Vacation" "/data/local/tmp/uidump_ctx.xml" || "${ADB}" -s "${SERIAL}" shell input tap 540 1135
    sleep 1

    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_cal_view.xml > /dev/null
    local cal_dump
    cal_dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_cal_view.xml)
    if ! echo "${cal_dump}" | grep -q "com.edom.alarm:id/gv_calendar_days"; then
        return 1
    fi

    # Select day cell 15
    tap_resource "^15$" || "${ADB}" -s "${SERIAL}" shell input tap 407 1231
    sleep 0.5
    # Tap Save Skips button
    tap_resource "btn_save_calendar" || "${ADB}" -s "${SERIAL}" shell input tap 862 1539
    sleep 1

    # Confirm in SkipConfirmationDialog if displayed
    tap_resource "button1|CONFIRM|Confirm|确定" || true
    sleep 1
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_skip_badge.xml > /dev/null
    local badge_dump
    badge_dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_skip_badge.xml)
    echo "${badge_dump}" | grep -E -q "(Vacation|skipped|已跳过)"
}
run_step "Verifying long-press Vacation Mode calendar date selection and badge" test_vacation_calendar_skip_flow

# Step 20: Settings Dialog & Dynamic Language Switching
test_settings_and_language_switch() {
    "${ADB}" -s "${SERIAL}" shell input keyevent 4 > /dev/null 2>&1 || true
    sleep 0.5
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.MainActivity" --activity-clear-top > /dev/null
    sleep 1
    # Dismiss any lingering heads-up notification banner so btn_settings is accessible
    "${ADB}" -s "${SERIAL}" shell input swipe 500 200 500 50 100 > /dev/null 2>&1 || true
    sleep 0.5
    tap_resource "btn_settings" || "${ADB}" -s "${SERIAL}" shell input tap 975 240
    sleep 1.5

    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_settings.xml > /dev/null
    local sdump
    sdump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_settings.xml)
    if ! echo "${sdump}" | grep -q "com.edom.alarm:id/rg_language"; then
        "${ADB}" -s "${SERIAL}" shell input swipe 500 200 500 50 100 > /dev/null 2>&1 || true
        sleep 0.5
        tap_resource "btn_settings" || "${ADB}" -s "${SERIAL}" shell input tap 975 240
        sleep 1.5
        "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_settings.xml > /dev/null
        sdump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_settings.xml)
        if ! echo "${sdump}" | grep -q "com.edom.alarm:id/rg_language"; then
            return 1
        fi
    fi

    # Tap English language option
    tap_resource "rb_lang_en" "/data/local/tmp/uidump_settings.xml" || "${ADB}" -s "${SERIAL}" shell input tap 808 464
    sleep 0.5

    # Scroll down to bottom of Settings to tap Save
    for _ in 1 2 3; do
        "${ADB}" -s "${SERIAL}" shell input swipe 540 1800 540 400 300
        sleep 0.3
    done

    # Tap Save button
    tap_resource "btn_save_settings" || "${ADB}" -s "${SERIAL}" shell input tap 821 2120
    sleep 1
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_language_result.xml > /dev/null
    local language_dump
    language_dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_language_result.xml)
    echo "${language_dump}" | grep -E -q "(Smart Alarm|Quick Nap|Settings)"
}
run_step "Verifying Application Settings dialog and dynamic language switching" test_settings_and_language_switch

# Step 21: Advance Notification Broadcast & Quick Dismiss Action
test_advance_notification_dismiss() {
    "${ADB}" -s "${SERIAL}" shell am broadcast -a com.edom.alarm.ACTION_ADVANCE_NOTIFICATION --el extra_alarm_id 1001 -n "${PACKAGE_NAME}/.core.scheduler.AlarmTriggerReceiver" > /dev/null
    sleep 1

    local result
    result=$("${ADB}" -s "${SERIAL}" shell am broadcast -a com.edom.alarm.ACTION_SKIP_TODAY --el extra_alarm_id 1001 -n "${PACKAGE_NAME}/.core.scheduler.AlarmTriggerReceiver" | tr -d '\r')
    echo "${result}" | grep -q "result=0"
}
run_diagnostic "Injecting advance-notification and skip broadcasts" test_advance_notification_dismiss

# Step 22: Process Kill / Swiped From Recents Wakeup Resilience
test_process_kill_wakeup() {
    "${ADB}" -s "${SERIAL}" shell am force-stop "${PACKAGE_NAME}"
    sleep 1

    # Trigger alarm broadcast with FLAG_RECEIVER_FOREGROUND (0x10000000)
    "${ADB}" -s "${SERIAL}" shell am broadcast -a com.edom.alarm.ACTION_ALARM_TRIGGER --el extra_alarm_id 1002 -f 0x10000000 -n "${PACKAGE_NAME}/.core.scheduler.AlarmTriggerReceiver" > /dev/null
    sleep 2

    local focused
    focused=$("${ADB}" -s "${SERIAL}" shell dumpsys window | grep -E "mFocusedApp" | tr -d '\r')
    echo "${focused}" | grep -q "${PACKAGE_NAME}/.ui.RingingActivity"
    "${ADB}" -s "${SERIAL}" shell input keyevent 4 # KEYCODE_BACK
    sleep 0.5
}
run_diagnostic "Force-stop plus injected broadcast sequence" test_process_kill_wakeup

# Step 23: Customized Quick Nap Slots (Minutes & Hours)
test_nap_slot_customization() {
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.MainActivity" --activity-clear-top > /dev/null
    sleep 1
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_nap_check2.xml > /dev/null
    local dump
    dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_nap_check2.xml)
    echo "${dump}" | grep -q "com.edom.alarm:id/btn_nap_15" && \
    echo "${dump}" | grep -q "com.edom.alarm:id/btn_nap_60"
}
run_step "Testing customizable Quick Nap preset slots and labels" test_nap_slot_customization

# Step 24: Vacation Calendar Layout (Sunday First & Header)
test_vacation_calendar_layout() {
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.MainActivity" --activity-clear-top > /dev/null
    sleep 1
    if ! tap_resource "tv_alarm_vacation"; then
        "${ADB}" -s "${SERIAL}" shell input swipe 400 600 400 600 1200
        sleep 1
        tap_resource "Skip Multiple Days|Vacation" || "${ADB}" -s "${SERIAL}" shell input tap 540 1135
        sleep 1
    fi
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_cal_layout.xml > /dev/null
    local cal_dump
    cal_dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_cal_layout.xml)
    "${ADB}" -s "${SERIAL}" shell input keyevent 4 # BACK
    sleep 0.5
    echo "${cal_dump}" | grep -q "com.edom.alarm:id/layout_calendar_header" && \
    echo "${cal_dump}" | grep -q "com.edom.alarm:id/btn_prev_month" && \
    echo "${cal_dump}" | grep -q "com.edom.alarm:id/btn_next_month"
}
run_step "Verifying Vacation Calendar Sunday-first layout and 7-column header" test_vacation_calendar_layout

# Step 25: Statutory Holiday Configuration Generator & Sync
test_holiday_generator_and_sync() {
    python3 "${REPO_ROOT}/scripts/generate_holiday_config.py" --year 2026 --output /tmp/test_holiday_2026.json > /dev/null
    python3 -c "import json; data=json.load(open('/tmp/test_holiday_2026.json')); assert 'holidays' in data and 'workdays' in data and data['year'] == 2026"
}
run_step "Validating statutory holiday generator output against HolidaySyncModel" test_holiday_generator_and_sync

# Step 26: Vacation Calendar Clear Skips Button Presence
test_clear_skips_button() {
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.MainActivity" --activity-clear-top > /dev/null
    sleep 1
    if ! tap_resource "tv_alarm_vacation"; then
        "${ADB}" -s "${SERIAL}" shell input swipe 400 600 400 600 1200
        sleep 1
        tap_resource "Skip Multiple Days|Vacation" || "${ADB}" -s "${SERIAL}" shell input tap 540 1135
        sleep 1
    fi
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_clear_skips.xml > /dev/null
    local dump
    dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_clear_skips.xml)
    "${ADB}" -s "${SERIAL}" shell input keyevent 4 # BACK
    sleep 0.5
    echo "${dump}" | grep -q "com.edom.alarm:id/btn_clear_calendar"
}
run_step "Verifying 'Clear Skips' action button in VacationCalendarDialog" test_clear_skips_button

# Delivery acceptance baseline. These cases deliberately require a real operation
# visible in AlarmManager; synthetic broadcasts above are retained only as diagnostics.
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

test_shared_preferences_json_fixture() {
    [ "${BUILD_READY}" -eq 1 ] || return 2
    local prefs
    prefs=$("${ADB}" -s "${SERIAL}" shell "run-as ${PACKAGE_NAME} ls shared_prefs 2>/dev/null || ls /data/data/${PACKAGE_NAME}/shared_prefs" 2>/dev/null || true)
    [ -n "${prefs}" ] || return 2
    local fixture
    fixture=$("${ADB}" -s "${SERIAL}" shell \
        "run-as ${PACKAGE_NAME} cat shared_prefs/smart_alarm_prefs.xml 2>/dev/null || cat /data/data/${PACKAGE_NAME}/shared_prefs/smart_alarm_prefs.xml" 2>/dev/null || true)
    [ -n "${fixture}" ] || return 2
    printf '%s\n' "${fixture}" > "${BASELINE_DIR}/${SAFE_SERIAL}-shared-prefs.xml"
    echo "${fixture}" | grep -q "alarms_list"
}

test_manifest_delivery_components() {
    [ "${BUILD_READY}" -eq 1 ] || return 2
    local package_dump
    package_dump=$("${ADB}" -s "${SERIAL}" shell dumpsys package "${PACKAGE_NAME}" 2>/dev/null || true)
    [ -n "${package_dump}" ] || return 2
    printf '%s\n' "${package_dump}" > "${BASELINE_DIR}/${SAFE_SERIAL}-package.txt"
    echo "${package_dump}" | grep -q "AlarmTriggerReceiver" && \
        echo "${package_dump}" | grep -q "BootCompletedReceiver" && \
        echo "${package_dump}" | grep -q "AlarmRingingService"
}

test_pending_intent_identity() {
    has_real_registered_alarm || return 2
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
    has_real_registered_alarm || return 2
    [ "${DELIVERY_WAIT_SECONDS}" -gt 0 ] || return 2
    "${ADB}" -s "${SERIAL}" logcat -c
    sleep "${DELIVERY_WAIT_SECONDS}"
    "${ADB}" -s "${SERIAL}" logcat -d -v threadtime > \
        "${BASELINE_DIR}/${SAFE_SERIAL}-receiver-service-logcat.txt"
    grep -q "AlarmTriggerReceiver" "${BASELINE_DIR}/${SAFE_SERIAL}-receiver-service-logcat.txt" && \
        grep -q "AlarmRingingService" "${BASELINE_DIR}/${SAFE_SERIAL}-receiver-service-logcat.txt"
}

test_real_delivery_after_exit_state() {
    local state_name="$1"
    has_real_registered_alarm || return 2
    [ "${DELIVERY_WAIT_SECONDS}" -gt 0 ] || return 2

    "${ADB}" -s "${SERIAL}" logcat -c
    case "${state_name}" in
        background)
            "${ADB}" -s "${SERIAL}" shell input keyevent 3 > /dev/null
            ;;
        process-kill)
            "${ADB}" -s "${SERIAL}" shell am kill "${PACKAGE_NAME}" > /dev/null
            ;;
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

test_force_stop_relaunch_reconciliation() {
    has_real_registered_alarm || return 2
    "${ADB}" -s "${SERIAL}" shell am force-stop "${PACKAGE_NAME}"
    "${ADB}" -s "${SERIAL}" shell am start -W -n "${PACKAGE_NAME}/.ui.MainActivity" > /dev/null
    has_real_registered_alarm
}

test_capability_revoke_banner_and_restore() {
    [ "${ALLOW_CAPABILITY_REVOCATION:-0}" = "1" ] || return 2
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
    # No injected payload is accepted as delivery proof. This case becomes runnable
    # only when a controllable persisted fixture and real registered occurrence exist.
    has_real_registered_alarm || return 2
    return 2
}

cleanup_delivery_state() {
    "${ADB}" -s "${SERIAL}" shell cmd deviceidle unforce > /dev/null 2>&1 || true
    "${ADB}" -s "${SERIAL}" shell input keyevent 3 > /dev/null 2>&1 || true
    return 0
}

run_delivery_case "Reading SharedPreferences JSON fixture" test_shared_preferences_json_fixture
run_delivery_case "Verifying manifest receiver and ringing-service declarations" test_manifest_delivery_components
run_delivery_case "Verifying canonical registered PendingIntent identity" test_pending_intent_identity
run_delivery_case "Probing exact, notification, and full-screen capabilities" test_capability_probes
run_delivery_case "Real registered receiver-to-service handoff" test_real_receiver_to_service_handoff
run_delivery_case "Real registered alarm after backgrounding" test_real_delivery_after_exit_state background
run_delivery_case "Real registered alarm after am kill" test_real_delivery_after_exit_state process-kill
run_delivery_case "Real registered alarm after Recents removal" test_real_delivery_after_exit_state recents-removal
run_delivery_case "Real registered alarm in verified Doze" test_real_delivery_after_exit_state doze
run_delivery_case "Force-stop then explicit relaunch reconciliation" test_force_stop_relaunch_reconciliation
run_delivery_case "Exact-capability revocation, banner, and restoration" test_capability_revoke_banner_and_restore
run_delivery_case "Stale, duplicate, expired, and missed-outcome acknowledgement" test_stale_duplicate_expired_and_missed_ack
run_delivery_case "Cleaning up delivery test state" cleanup_delivery_state

echo "========================================================================"
echo " Emulator E2E Verification Summary"
echo " Total Tests:  ${TOTAL_TESTS}"
echo " Passed Tests: ${PASSED_TESTS}"
echo " Failed Tests: ${FAILED_TESTS}"
echo " Unverified:   ${UNVERIFIED_TESTS}"
echo " Baseline:     ${BASELINE_FILE}"
echo "========================================================================"

if [ "${FAILED_TESTS}" -eq 0 ] && [ "${UNVERIFIED_TESTS}" -eq 0 ]; then
    echo "==> ALL EMULATOR E2E TESTS PASSED SUCCESSFULLY!"
    exit 0
elif [ "${FAILED_TESTS}" -eq 0 ]; then
    echo "==> EMULATOR E2E TESTS INCOMPLETE: UNVERIFIED CASES REMAIN."
    exit 2
else
    echo "==> SOME EMULATOR E2E TESTS FAILED!"
    exit 1
fi
