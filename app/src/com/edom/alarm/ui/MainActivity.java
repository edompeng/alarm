package com.edom.alarm.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.edom.alarm.R;
import com.edom.alarm.AlarmApplication;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedState;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ReconcileReason;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ReconcileReport;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import com.edom.alarm.core.scheduler.AlarmScheduleStore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Main dashboard for Android Smart Alarm.
 * Displays configured alarms with statutory workday recognition,
 * TimePicker creation/edit dialog, card list with On/Off toggles,
 * customizable quick nap triggers, settings navigation, and vacation mode.
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = "smart_alarm_prefs";
    private ListView mAlarmListView;
    private TextView mTvEmpty;
    private AlarmListAdapter mAdapter;
    private final List<AlarmListAdapter.AlarmItemModel> mAlarms = new ArrayList<>();
    private AlarmApplication mApplication;
    private AlarmScheduleStore mAlarmStore;
    private AlarmProtectionPresenter mProtectionPresenter;

    private static final long COUNTDOWN_REFRESH_INTERVAL_MS = 30_000L;
    private final Handler mCountdownHandler = new Handler(Looper.getMainLooper());
    private final Runnable mCountdownRefresh = new Runnable() {
        @Override
        public void run() {
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
            mCountdownHandler.postDelayed(this, COUNTDOWN_REFRESH_INTERVAL_MS);
        }
    };

    private Button mBtnNap1;
    private Button mBtnNap2;
    private Button mBtnNap3;
    private Button mBtnNap4;

    private int mNapSlot1Val = 15;
    private String mNapSlot1Unit = SettingsDialog.UNIT_MINUTES;
    private int mNapSlot2Val = 30;
    private String mNapSlot2Unit = SettingsDialog.UNIT_MINUTES;
    private int mNapSlot3Val = 45;
    private String mNapSlot3Unit = SettingsDialog.UNIT_MINUTES;
    private int mNapSlot4Val = 60;
    private String mNapSlot4Unit = SettingsDialog.UNIT_MINUTES;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocalizationManager.wrapContext(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocalizationManager.updateActivityLocale(this);
        setContentView(R.layout.activity_main);

        mApplication = AlarmApplication.from(this);
        mAlarmStore = mApplication.alarmStore();

        mAlarmListView = findViewById(R.id.lv_alarms);
        mTvEmpty = findViewById(R.id.tv_empty_alarms);
        mProtectionPresenter = new AlarmProtectionPresenter(
                this,
                findViewById(R.id.alarm_protection_warning),
                findViewById(R.id.tv_alarm_protection_status),
                findViewById(R.id.btn_alarm_protection_fix),
                findViewById(R.id.btn_alarm_protection_help),
                mAlarmStore,
                mApplication.capabilityEvaluator(),
                this::reconcileAndPresent);

        // Header Settings Button and Version
        TextView tvAppVersion = findViewById(R.id.tv_app_version);
        if (tvAppVersion != null) {
            tvAppVersion.setText(AppVersionUtils.getFormattedVersion(this));
        }

        findViewById(R.id.btn_settings).setOnClickListener(v -> {
            new SettingsDialog(MainActivity.this, languageChanged -> {
                if (languageChanged) {
                    recreate();
                } else {
                    loadNapPresets();
                    updateNapButtons();
                    rescheduleAllAdvanceNotifications();
                }
            }, this::reconcileAndPresent).show();
        });

        // Quick Nap Buttons
        mBtnNap1 = findViewById(R.id.btn_nap_15);
        mBtnNap2 = findViewById(R.id.btn_nap_30);
        mBtnNap3 = findViewById(R.id.btn_nap_45);
        mBtnNap4 = findViewById(R.id.btn_nap_60);

        setupQuickNapButtons();

        // Add Alarm Button
        findViewById(R.id.btn_add_alarm).setOnClickListener(v -> {
            new AlarmEditDialog(MainActivity.this, null, mSavedListener).show();
        });

        // Initialize Adapter & Listeners
        mAdapter = new AlarmListAdapter(this);
        mAdapter.setOnAlarmToggleListener((item, isChecked) -> {
            if (!isChecked && item.isQuickNap) {
                // Quick Nap toggled off: auto-destruct and remove from list and storage
                cancelAlarmInSystem(item);
                mAlarms.remove(item);
                saveAlarms();
                updateView();
                Toast.makeText(this, R.string.toast_nap_cancelled, Toast.LENGTH_SHORT).show();
                return;
            }

            if (isChecked) {
                scheduleAlarmInSystem(item);
            } else {
                cancelAlarmInSystem(item);
            }
            saveAlarms();
            String formattedTime = String.format("%02d:%02d", item.hour, item.minute);
            int toastRes = isChecked ? R.string.toast_alarm_enabled : R.string.toast_alarm_disabled;
            Toast.makeText(this, getString(toastRes, formattedTime), Toast.LENGTH_SHORT).show();
        });

        mAdapter.setOnAlarmClickListener(new AlarmListAdapter.OnAlarmClickListener() {
            @Override
            public void onClick(AlarmListAdapter.AlarmItemModel item) {
                new AlarmEditDialog(MainActivity.this, item, mSavedListener).show();
            }

            @Override
            public void onLongClick(AlarmListAdapter.AlarmItemModel item) {
                CharSequence[] options = new CharSequence[] {
                    getString(R.string.vacation_mode),
                    getString(R.string.edit_alarm),
                    getString(R.string.delete_alarm)
                };

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(item.label != null && !item.label.isEmpty() ? item.label : getString(R.string.alarm_options))
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) {
                                // Vacation Mode
                                new VacationCalendarDialog(
                                        MainActivity.this, item, MainActivity.this::applyVacationResult)
                                        .show();
                            } else if (which == 1) {
                                // Edit Alarm
                                new AlarmEditDialog(MainActivity.this, item, mSavedListener).show();
                            } else if (which == 2) {
                                // Delete Alarm
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle(getString(R.string.delete_alarm))
                                        .setMessage(R.string.delete_alarm_confirm)
                                        .setPositiveButton(getString(R.string.confirm), (d, w) -> deleteAlarmById(item.id))
                                        .setNegativeButton(getString(R.string.cancel), null)
                                        .show();
                            }
                        })
                        .show();
            }
        });

        mAdapter.setOnVacationBadgeClickListener(item -> {
            new VacationCalendarDialog(
                    MainActivity.this, item, MainActivity.this::applyVacationResult).show();
        });

        mAlarmListView.setAdapter(mAdapter);

        loadNapPresets();
        updateNapButtons();
        loadAlarms();
        ReconcileReport report = mApplication.reconciler().reconcile(ReconcileReason.APP_LAUNCH);
        loadAlarms();
        mProtectionPresenter.present(report);
        mProtectionPresenter.presentPendingMissedOutcomes();
        HolidaySyncManager.checkAndSyncAuto(this, this::reconcileAndPresent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mApplication != null && mProtectionPresenter != null) {
            reconcileAndPresent();
        }
        mCountdownHandler.removeCallbacks(mCountdownRefresh);
        mCountdownHandler.post(mCountdownRefresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCountdownHandler.removeCallbacks(mCountdownRefresh);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AlarmProtectionPresenter.REQUEST_CODE_POST_NOTIFICATIONS) {
            reconcileAndPresent();
        }
    }

    private void setupQuickNapButtons() {
        mBtnNap1.setOnClickListener(v -> triggerQuickNapBySlot(1, mNapSlot1Val, mNapSlot1Unit));
        mBtnNap2.setOnClickListener(v -> triggerQuickNapBySlot(2, mNapSlot2Val, mNapSlot2Unit));
        mBtnNap3.setOnClickListener(v -> triggerQuickNapBySlot(3, mNapSlot3Val, mNapSlot3Unit));
        mBtnNap4.setOnClickListener(v -> triggerQuickNapBySlot(4, mNapSlot4Val, mNapSlot4Unit));

        mBtnNap1.setOnLongClickListener(v -> { openNapEdit(1, mNapSlot1Val, mNapSlot1Unit); return true; });
        mBtnNap2.setOnLongClickListener(v -> { openNapEdit(2, mNapSlot2Val, mNapSlot2Unit); return true; });
        mBtnNap3.setOnLongClickListener(v -> { openNapEdit(3, mNapSlot3Val, mNapSlot3Unit); return true; });
        mBtnNap4.setOnLongClickListener(v -> { openNapEdit(4, mNapSlot4Val, mNapSlot4Unit); return true; });
    }

    private void openNapEdit(int slotIndex, int currentVal, String currentUnit) {
        new NapEditDialog(this, slotIndex, currentVal, currentUnit, (idx, newVal, newUnit) -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            if (idx == 1) {
                edit.putInt(SettingsDialog.KEY_NAP_SLOT_1_VAL, newVal);
                edit.putString(SettingsDialog.KEY_NAP_SLOT_1_UNIT, newUnit);
            } else if (idx == 2) {
                edit.putInt(SettingsDialog.KEY_NAP_SLOT_2_VAL, newVal);
                edit.putString(SettingsDialog.KEY_NAP_SLOT_2_UNIT, newUnit);
            } else if (idx == 3) {
                edit.putInt(SettingsDialog.KEY_NAP_SLOT_3_VAL, newVal);
                edit.putString(SettingsDialog.KEY_NAP_SLOT_3_UNIT, newUnit);
            } else if (idx == 4) {
                edit.putInt(SettingsDialog.KEY_NAP_SLOT_4_VAL, newVal);
                edit.putString(SettingsDialog.KEY_NAP_SLOT_4_UNIT, newUnit);
            }
            edit.apply();
            loadNapPresets();
            updateNapButtons();
            Toast.makeText(this, R.string.toast_save_success, Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void loadNapPresets() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mNapSlot1Val = prefs.getInt(SettingsDialog.KEY_NAP_SLOT_1_VAL, 15);
        mNapSlot1Unit = prefs.getString(SettingsDialog.KEY_NAP_SLOT_1_UNIT, SettingsDialog.UNIT_MINUTES);
        mNapSlot2Val = prefs.getInt(SettingsDialog.KEY_NAP_SLOT_2_VAL, 30);
        mNapSlot2Unit = prefs.getString(SettingsDialog.KEY_NAP_SLOT_2_UNIT, SettingsDialog.UNIT_MINUTES);
        mNapSlot3Val = prefs.getInt(SettingsDialog.KEY_NAP_SLOT_3_VAL, 45);
        mNapSlot3Unit = prefs.getString(SettingsDialog.KEY_NAP_SLOT_3_UNIT, SettingsDialog.UNIT_MINUTES);
        mNapSlot4Val = prefs.getInt(SettingsDialog.KEY_NAP_SLOT_4_VAL, 60);
        mNapSlot4Unit = prefs.getString(SettingsDialog.KEY_NAP_SLOT_4_UNIT, SettingsDialog.UNIT_MINUTES);
    }

    private void updateNapButtons() {
        mBtnNap1.setText(formatNapButtonText(mNapSlot1Val, mNapSlot1Unit));
        mBtnNap2.setText(formatNapButtonText(mNapSlot2Val, mNapSlot2Unit));
        mBtnNap3.setText(formatNapButtonText(mNapSlot3Val, mNapSlot3Unit));
        mBtnNap4.setText(formatNapButtonText(mNapSlot4Val, mNapSlot4Unit));
    }

    private String formatNapButtonText(int val, String unit) {
        String unitSuffix = SettingsDialog.UNIT_HOURS.equals(unit) ? getString(R.string.unit_hr_short) : getString(R.string.unit_min_short);
        return val + unitSuffix;
    }

    private void triggerQuickNapBySlot(int slotIndex, int val, String unit) {
        int minutes = SettingsDialog.UNIT_HOURS.equals(unit) ? val * 60 : val;
        triggerQuickNap(minutes, val, unit);
    }

    private void triggerQuickNap(int minutes, int originalVal, String unit) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, minutes);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        long id = System.currentTimeMillis();
        String unitLabel = SettingsDialog.UNIT_HOURS.equals(unit) ? (originalVal + "h") : (minutes + "m");
        AlarmListAdapter.AlarmItemModel napItem = new AlarmListAdapter.AlarmItemModel(
                id,
                hour,
                minute,
                true,
                0, // Ring once
                0,
                getString(R.string.quick_nap) + " (" + unitLabel + ")",
                true, // isQuickNap
                null,
                "Default Alarm Sound",
                true,
                new ArrayList<>()
        );

        mAlarms.add(napItem);
        sortAlarms();
        saveAlarms();
        StoredAlarm persistedNap = mAlarmStore.findById(napItem.id);
        if (persistedNap != null) {
            mAlarmStore.save(persistedNap.withOccurrence(cal.getTimeInMillis(), 1L));
        }
        scheduleAlarmInSystem(napItem);
        updateView();

        int napIndex = mAlarms.indexOf(napItem);
        if (napIndex >= 0) {
            mAlarmListView.setSelection(napIndex);
        }

        Toast.makeText(this, getString(R.string.toast_nap_scheduled, String.format("%02d:%02d", hour, minute)), Toast.LENGTH_SHORT).show();
    }

    private final AlarmEditDialog.OnAlarmSavedListener mSavedListener = new AlarmEditDialog.OnAlarmSavedListener() {
        @Override
        public void onAlarmSaved(AlarmListAdapter.AlarmItemModel item) {
            boolean isNew = true;
            for (int i = 0; i < mAlarms.size(); i++) {
                if (mAlarms.get(i).id == item.id) {
                    mAlarms.set(i, item);
                    isNew = false;
                    break;
                }
            }
            if (isNew) {
                mAlarms.add(item);
            }
            sortAlarms();
            saveAlarms();
            scheduleAlarmInSystem(item);
            updateView();

            // Report the schedule that was actually persisted (holidays, repeat mode,
            // and skip dates included) instead of a hand-rolled next-day estimate.
            StoredAlarm persisted = mAlarmStore.findById(item.id);
            long triggerAtMs = persisted != null ? persisted.nextTriggerAtMs : 0L;
            long nowMs = System.currentTimeMillis();
            if (persisted != null && persisted.enabled && triggerAtMs > nowMs) {
                Toast.makeText(MainActivity.this,
                        getString(R.string.toast_alarm_ring_in,
                                RingCountdownFormatter.format(MainActivity.this, triggerAtMs - nowMs)),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, R.string.toast_save_success, Toast.LENGTH_SHORT)
                        .show();
            }
        }

        @Override
        public void onAlarmDeleted(long alarmId) {
            deleteAlarmById(alarmId);
        }
    };

    private void deleteAlarmById(long alarmId) {
        for (int i = 0; i < mAlarms.size(); i++) {
            AlarmListAdapter.AlarmItemModel item = mAlarms.get(i);
            if (item.id == alarmId) {
                cancelAlarmInSystem(item);
                mAlarms.remove(i);
                break;
            }
        }
        saveAlarms();
        updateView();
        Toast.makeText(this, R.string.toast_delete_success, Toast.LENGTH_SHORT).show();
    }

    /**
     * Applies the vacation calendar result. When every remaining occurrence is
     * skipped the alarm can never ring again, so its switch is turned off.
     */
    private void applyVacationResult(
            long alarmId, List<String> skippedDates, boolean hasFutureRing) {
        AlarmListAdapter.AlarmItemModel target = null;
        for (AlarmListAdapter.AlarmItemModel candidate : mAlarms) {
            if (candidate.id == alarmId) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            return;
        }
        target.skippedDates = new ArrayList<>(skippedDates);
        if (!hasFutureRing) {
            target.isEnabled = false;
            if (target.isQuickNap) {
                // A Quick Nap with its only occurrence skipped is gone entirely.
                cancelAlarmInSystem(target);
                mAlarms.remove(target);
                saveAlarms();
                updateView();
                Toast.makeText(this, R.string.toast_alarm_disabled_no_dates,
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        saveAlarms();
        scheduleAlarmInSystem(target);
        updateView();
        Toast.makeText(this,
                hasFutureRing ? R.string.toast_skips_saved : R.string.toast_alarm_disabled_no_dates,
                Toast.LENGTH_SHORT).show();
    }

    private void loadAlarms() {
        mAlarms.clear();
        for (StoredAlarm alarm : mAlarmStore.loadAll()) {
            mAlarms.add(toViewModel(alarm));
        }
        sortAlarms();
        updateView();
    }

    private void saveAlarms() {
        Set<Long> retainedIds = new HashSet<>();
        for (AlarmListAdapter.AlarmItemModel item : mAlarms) {
            retainedIds.add(item.id);
            mAlarmStore.save(toStoredAlarm(item, mAlarmStore.findById(item.id)));
        }
        for (StoredAlarm existing : mAlarmStore.loadAll()) {
            if (!retainedIds.contains(existing.id)) {
                mAlarmStore.delete(existing.id);
            }
        }
    }

    private void sortAlarms() {
        Collections.sort(mAlarms, (a, b) -> Integer.compare(a.hour * 60 + a.minute, b.hour * 60 + b.minute));
    }

    private void updateView() {
        mAdapter.setItems(mAlarms);
        if (mAlarms.isEmpty()) {
            mTvEmpty.setVisibility(View.VISIBLE);
            mAlarmListView.setVisibility(View.GONE);
        } else {
            mTvEmpty.setVisibility(View.GONE);
            mAlarmListView.setVisibility(View.VISIBLE);
        }
    }

    private void rescheduleAllAdvanceNotifications() {
        reconcileAndPresent();
    }

    private void scheduleAlarmInSystem(AlarmListAdapter.AlarmItemModel item) {
        if (!item.isEnabled) {
            cancelAlarmInSystem(item);
            return;
        }
        mAlarmStore.save(toStoredAlarm(item, mAlarmStore.findById(item.id)));
        reconcileAndPresent();
    }

    private void cancelAlarmInSystem(AlarmListAdapter.AlarmItemModel item) {
        mApplication.registrationGateway().cancel(item.id);
        mApplication.registrationGateway().cancelAdvanceNotification(item.id);
    }

    private void reconcileAndPresent() {
        ReconcileReport report = mApplication.reconciler().reconcile(ReconcileReason.APP_RESUME);
        loadAlarms();
        mProtectionPresenter.present(report);
    }

    private AlarmListAdapter.AlarmItemModel toViewModel(StoredAlarm alarm) {
        AlarmListAdapter.AlarmItemModel item = new AlarmListAdapter.AlarmItemModel(
                alarm.id, alarm.hour, alarm.minute, alarm.enabled, alarm.repeatMode,
                alarm.daysBitmask, alarm.label, alarm.quickNap, alarm.ringtoneUri,
                alarm.ringtoneTitle, alarm.vibrateEnabled, new ArrayList<>(alarm.skippedDates));
        item.nextTriggerAtMs = alarm.nextTriggerAtMs;
        return item;
    }

    private StoredAlarm toStoredAlarm(
            AlarmListAdapter.AlarmItemModel item, StoredAlarm existing) {
        boolean scheduleChanged = existing == null
                || existing.hour != item.hour
                || existing.minute != item.minute
                || existing.enabled != item.isEnabled
                || existing.repeatMode != item.repeatMode
                || existing.daysBitmask != item.daysBitmask
                || existing.quickNap != item.isQuickNap
                || !existing.skippedDates.equals(new LinkedHashSet<>(item.skippedDates));
        return new StoredAlarm(
                item.id,
                item.hour,
                item.minute,
                item.isEnabled,
                item.repeatMode,
                item.daysBitmask,
                item.label,
                item.isQuickNap,
                item.ringtoneUri,
                item.ringtoneTitle,
                item.vibrateEnabled,
                new LinkedHashSet<>(item.skippedDates),
                scheduleChanged ? 0L : existing.nextTriggerAtMs,
                existing == null ? 0L : existing.occurrenceGeneration,
                scheduleChanged ? "" : existing.occurrenceId,
                scheduleChanged ? "" : existing.lastClaimedOccurrenceId,
                existing == null ? MissedState.NONE : existing.missedState,
                existing == null ? "" : existing.sourceJson);
    }
}
