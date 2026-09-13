package com.edom.alarm.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import com.edom.alarm.R;
import com.edom.alarm.core.scheduler.AlarmRingingService;

/** Lockscreen presentation and user-command surface for the active ringing service. */
public final class RingingActivity extends Activity {
    private long alarmId;
    private String occurrenceId;

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

        alarmId = getIntent().getLongExtra(AlarmRingingService.EXTRA_ALARM_ID, -1L);
        occurrenceId = getIntent().getStringExtra(AlarmRingingService.EXTRA_OCCURRENCE_ID);
        if (alarmId < 0L || occurrenceId == null || occurrenceId.isEmpty()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_ringing);
        Button dismiss = findViewById(R.id.btn_dismiss);
        Button snooze = findViewById(R.id.btn_snooze);
        dismiss.setOnClickListener(view -> sendCommand(AlarmRingingService.ACTION_DISMISS));
        snooze.setOnClickListener(view -> sendCommand(AlarmRingingService.ACTION_SNOOZE));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            sendCommand(AlarmRingingService.ACTION_SNOOZE);
            return true;
        }
        return super.onKeyDown(keyCode, event);
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
