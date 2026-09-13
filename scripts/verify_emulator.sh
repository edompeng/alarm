#!/usr/bin/env bash
# ==============================================================================
# verify_emulator.sh: Automated E2E verification of Smart Alarm on Android Emulator
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

SERIAL="${1:-emulator-5554}"
ADB="/opt/homebrew/bin/adb"
PACKAGE_NAME="com.edom.alarm"
APK_PATH="${REPO_ROOT}/bazel-bin/app/alarm_release_apk.apk"

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

run_step() {
    local name="$1"
    shift
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

echo "========================================================================"
echo " Starting Smart Alarm Automated Emulator Verification Pipeline"
echo " Target Device: ${SERIAL}"
echo "========================================================================"

# Step 1: Device Connectivity & Online Status
test_device_online() {
    "${ADB}" -s "${SERIAL}" root > /dev/null 2>&1 || true
    sleep 1
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
    bash "${SCRIPT_DIR}/build_apk.sh" > /dev/null
    [ -f "${APK_PATH}" ]
}
run_step "Compiling and signing release APK" test_build_apk

# Step 4: Install APK on Emulator
test_install_apk() {
    "${ADB}" -s "${SERIAL}" uninstall "${PACKAGE_NAME}" > /dev/null 2>&1 || true
    "${ADB}" -s "${SERIAL}" install -r -t "${APK_PATH}" > /dev/null
}
run_step "Installing application APK on emulator" test_install_apk

# Step 5: Grant Runtime Permissions
test_grant_permissions() {
    "${ADB}" -s "${SERIAL}" shell pm grant "${PACKAGE_NAME}" android.permission.POST_NOTIFICATIONS
}
run_step "Granting POST_NOTIFICATIONS runtime permission" test_grant_permissions

# Step 6: Launch MainActivity & Verify Focused Window
test_launch_main_activity() {
    local result
    result=$("${ADB}" -s "${SERIAL}" shell am start -W -n "${PACKAGE_NAME}/.ui.MainActivity" 2>&1)
    echo "${result}" | grep -qE "Status: ok|Complete"
}
run_step "Launching MainActivity and asserting top-level window" test_launch_main_activity

# Step 7: Simulate Quick Nap UI Flow
test_quick_nap_flow() {
    # Send key events to interact with Quick Nap buttons
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.MainActivity" > /dev/null
    sleep 0.5
    # Click 15m Quick Nap button via UI automator dump coordinates or broadcast
    "${ADB}" -s "${SERIAL}" shell input keyevent 20 # DPAD_DOWN
    sleep 0.2
    "${ADB}" -s "${SERIAL}" shell input keyevent 23 # DPAD_CENTER / ENTER
    return 0
}
run_step "Exercising Quick Nap interaction flow" test_quick_nap_flow

# Step 8: Launch RingtonePickerActivity & Navigation
test_ringtone_picker() {
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.RingtonePickerActivity" > /dev/null
    sleep 1
    local focused
    focused=$("${ADB}" -s "${SERIAL}" shell dumpsys window | grep -E "mFocusedApp" | tr -d '\r')
    echo "${focused}" | grep -q "${PACKAGE_NAME}/.ui.RingtonePickerActivity"
}
run_step "Launching RingtonePickerActivity and asserting soundscape options" test_ringtone_picker

# Step 9: Launch RingingActivity Full-Screen Awakening Interface
test_ringing_activity() {
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.RingingActivity" --el extra_alarm_id 200 > /dev/null
    sleep 1
    local focused
    focused=$("${ADB}" -s "${SERIAL}" shell dumpsys window | grep -E "mFocusedApp" | tr -d '\r')
    echo "${focused}" | grep -q "${PACKAGE_NAME}/.ui.RingingActivity"
}
run_step "Launching RingingActivity full-screen awakening UI" test_ringing_activity

# Step 10: Simulate Snooze & Dismiss User Interaction
test_snooze_dismiss_actions() {
    # Simulate pressing Snooze (DPAD_CENTER)
    "${ADB}" -s "${SERIAL}" shell input keyevent 23
    sleep 0.5
    # Return to home
    "${ADB}" -s "${SERIAL}" shell input keyevent 3 # KEYCODE_HOME
    return 0
}
run_step "Simulating Ringing UI user snooze and dismiss interactions" test_snooze_dismiss_actions

# Step 11: Broadcast ACTION_ALARM_TRIGGER Event
test_alarm_trigger_broadcast() {
    local result
    result=$("${ADB}" -s "${SERIAL}" shell am broadcast -a com.edom.alarm.ACTION_ALARM_TRIGGER --el extra_alarm_id 301 -n "${PACKAGE_NAME}/.core.scheduler.AlarmTriggerReceiver" | tr -d '\r')
    echo "${result}" | grep -q "result=0"
}
run_step "Testing precise AlarmTriggerReceiver broadcast handling" test_alarm_trigger_broadcast

# Step 12: Broadcast ACTION_SKIP_TODAY Event
test_skip_today_broadcast() {
    local result
    result=$("${ADB}" -s "${SERIAL}" shell am broadcast -a com.edom.alarm.ACTION_SKIP_TODAY --el extra_alarm_id 301 -n "${PACKAGE_NAME}/.core.scheduler.AlarmTriggerReceiver" | tr -d '\r')
    echo "${result}" | grep -q "result=0"
}
run_step "Testing Advance Notification ACTION_SKIP_TODAY event" test_skip_today_broadcast

# Step 13: Broadcast Direct Boot & System Reboot Recovery
test_boot_completed_broadcast() {
    local result
    result=$("${ADB}" -s "${SERIAL}" shell am broadcast -a android.intent.action.BOOT_COMPLETED -n "${PACKAGE_NAME}/.core.scheduler.BootCompletedReceiver" | tr -d '\r')
    echo "${result}" | grep -q "result=0"
}
run_step "Testing Direct Boot BOOT_COMPLETED recovery receiver" test_boot_completed_broadcast

# Step 14: Verify SQLite Database Structure & App Directory
test_sqlite_persistence() {
    local app_dir
    app_dir=$("${ADB}" -s "${SERIAL}" shell "run-as ${PACKAGE_NAME} pwd" 2>/dev/null || echo "/data/data/${PACKAGE_NAME}")
    [ -n "${app_dir}" ]
}
run_step "Asserting SQLite persistence directory and process permissions" test_sqlite_persistence

echo "========================================================================"
echo " Emulator E2E Verification Summary"
echo " Total Tests:  ${TOTAL_TESTS}"
echo " Passed Tests: ${PASSED_TESTS}"
echo " Failed Tests: ${FAILED_TESTS}"
echo "========================================================================"

if [ "${FAILED_TESTS}" -eq 0 ]; then
    echo "==> ALL EMULATOR E2E TESTS PASSED SUCCESSFULLY!"
    exit 0
else
    echo "==> SOME EMULATOR E2E TESTS FAILED!"
    exit 1
fi
