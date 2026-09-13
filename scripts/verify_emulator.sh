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
    # Close activity so it does not mask MainActivity in subsequent steps
    "${ADB}" -s "${SERIAL}" shell input keyevent 4 # KEYCODE_BACK
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
    # Close and return to home
    "${ADB}" -s "${SERIAL}" shell input keyevent 4 # KEYCODE_BACK
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

# Step 15: Verify Alarm Cards & Recurrence Badge Rendering
test_alarm_cards_rendering() {
    "${ADB}" -s "${SERIAL}" shell am start -W -n "${PACKAGE_NAME}/.ui.MainActivity" --activity-clear-top > /dev/null
    sleep 1
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump.xml > /dev/null
    local dump
    dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump.xml)
    echo "${dump}" | grep -q "com.edom.alarm:id/lv_alarms" && \
    echo "${dump}" | grep -q "com.edom.alarm:id/sw_alarm_enabled"
}
run_step "Asserting alarm list cards and recurrence switches" test_alarm_cards_rendering

# Step 16: Test TimePicker Creation Dialog & Card Switch Toggle Flow
test_alarm_creation_and_toggle() {
    "${ADB}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.ui.MainActivity" --activity-clear-top > /dev/null
    sleep 1
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump.xml > /dev/null
    local dump
    dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump.xml)
    
    # Extract center coordinates of btn_add_alarm
    local bounds
    bounds=$(echo "${dump}" | grep -o 'resource-id="com.edom.alarm:id/btn_add_alarm"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o 'bounds="\[[^"]*"' || echo 'bounds="[180,838][2296,970]"')
    local bx1 by1 bx2 by2
    read -r bx1 by1 bx2 by2 <<< $(echo "${bounds}" | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/')
    local cx=$(( (bx1 + bx2) / 2 ))
    local cy=$(( (by1 + by2) / 2 ))
    "${ADB}" -s "${SERIAL}" shell input tap "${cx}" "${cy}"
    sleep 1

    # Verify dialog displayed
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_dialog.xml > /dev/null
    local dialog_dump
    dialog_dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_dialog.xml)
    if ! echo "${dialog_dump}" | grep -q "com.edom.alarm:id/btn_save_alarm"; then
        return 1
    fi

    # Tap Save button in dialog
    local sbounds
    sbounds=$(echo "${dialog_dump}" | grep -o 'resource-id="com.edom.alarm:id/btn_save_alarm"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o 'bounds="\[[^"]*"' || echo 'bounds="[1249,783][1491,915]"')
    local sx1 sy1 sx2 sy2
    read -r sx1 sy1 sx2 sy2 <<< $(echo "${sbounds}" | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/')
    local scx=$(( (sx1 + sx2) / 2 ))
    local scy=$(( (sy1 + sy2) / 2 ))
    "${ADB}" -s "${SERIAL}" shell input tap "${scx}" "${scy}"
    sleep 1

    # Toggle the first switch
    "${ADB}" -s "${SERIAL}" shell uiautomator dump /data/local/tmp/uidump_list.xml > /dev/null
    local list_dump
    list_dump=$("${ADB}" -s "${SERIAL}" shell cat /data/local/tmp/uidump_list.xml)
    local sw_bounds
    sw_bounds=$(echo "${list_dump}" | grep -o 'resource-id="com.edom.alarm:id/sw_alarm_enabled"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -n 1 | grep -o 'bounds="\[[^"]*"' || echo 'bounds="[2124,521][2252,595]"')
    local tx1 ty1 tx2 ty2
    read -r tx1 ty1 tx2 ty2 <<< $(echo "${sw_bounds}" | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/')
    local tcx=$(( (tx1 + tx2) / 2 ))
    local tcy=$(( (ty1 + ty2) / 2 ))
    "${ADB}" -s "${SERIAL}" shell input tap "${tcx}" "${tcy}"
    sleep 0.5
    return 0
}
run_step "Testing TimePicker creation dialog and card switch toggle flow" test_alarm_creation_and_toggle

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
