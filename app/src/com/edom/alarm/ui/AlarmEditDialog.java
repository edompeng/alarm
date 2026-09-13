package com.edom.alarm.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.ToggleButton;
import com.edom.alarm.R;

import java.util.Calendar;

public class AlarmEditDialog extends Dialog {

    public interface OnAlarmSavedListener {
        void onAlarmSaved(AlarmListAdapter.AlarmItemModel item);
        void onAlarmDeleted(long alarmId);
    }

    private final AlarmListAdapter.AlarmItemModel mExistingItem;
    private final OnAlarmSavedListener mListener;

    private TimePicker mTpTime;
    private EditText mEtLabel;
    private RadioGroup mRgRepeat;
    private LinearLayout mLayoutCustomDays;
    private ToggleButton mTbMon, mTbTue, mTbWed, mTbThu, mTbFri, mTbSat, mTbSun;
    private Button mBtnDelete;

    public AlarmEditDialog(Context context, AlarmListAdapter.AlarmItemModel existingItem,
                           OnAlarmSavedListener listener) {
        super(context);
        mExistingItem = existingItem;
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_alarm_edit);

        TextView tvTitle = findViewById(R.id.tv_dialog_title);
        mTpTime = findViewById(R.id.tp_time);
        mEtLabel = findViewById(R.id.et_alarm_label);
        mRgRepeat = findViewById(R.id.rg_repeat_mode);
        mLayoutCustomDays = findViewById(R.id.layout_custom_days);
        mBtnDelete = findViewById(R.id.btn_delete_alarm);

        mTbMon = findViewById(R.id.tb_mon);
        mTbTue = findViewById(R.id.tb_tue);
        mTbWed = findViewById(R.id.tb_wed);
        mTbThu = findViewById(R.id.tb_thu);
        mTbFri = findViewById(R.id.tb_fri);
        mTbSat = findViewById(R.id.tb_sat);
        mTbSun = findViewById(R.id.tb_sun);

        mTpTime.setIs24HourView(true);

        if (mExistingItem != null) {
            tvTitle.setText("Edit Alarm");
            mTpTime.setHour(mExistingItem.hour);
            mTpTime.setMinute(mExistingItem.minute);
            mEtLabel.setText(mExistingItem.label);

            if (mExistingItem.repeatMode == 2) {
                mRgRepeat.check(R.id.rb_repeat_workdays);
            } else if (mExistingItem.repeatMode == 1) {
                mRgRepeat.check(R.id.rb_repeat_custom);
                mLayoutCustomDays.setVisibility(View.VISIBLE);
                setDaysBitmask(mExistingItem.daysBitmask);
            } else {
                mRgRepeat.check(R.id.rb_repeat_once);
            }

            mBtnDelete.setVisibility(View.VISIBLE);
            mBtnDelete.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onAlarmDeleted(mExistingItem.id);
                }
                dismiss();
            });
        } else {
            tvTitle.setText("Add New Alarm");
            Calendar now = Calendar.getInstance();
            now.add(Calendar.HOUR_OF_DAY, 1);
            mTpTime.setHour(now.get(Calendar.HOUR_OF_DAY));
            mTpTime.setMinute(0);
            mRgRepeat.check(R.id.rb_repeat_once);
        }

        mRgRepeat.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_repeat_custom) {
                mLayoutCustomDays.setVisibility(View.VISIBLE);
            } else {
                mLayoutCustomDays.setVisibility(View.GONE);
            }
        });

        findViewById(R.id.btn_cancel_alarm).setOnClickListener(v -> dismiss());
        findViewById(R.id.btn_save_alarm).setOnClickListener(v -> saveAlarm());
    }

    private void saveAlarm() {
        int hour = mTpTime.getHour();
        int minute = mTpTime.getMinute();
        String label = mEtLabel.getText().toString().trim();
        if (label.isEmpty()) {
            label = "Alarm";
        }

        int repeatMode = 0; // default once
        int daysBitmask = 0;

        int checkedId = mRgRepeat.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_repeat_workdays) {
            repeatMode = 2; // Statutory workdays
        } else if (checkedId == R.id.rb_repeat_custom) {
            repeatMode = 1;
            daysBitmask = getDaysBitmask();
            if (daysBitmask == 0) {
                repeatMode = 0; // Fallback to once if no days toggled
            }
        } else {
            repeatMode = 0; // Ring once
        }

        long id = mExistingItem != null ? mExistingItem.id : System.currentTimeMillis();
        boolean isEnabled = true;

        AlarmListAdapter.AlarmItemModel item = new AlarmListAdapter.AlarmItemModel(
                id, hour, minute, isEnabled, repeatMode, daysBitmask, label);

        if (mListener != null) {
            mListener.onAlarmSaved(item);
        }
        dismiss();
    }

    private int getDaysBitmask() {
        int mask = 0;
        if (mTbMon.isChecked()) mask |= (1 << 0);
        if (mTbTue.isChecked()) mask |= (1 << 1);
        if (mTbWed.isChecked()) mask |= (1 << 2);
        if (mTbThu.isChecked()) mask |= (1 << 3);
        if (mTbFri.isChecked()) mask |= (1 << 4);
        if (mTbSat.isChecked()) mask |= (1 << 5);
        if (mTbSun.isChecked()) mask |= (1 << 6);
        return mask;
    }

    private void setDaysBitmask(int mask) {
        mTbMon.setChecked((mask & (1 << 0)) != 0);
        mTbTue.setChecked((mask & (1 << 1)) != 0);
        mTbWed.setChecked((mask & (1 << 2)) != 0);
        mTbThu.setChecked((mask & (1 << 3)) != 0);
        mTbFri.setChecked((mask & (1 << 4)) != 0);
        mTbSat.setChecked((mask & (1 << 5)) != 0);
        mTbSun.setChecked((mask & (1 << 6)) != 0);
    }
}
