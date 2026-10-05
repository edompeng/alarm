package com.edom.alarm.core.scheduler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import com.edom.alarm.AlarmApplication;
import com.edom.alarm.R;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.Registration;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import com.edom.alarm.ui.LocalizationManager;
import com.edom.alarm.ui.RingingActivity;
import com.edom.alarm.ui.SettingsDialog;

/** Owns bounded ringing resources for one claimed occurrence. */
public final class AlarmRingingService extends Service {
    public static final String ACTION_START = "com.edom.alarm.ACTION_START_RINGING";
    public static final String ACTION_STOP = "com.edom.alarm.ACTION_STOP_RINGING";
    public static final String ACTION_SNOOZE = "com.edom.alarm.ACTION_SNOOZE";
    public static final String ACTION_DISMISS = "com.edom.alarm.ACTION_DISMISS";
    public static final String EXTRA_ALARM_ID = "extra_alarm_id";
    public static final String EXTRA_OCCURRENCE_ID = "extra_occurrence_id";
    public static final String EXTRA_RINGTONE_URI = "extra_ringtone_uri";
    public static final String EXTRA_VIBRATE_ENABLED = "extra_vibrate_enabled";

    private static final String CHANNEL_ID = "channel_smart_alarm_high_priority";
    private static final String TAG = "SmartAlarm:Ringing";
    private static final long SAFETY_TIMEOUT_MS = 10L * 60L * 1000L;
    private static final long RINGING_SCREEN_GRACE_MS = 5_000L;
    private static final long[] VIBRATION_PATTERN = {0L, 500L, 500L};

