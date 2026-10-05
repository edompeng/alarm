package com.edom.alarm.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.edom.alarm.R;
import com.edom.alarm.core.scheduler.AlarmCapabilityEvaluator;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.CapabilitySnapshot;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedAlarmOutcome;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedState;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ProtectionLevel;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ReconcileReport;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import com.edom.alarm.core.scheduler.AlarmDeliveryPolicy;
import com.edom.alarm.core.scheduler.AlarmScheduleStore;
import java.util.ArrayList;
import java.util.List;

/** Presents current delivery limitations and independently durable missed outcomes. */
public final class AlarmProtectionPresenter {
    public static final int REQUEST_CODE_POST_NOTIFICATIONS = 1001;

    private final Activity activity;
    private final View warningContainer;
    private final TextView warningText;
    private final Button correctiveAction;
    private final View helpAction;
    private final AlarmScheduleStore store;
    private final AlarmCapabilityEvaluator evaluator;
    private final Runnable retryAction;

    public AlarmProtectionPresenter(
            Activity activity,
            View warningContainer,
            TextView warningText,
            Button correctiveAction,
            View helpAction,
            AlarmScheduleStore store,
            AlarmCapabilityEvaluator evaluator,
            Runnable retryAction) {
        this.activity = activity;
        this.warningContainer = warningContainer;
        this.warningText = warningText;
        this.correctiveAction = correctiveAction;
        this.helpAction = helpAction;
        this.store = store;
        this.evaluator = evaluator;
        this.retryAction = retryAction;
        this.helpAction.setOnClickListener(view -> showForceStopGuidance());
    }

    public void present(ReconcileReport report) {
        CapabilitySnapshot capabilities = evaluator.evaluate();
        List<Long> enabledIds = new ArrayList<>();
        for (StoredAlarm alarm : store.loadAll()) {
            if (alarm.enabled) {
                enabledIds.add(alarm.id);
            }
        }
        ProtectionLevel level = AlarmDeliveryPolicy.deriveProtection(
                capabilities, report, enabledIds);
        warningContainer.setVisibility(
                enabledIds.isEmpty() || level == ProtectionLevel.FULL ? View.GONE : View.VISIBLE);
        if (enabledIds.isEmpty() || level == ProtectionLevel.FULL) {
            correctiveAction.setOnClickListener(null);
            return;
        }

        if (!capabilities.exactAlarmAvailable) {
            warningText.setText(R.string.alarm_protection_exact_limited);
            openSettingsOnClick(capabilities);
        } else if (!capabilities.notificationsAvailable) {
            warningText.setText(R.string.alarm_protection_notification_limited);
            openSettingsOnClick(capabilities);
        } else if (!capabilities.fullScreenAvailable) {
            warningText.setText(R.string.alarm_protection_full_screen_limited);
            openSettingsOnClick(capabilities);
        } else {
            warningText.setText(activity.getString(
                    R.string.alarm_protection_registration_failed,
                    report == null ? "?" : report.failedAlarmIds.toString()));
            correctiveAction.setOnClickListener(view -> retryAction.run());
        }
    }

    public void presentPendingMissedOutcomes() {
        List<MissedAlarmOutcome> outcomes = store.loadPendingMissedOutcomes();
        if (outcomes.isEmpty() || activity.isFinishing()) {
            return;
        }
        StringBuilder message = new StringBuilder();
        for (MissedAlarmOutcome outcome : outcomes) {
            if (message.length() > 0) {
                message.append('\n');
            }
            int resource = outcome.missedState == MissedState.MISSED_QUICK_NAP
                    ? R.string.missed_alarm_quick_nap
                    : outcome.missedState == MissedState.MISSED_RECURRING
                            ? R.string.missed_alarm_recurring : R.string.missed_alarm_once;
            message.append(activity.getString(resource, outcome.label));
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.alarm_protection_limited)
                .setMessage(message.toString())
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    for (MissedAlarmOutcome outcome : outcomes) {
                        store.acknowledgeMissedOutcome(outcome.outcomeId);
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void openSettingsOnClick(CapabilitySnapshot capabilities) {
        correctiveAction.setOnClickListener(view -> {
            // On Android 13+, if notifications are not granted, request runtime permission first!
            if (Build.VERSION.SDK_INT >= 33 && !capabilities.notificationsAvailable) {
                if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            REQUEST_CODE_POST_NOTIFICATIONS);
                    return;
                }
            }

            safeLaunchSettings(capabilities);
        });
    }

    public void safeLaunchSettings(CapabilitySnapshot capabilities) {
        try {
            Intent intent = evaluator.highestImpactSettingsIntent(capabilities);
            activity.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallback.setData(Uri.parse("package:" + activity.getPackageName()));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(fallback);
            } catch (Exception fallbackEx) {
                try {
                    Intent systemSettings = new Intent(Settings.ACTION_SETTINGS);
                    systemSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(systemSettings);
                } catch (Exception ex) {
                    Toast.makeText(activity, R.string.alarm_protection_open_settings_failed, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void showForceStopGuidance() {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.alarm_protection_help)
                .setMessage(activity.getString(
                        R.string.alarm_force_stop_guidance) + "\n\nAndroid API " + Build.VERSION.SDK_INT)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
