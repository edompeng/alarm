package com.edom.alarm;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import com.edom.alarm.core.scheduler.AlarmCapabilityEvaluator;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import com.edom.alarm.core.scheduler.AlarmRegistrationGateway;
import com.edom.alarm.core.scheduler.AlarmRingingService;
import com.edom.alarm.core.scheduler.AlarmScheduleReconciler;
import com.edom.alarm.core.scheduler.AlarmScheduleStore;
import com.edom.alarm.core.scheduler.AlarmSystemScheduler;
import com.edom.alarm.core.scheduler.AlarmTriggerReceiver;
import com.edom.alarm.core.scheduler.DefaultNextOccurrenceCalculator;
import com.edom.alarm.core.scheduler.NextOccurrenceCalculator;
import com.edom.alarm.core.scheduler.SharedPreferencesAlarmScheduleStore;
import com.edom.alarm.ui.HolidaySyncManager;
import java.time.ZoneId;

/** Process-wide composition root for credential-protected alarm delivery dependencies. */
public final class AlarmApplication extends Application {
    private AlarmScheduleStore alarmStore;
    private NextOccurrenceCalculator nextOccurrenceCalculator;
    private AlarmCapabilityEvaluator capabilityEvaluator;
    private AlarmSystemScheduler registrationGateway;
    private AlarmScheduleReconciler reconciler;
    private AlarmTriggerReceiver.ServiceHandoff serviceHandoff;

    @Override
    public void onCreate() {
        super.onCreate();
        AlarmRingingService.ensureChannel(this);
        alarmStore = new SharedPreferencesAlarmScheduleStore(this);
        nextOccurrenceCalculator = new DefaultNextOccurrenceCalculator(
                date -> HolidaySyncManager.isStatutoryWorkday(this, date.toString()));
        capabilityEvaluator = new AlarmCapabilityEvaluator(this);
        registrationGateway = new AlarmSystemScheduler(this, alarmStore, capabilityEvaluator);
        reconciler = new AlarmScheduleReconciler(
                alarmStore,
                nextOccurrenceCalculator,
                registrationGateway,
                System::currentTimeMillis,
                ZoneId::systemDefault);
        serviceHandoff = alarm -> startForegroundService(createRingingIntent(alarm));
    }

    public static AlarmApplication from(Context context) {
        Context application = context.getApplicationContext();
        if (!(application instanceof AlarmApplication)) {
            throw new IllegalStateException("AlarmApplication is not installed");
        }
        return (AlarmApplication) application;
    }

    public AlarmScheduleStore alarmStore() {
        return alarmStore;
    }

    public NextOccurrenceCalculator nextOccurrenceCalculator() {
        return nextOccurrenceCalculator;
    }

    public AlarmCapabilityEvaluator capabilityEvaluator() {
        return capabilityEvaluator;
    }

    public AlarmRegistrationGateway registrationGateway() {
        return registrationGateway;
    }

    public AlarmScheduleReconciler reconciler() {
        return reconciler;
    }

    public AlarmTriggerReceiver.ServiceHandoff serviceHandoff() {
        return serviceHandoff;
    }

    private Intent createRingingIntent(StoredAlarm alarm) {
        return new Intent(this, AlarmRingingService.class)
                .setAction(AlarmRingingService.ACTION_START)
                .putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarm.id)
                .putExtra(AlarmRingingService.EXTRA_OCCURRENCE_ID, alarm.occurrenceId)
                .putExtra(AlarmRingingService.EXTRA_RINGTONE_URI, alarm.ringtoneUri)
                .putExtra(AlarmRingingService.EXTRA_VIBRATE_ENABLED, alarm.vibrateEnabled);
    }
}
