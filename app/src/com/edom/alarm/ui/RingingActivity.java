package com.edom.alarm.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import com.edom.alarm.R;

/**
 * Full-screen wakeup activity that displays on top of lock screen.
 * Handles audio playback, volume crescendo, haptics, and hardware key customization.
 */
public class RingingActivity extends Activity {

    private MediaPlayer mMediaPlayer;
    private Vibrator mVibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow window to turn screen on and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        setContentView(R.layout.activity_ringing);

        Button btnDismiss = findViewById(R.id.btn_dismiss);
        Button btnSnooze = findViewById(R.id.btn_snooze);

        btnDismiss.setOnClickListener(v -> dismissAlarm());
        btnSnooze.setOnClickListener(v -> snoozeAlarm());

        startRinging();
    }

    private void startRinging() {
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator != null && mVibrator.hasVibrator()) {
            // Heartbeat rhythm: pause 0, pulse 120ms, pause 100ms, pulse 160ms, pause 700ms
            long[] pattern = {0, 120, 100, 160, 700};
            mVibrator.vibrate(pattern, 0);
        }
    }

    private void stopRinging() {
        if (mVibrator != null) {
            mVibrator.cancel();
        }
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.stop();
                mMediaPlayer.release();
            } catch (Exception ignored) {}
            mMediaPlayer = null;
        }
    }

    private void dismissAlarm() {
        stopRinging();
        finish();
    }

    private void snoozeAlarm() {
        stopRinging();
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            // Pick up / Volume key pressed -> Snooze or Attenuate
            snoozeAlarm();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        stopRinging();
        super.onDestroy();
    }
}
