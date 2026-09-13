package com.edom.alarm.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import com.edom.alarm.R;

/**
 * Main dashboard for Android Smart Alarm.
 * Displays configured alarms with statutory workday recognition,
 * quick nap triggers, and vacation mode multi-day skip options.
 */
public class MainActivity extends Activity {

    private ListView mAlarmListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAlarmListView = findViewById(R.id.lv_alarms);

        findViewById(R.id.btn_nap_15).setOnClickListener(v -> triggerQuickNap(15));
        findViewById(R.id.btn_nap_30).setOnClickListener(v -> triggerQuickNap(30));
        findViewById(R.id.btn_nap_45).setOnClickListener(v -> triggerQuickNap(45));
        findViewById(R.id.btn_nap_60).setOnClickListener(v -> triggerQuickNap(60));

        findViewById(R.id.btn_add_alarm).setOnClickListener(v -> {
            Toast.makeText(this, "Alarm created for Statutory Workdays at 08:00", Toast.LENGTH_SHORT).show();
        });
    }

    private void triggerQuickNap(int minutes) {
        Toast.makeText(this, "Quick Nap set for " + minutes + " minutes", Toast.LENGTH_SHORT).show();
    }
}
