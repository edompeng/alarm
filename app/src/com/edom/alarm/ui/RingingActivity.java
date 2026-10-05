package com.edom.alarm.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import com.edom.alarm.R;
import com.edom.alarm.AlarmApplication;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import com.edom.alarm.core.scheduler.AlarmRingingService;
import java.util.Locale;

/** Lockscreen presentation and user-command surface for the active ringing service. */
public final class RingingActivity extends Activity {
    private long alarmId;
    private String occurrenceId;

    private static volatile boolean sVisible;
    private static volatile long sHiddenAtMs;

    /**
     * True while this screen is on top, or was on top within {@code windowMs}. The
     * ringing service uses this to tell a real power-button press apart from an
     * unrelated display timeout before dismissing the alarm.
     */
    public static boolean isOnScreenOrRecentlyShown(long windowMs) {
        if (sVisible) {
            return true;
        }
        long hiddenAt = sHiddenAtMs;
        return hiddenAt > 0L && System.currentTimeMillis() - hiddenAt <= windowMs;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocalizationManager.wrapContext(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        if (!bindRingingIntent(getIntent())) {
            finish();
            return;
        }

        setContentView(R.layout.activity_ringing);
        renderAlarmDetails();
        Button dismiss = findViewById(R.id.btn_dismiss);
        Button snooze = findViewById(R.id.btn_snooze);
        dismiss.setOnClickListener(view -> sendCommand(AlarmRingingService.ACTION_DISMISS));
        snooze.setOnClickListener(view -> sendCommand(AlarmRingingService.ACTION_SNOOZE));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // A second alarm can reuse this singleInstance activity; re-bind so the
        // dismiss/snooze commands always carry the active occurrence.
        if (!bindRingingIntent(intent)) {
            finish();
            return;
        }
        renderAlarmDetails();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sVisible = true;
    }

    @Override
    protected void onPause() {
        sVisible = false;
        sHiddenAtMs = System.currentTimeMillis();
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            sendCommand(AlarmRingingService.ACTION_SNOOZE);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean bindRingingIntent(Intent intent) {
        alarmId = intent == null ? -1L
                : intent.getLongExtra(AlarmRingingService.EXTRA_ALARM_ID, -1L);
        occurrenceId = intent == null
                ? null : intent.getStringExtra(AlarmRingingService.EXTRA_OCCURRENCE_ID);
        return alarmId >= 0L && occurrenceId != null && !occurrenceId.isEmpty();
    }

    private void renderAlarmDetails() {
        TextView tvTime = findViewById(R.id.tv_ring_time);
        TextView tvLabel = findViewById(R.id.tv_ring_label);
        StoredAlarm alarm = AlarmApplication.from(this).alarmStore().findById(alarmId);
        if (alarm == null) {
            return;
        }
        if (tvTime != null) {
            tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute));
        }
        if (tvLabel != null && alarm.label != null && !alarm.label.isEmpty()) {
            tvLabel.setText(alarm.label);
        }
    }

    private void sendCommand(String action) {
        Intent command = new Intent(this, AlarmRingingService.class)
                .setAction(action)
                .putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmRingingService.EXTRA_OCCURRENCE_ID, occurrenceId);
        startService(command);
        finish();
    }
}