    private PowerManager.WakeLock wakeLock;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private BroadcastReceiver powerButtonReceiver;
    private final android.os.Handler mainHandler = new android.os.Handler();
    private Runnable timeoutAction;
    private long activeAlarmId = -1L;
    private String activeOccurrenceId = "";
    private long activeSessionToken;
    private boolean audioFallbackAttempted;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction()) || ACTION_DISMISS.equals(intent.getAction())
                || ACTION_SNOOZE.equals(intent.getAction())) {
            if (isActiveCommand(intent)) {
                if (ACTION_SNOOZE.equals(intent.getAction()) && !scheduleSnooze(activeAlarmId)) {
                    return START_NOT_STICKY;
                }
                if (ACTION_DISMISS.equals(intent.getAction())) {
                    completeActiveOccurrence(activeAlarmId);
                }
                stopSelf();
            } else if (handleClaimedOccurrenceCommand(intent)) {
                if (activeAlarmId < 0L) {
                    // Never tear down a different alarm that is still ringing.
                    stopSelf();
                }
            } else if (activeAlarmId < 0L) {
                // Stale command for an already finished session: do not leave an empty
                // started service alive without a foreground notification.
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        long alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L);
        String occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID);
        if (alarmId < 0L || occurrenceId == null || occurrenceId.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (alarmId == activeAlarmId && occurrenceId.equals(activeOccurrenceId)) {
            return START_NOT_STICKY;
        }
        releaseResources();
        activeAlarmId = alarmId;
        activeOccurrenceId = occurrenceId;
        long sessionToken = ++activeSessionToken;

        ensureChannel(this);
        Notification notification = buildNotification(intent, alarmId);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                        AlarmDeliveryPolicy.deterministicRequestCode(alarmId),
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(AlarmDeliveryPolicy.deterministicRequestCode(alarmId), notification);
            }
        } catch (RuntimeException rejection) {
            // Android 12+ may refuse a background foreground-service start. Degrade to
            // an audible high-priority notification instead of crashing silently.
            Log.e(TAG, "Foreground ringing service was rejected", rejection);
            StoredAlarm claimed = AlarmApplication.from(this).alarmStore().findById(alarmId);
            if (claimed != null) {
                AlarmTriggerReceiver.postFallbackNotification(this, claimed);
            }
            releaseResources();
            stopSelf();
            return START_NOT_STICKY;
        }
        acquireWakeLock();
        registerPowerButtonDismiss();
        startAudio(intent.getStringExtra(EXTRA_RINGTONE_URI));
        if (intent.getBooleanExtra(EXTRA_VIBRATE_ENABLED, true)) {
            startVibration();
        }
        scheduleSafetyTimeout(sessionToken);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseResources();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification(Intent source, long alarmId) {
        Context localizedContext = LocalizationManager.wrapContext(this);
        Intent presentation = new Intent(this, RingingActivity.class)
                .setAction(ACTION_START)
                .putExtras(source)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreen = PendingIntent.getActivity(
                this,
                AlarmDeliveryPolicy.deterministicRequestCode(alarmId),
                presentation,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent dismissIntent = new Intent(this, AlarmRingingService.class)
                .setAction(ACTION_DISMISS)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_OCCURRENCE_ID, source.getStringExtra(EXTRA_OCCURRENCE_ID))
                .setData(new Uri.Builder()
                        .scheme("alarm")
                        .authority("dismiss")
                        .appendPath(Long.toString(alarmId))
                        .appendPath(source.getStringExtra(EXTRA_OCCURRENCE_ID))
                        .build());
        PendingIntent dismiss = PendingIntent.getService(
                this,
                AlarmDeliveryPolicy.deterministicRequestCode(alarmId) ^ 0x20000000,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(localizedContext.getString(R.string.app_name))
                .setContentText(localizedContext.getString(R.string.alarm_ringing))
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setFullScreenIntent(fullScreen, true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        localizedContext.getString(R.string.dismiss_alarm),
                        dismiss).build())
                .build();
    }

    public static void ensureChannel(Context context) {
        Context localizedContext = LocalizationManager.wrapContext(context);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                localizedContext.getString(R.string.alarm_notification_channel),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(
                localizedContext.getString(R.string.alarm_notification_channel_description));
        channel.enableVibration(true);
        channel.setSound(null, null);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void acquireWakeLock() {
        PowerManager manager = getSystemService(PowerManager.class);
        if (manager != null) {
            wakeLock = manager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "SmartAlarm:RingingService");
            wakeLock.acquire(SAFETY_TIMEOUT_MS);
        }
    }

    private void startAudio(String ringtoneUri) {
        audioFallbackAttempted = false;
        Uri primary = ringtoneUri == null || ringtoneUri.isEmpty()
                ? Settings.System.DEFAULT_ALARM_ALERT_URI : Uri.parse(ringtoneUri);
        if (!prepareAudio(primary) && !tryDefaultAudio(primary)) {
            Log.e(TAG, "Unable to play any alarm sound for the active occurrence");
        }
    }

    private boolean tryDefaultAudio(Uri failedUri) {
        Uri fallback = Settings.System.DEFAULT_ALARM_ALERT_URI;
        if (audioFallbackAttempted || fallback == null || fallback.equals(failedUri)) {
            return false;
        }
        audioFallbackAttempted = true;
        Log.w(TAG, "Falling back to the system alarm sound");
        return prepareAudio(fallback);
    }

    private boolean prepareAudio(Uri uri) {
        if (uri == null) {
            return false;
        }
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setLooping(true);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnErrorListener((player, what, extra) -> {
                Log.e(TAG, "MediaPlayer error " + what + "/" + extra + " for " + uri);
                releasePlayer();
                tryDefaultAudio(uri);
                return true;
            });
            mediaPlayer.prepareAsync();
            return true;
        } catch (Exception failure) {
            Log.e(TAG, "Unable to prepare alarm audio " + uri, failure);
            releasePlayer();
            return false;
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (RuntimeException ignored) {
                // Release is best-effort during error recovery.
            }
            mediaPlayer = null;
        }
    }

    private void startVibration() {
        vibrator = getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0));
        }
    }

    private boolean isActiveCommand(Intent intent) {
        return intent.getLongExtra(EXTRA_ALARM_ID, -1L) == activeAlarmId
                && activeOccurrenceId.equals(intent.getStringExtra(EXTRA_OCCURRENCE_ID));
    }

    private boolean scheduleSnooze(long alarmId) {
        AlarmApplication application = AlarmApplication.from(this);
        if (!(application.alarmStore() instanceof SharedPreferencesAlarmScheduleStore)) {
            Log.e(TAG, "Alarm store does not support an atomic snooze transition");
            return false;
        }
        SharedPreferencesAlarmScheduleStore store =
                (SharedPreferencesAlarmScheduleStore) application.alarmStore();
        StoredAlarm snoozed = store.createSnoozedOccurrence(alarmId, System.currentTimeMillis());
        if (snoozed == null) {
            Log.i(TAG, "Snooze rejected for alarm " + alarmId);
            return false;
        }
        application.registrationGateway().cancelAdvanceNotification(alarmId);
        Registration registration = application.registrationGateway().schedule(snoozed);
        if (!registration.isRegistered()) {
            Log.e(TAG, "Failed to register snooze for alarm " + alarmId
                    + ": " + registration.failureMessage);
            return false;
        }
        return true;
    }

    private void completeActiveOccurrence(long alarmId) {
        AlarmApplication application = AlarmApplication.from(this);
        boolean deleteExpired = ExpiredAlarmPolicy.shouldDeleteExpired(this);
        if (application.alarmStore() instanceof SharedPreferencesAlarmScheduleStore) {
            ((SharedPreferencesAlarmScheduleStore) application.alarmStore())
                    .completeRingingOccurrence(alarmId, deleteExpired);
        } else {
            StoredAlarm alarm = application.alarmStore().findById(alarmId);
            if (alarm != null && (alarm.quickNap || (deleteExpired && alarm.repeatMode == 0))) {
                application.alarmStore().delete(alarmId);
            } else if (alarm != null && alarm.repeatMode == 0) {
                application.alarmStore().save(alarm.withEnabled(false));
            }
        }
        StoredAlarm remaining = application.alarmStore().findById(alarmId);
        if (remaining == null || !remaining.enabled) {
            application.registrationGateway().cancel(alarmId);
            application.registrationGateway().cancelAdvanceNotification(alarmId);
        }
    }

    /**
     * Honors snooze/dismiss for an occurrence that was already claimed but whose ringing
     * session is no longer running (reclaimed process or rejected foreground handoff).
     */
    private boolean handleClaimedOccurrenceCommand(Intent intent) {
        long alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L);
        String occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID);
        if (alarmId < 0L || occurrenceId == null || occurrenceId.isEmpty()) {
            return false;
        }
        StoredAlarm alarm = AlarmApplication.from(this).alarmStore().findById(alarmId);
        if (alarm == null || !occurrenceId.equals(alarm.lastClaimedOccurrenceId)) {
            return false;
        }
        cancelRingingNotification(alarmId);
        if (ACTION_SNOOZE.equals(intent.getAction())) {
            return scheduleSnooze(alarmId);
        }
        completeActiveOccurrence(alarmId);
        return true;
    }

    private void cancelRingingNotification(long alarmId) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(AlarmDeliveryPolicy.deterministicRequestCode(alarmId));
        }
    }

    private void scheduleSafetyTimeout(long sessionToken) {
        timeoutAction = () -> {
            if (sessionToken != activeSessionToken || activeAlarmId < 0L) {
                return;
            }
            completeActiveOccurrence(activeAlarmId);
            stopSelf();
        };
        mainHandler.postDelayed(timeoutAction, SAFETY_TIMEOUT_MS);
    }

    private void releaseResources() {
        unregisterPowerButtonDismiss();
        if (timeoutAction != null) {
            mainHandler.removeCallbacks(timeoutAction);
            timeoutAction = null;
        }
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (RuntimeException ignored) {
                // An asynchronous prepare may still be in flight; release remains authoritative.
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
        activeAlarmId = -1L;
        activeOccurrenceId = "";
        activeSessionToken++;
    }

    /**
     * Dismissing with the power button is observed through ACTION_SCREEN_OFF: the
     * platform consumes KEYCODE_POWER before it reaches application windows, so the
     * screen-off broadcast is the only reliable signal available to a normal app.
     */
    private void registerPowerButtonDismiss() {
        SharedPreferences preferences = getSharedPreferences(
                SharedPreferencesAlarmScheduleStore.PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (!preferences.getBoolean(
                SettingsDialog.KEY_POWER_DISMISS, SettingsDialog.DEFAULT_POWER_DISMISS)) {
            return;
        }
        if (powerButtonReceiver != null) {
            return;
        }
        powerButtonReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    return;
                }
                long alarmId = activeAlarmId;
                if (alarmId < 0L) {
                    return;
                }
                if (!RingingActivity.isOnScreenOrRecentlyShown(RINGING_SCREEN_GRACE_MS)) {
                    // The ringing screen was not on top (for example the display just
                    // timed out); keep ringing instead of dismissing silently.
                    Log.i(TAG, "Ignoring screen-off without a visible ringing screen");
                    return;
                }
                Log.i(TAG, "Power button pressed; dismissing alarm " + alarmId);
                completeActiveOccurrence(alarmId);
                stopSelf();
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(powerButtonReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(powerButtonReceiver, filter);
        }
    }

    private void unregisterPowerButtonDismiss() {
        if (powerButtonReceiver != null) {
            try {
                unregisterReceiver(powerButtonReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered by the framework.
            }
            powerButtonReceiver = null;
        }
    }
}
