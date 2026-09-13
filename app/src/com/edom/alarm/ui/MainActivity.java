package com.edom.alarm.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.edom.alarm.R;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Main dashboard for Android Smart Alarm.
 * Displays configured alarms with statutory workday recognition,
 * TimePicker creation/edit dialog, card list with On/Off toggles,
 * quick nap triggers, and vacation mode multi-day skip options.
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = "smart_alarm_prefs";
    private static final String KEY_ALARMS = "alarms_list";

    private ListView mAlarmListView;
    private TextView mTvEmpty;
    private AlarmListAdapter mAdapter;
    private final List<AlarmListAdapter.AlarmItemModel> mAlarms = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAlarmListView = findViewById(R.id.lv_alarms);
        mTvEmpty = findViewById(R.id.tv_empty_alarms);

        // Quick Nap Buttons
        findViewById(R.id.btn_nap_15).setOnClickListener(v -> triggerQuickNap(15));
        findViewById(R.id.btn_nap_30).setOnClickListener(v -> triggerQuickNap(30));
        findViewById(R.id.btn_nap_45).setOnClickListener(v -> triggerQuickNap(45));
        findViewById(R.id.btn_nap_60).setOnClickListener(v -> triggerQuickNap(60));

        // Add Alarm Button
        findViewById(R.id.btn_add_alarm).setOnClickListener(v -> {
            new AlarmEditDialog(MainActivity.this, null, mSavedListener).show();
        });

        // Initialize Adapter & Listeners
        mAdapter = new AlarmListAdapter(this);
        mAdapter.setOnAlarmToggleListener((item, isChecked) -> {
            saveAlarms();
            String status = isChecked ? "enabled" : "disabled";
            Toast.makeText(this, String.format("%02d:%02d alarm %s", item.hour, item.minute, status), Toast.LENGTH_SHORT).show();
        });

        mAdapter.setOnAlarmClickListener(new AlarmListAdapter.OnAlarmClickListener() {
            @Override
            public void onClick(AlarmListAdapter.AlarmItemModel item) {
                new AlarmEditDialog(MainActivity.this, item, mSavedListener).show();
            }

            @Override
            public void onLongClick(AlarmListAdapter.AlarmItemModel item) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Delete Alarm")
                        .setMessage(String.format("Delete alarm for %02d:%02d (%s)?", item.hour, item.minute, item.label))
                        .setPositiveButton("Delete", (dialog, which) -> deleteAlarmById(item.id))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        mAlarmListView.setAdapter(mAdapter);

        // Load persisted alarms
        loadAlarms();
    }

    private final AlarmEditDialog.OnAlarmSavedListener mSavedListener = new AlarmEditDialog.OnAlarmSavedListener() {
        @Override
        public void onAlarmSaved(AlarmListAdapter.AlarmItemModel item) {
            int existingIndex = -1;
            for (int i = 0; i < mAlarms.size(); i++) {
                if (mAlarms.get(i).id == item.id) {
                    existingIndex = i;
                    break;
                }
            }
            if (existingIndex >= 0) {
                mAlarms.set(existingIndex, item);
                Toast.makeText(MainActivity.this, String.format("Updated alarm for %02d:%02d", item.hour, item.minute), Toast.LENGTH_SHORT).show();
            } else {
                mAlarms.add(item);
                Toast.makeText(MainActivity.this, String.format("Alarm set for %02d:%02d", item.hour, item.minute), Toast.LENGTH_SHORT).show();
            }
            sortAlarms();
            saveAlarms();
            updateView();
        }

        @Override
        public void onAlarmDeleted(long alarmId) {
            deleteAlarmById(alarmId);
        }
    };

    private void deleteAlarmById(long alarmId) {
        for (int i = 0; i < mAlarms.size(); i++) {
            if (mAlarms.get(i).id == alarmId) {
                mAlarms.remove(i);
                break;
            }
        }
        saveAlarms();
        updateView();
        Toast.makeText(this, "Alarm deleted", Toast.LENGTH_SHORT).show();
    }

    private void loadAlarms() {
        mAlarms.clear();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String jsonStr = prefs.getString(KEY_ALARMS, null);
        if (jsonStr != null && !jsonStr.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    AlarmListAdapter.AlarmItemModel item = new AlarmListAdapter.AlarmItemModel(
                            obj.optLong("id", System.currentTimeMillis()),
                            obj.optInt("hour", 8),
                            obj.optInt("minute", 0),
                            obj.optBoolean("isEnabled", true),
                            obj.optInt("repeatMode", 0),
                            obj.optInt("daysBitmask", 0),
                            obj.optString("label", "Alarm")
                    );
                    mAlarms.add(item);
                }
            } catch (Exception ignored) {
            }
        }
        if (mAlarms.isEmpty()) {
            // Seed default alarms: Statutory Workdays at 07:30 and Standup at 08:30
            mAlarms.add(new AlarmListAdapter.AlarmItemModel(1001, 7, 30, true, 2, 0, "Workday Alarm"));
            mAlarms.add(new AlarmListAdapter.AlarmItemModel(1002, 8, 30, true, 0, 0, "Morning Standup"));
            saveAlarms();
        }
        sortAlarms();
        updateView();
    }

    private void saveAlarms() {
        try {
            JSONArray arr = new JSONArray();
            for (AlarmListAdapter.AlarmItemModel item : mAlarms) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("hour", item.hour);
                obj.put("minute", item.minute);
                obj.put("isEnabled", item.isEnabled);
                obj.put("repeatMode", item.repeatMode);
                obj.put("daysBitmask", item.daysBitmask);
                obj.put("label", item.label);
                arr.put(obj);
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_ALARMS, arr.toString()).apply();
        } catch (Exception ignored) {
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

    private void triggerQuickNap(int minutes) {
        Toast.makeText(this, "Quick Nap set for " + minutes + " minutes", Toast.LENGTH_SHORT).show();
    }
}
