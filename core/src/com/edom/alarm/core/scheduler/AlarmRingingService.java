package com.edom.alarm.core.scheduler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
import com.edom.alarm.ui.RingingActivity;

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
    private static final long[] VIBRATION_PATTERN = {0L, 500L, 500L};

    private PowerManager.WakeLock wakeLock;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private final android.os.Handler mainHandler = new android.os.Handler();
    private Runnable timeoutAction;
    private long activeAlarmId = -1L;
    private String activeOccurrenceId = "";
    private long activeSessionToken;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction()) || ACTION_DISMISS.equals(intent.getAction())
                || ACTION_SNOOZE.equals(intent.getAction())) {
            if (isActiveCommand(intent)) {
                if (ACTION_SNOOZE.equals(intent.getAction()) && !scheduleSnooze()) {
                    return START_NOT_STICKY;
                }
                if (ACTION_DISMISS.equals(intent.getAction())) {
                    completeActiveOccurrence();
                }
                stopSelf();
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
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                    AlarmDeliveryPolicy.deterministicRequestCode(alarmId),
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(AlarmDeliveryPolicy.deterministicRequestCode(alarmId), notification);
        }
        acquireWakeLock();
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
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.alarm_ringing))
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setFullScreenIntent(fullScreen, true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.dismiss_alarm),
                        dismiss).build())
                .build();
    }

    public static void ensureChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alarm_notification_channel),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.alarm_notification_channel_description));
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
        Uri uri = ringtoneUri == null || ringtoneUri.isEmpty()
                ? Settings.System.DEFAULT_ALARM_ALERT_URI : Uri.parse(ringtoneUri);
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setLooping(true);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.prepareAsync();
        } catch (Exception failure) {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
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

    private boolean scheduleSnooze() {
        AlarmApplication application = AlarmApplication.from(this);
        if (!(application.alarmStore() instanceof SharedPreferencesAlarmScheduleStore)) {
            Log.e(TAG, "Alarm store does not support an atomic snooze transition");
            return false;
        }
        SharedPreferencesAlarmScheduleStore store =
                (SharedPreferencesAlarmScheduleStore) application.alarmStore();
        StoredAlarm snoozed = store.createSnoozedOccurrence(
                activeAlarmId, System.currentTimeMillis());
        if (snoozed == null) {
            Log.i(TAG, "Snooze rejected for alarm " + activeAlarmId);
            return false;
        }
        application.registrationGateway().cancelAdvanceNotification(activeAlarmId);
        Registration registration = application.registrationGateway().schedule(snoozed);
        if (!registration.isRegistered()) {
            Log.e(TAG, "Failed to register snooze for alarm " + activeAlarmId
                    + ": " + registration.failureMessage);
            return false;
        }
        return true;
    }

    private void completeActiveOccurrence() {
        AlarmApplication application = AlarmApplication.from(this);
        if (application.alarmStore() instanceof SharedPreferencesAlarmScheduleStore) {
            ((SharedPreferencesAlarmScheduleStore) application.alarmStore())
                    .completeRingingOccurrence(activeAlarmId);
        } else {
            StoredAlarm alarm = application.alarmStore().findById(activeAlarmId);
            if (alarm != null && alarm.quickNap) {
                application.alarmStore().delete(activeAlarmId);
            } else if (alarm != null && alarm.repeatMode == 0) {
                application.alarmStore().save(alarm.withEnabled(false));
            }
        }
        StoredAlarm remaining = application.alarmStore().findById(activeAlarmId);
        if (remaining == null || !remaining.enabled) {
            application.registrationGateway().cancel(activeAlarmId);
            application.registrationGateway().cancelAdvanceNotification(activeAlarmId);
        }
    }

    private void scheduleSafetyTimeout(long sessionToken) {
        timeoutAction = () -> {
            if (sessionToken != activeSessionToken || activeAlarmId < 0L) {
                return;
            }
            completeActiveOccurrence();
            stopSelf();
        };
        mainHandler.postDelayed(timeoutAction, SAFETY_TIMEOUT_MS);
    }

    private void releaseResources() {
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
}
