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
    private TextView mTvRingtoneName;
    private Button mBtnPickRingtone;
    private android.widget.Switch mSwVibrate;
    private Button mBtnDelete;

    private String mSelectedRingtoneUri;
    private String mSelectedRingtoneTitle;

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
        mTvRingtoneName = findViewById(R.id.tv_ringtone_name);
        mBtnPickRingtone = findViewById(R.id.btn_pick_ringtone);
        mSwVibrate = findViewById(R.id.sw_alarm_vibrate);

        mTbMon = findViewById(R.id.tb_mon);
        mTbTue = findViewById(R.id.tb_tue);
        mTbWed = findViewById(R.id.tb_wed);
        mTbThu = findViewById(R.id.tb_thu);
        mTbFri = findViewById(R.id.tb_fri);
        mTbSat = findViewById(R.id.tb_sat);
        mTbSun = findViewById(R.id.tb_sun);

        mTpTime.setIs24HourView(true);

        String defaultSoundTitle = getContext().getString(R.string.default_alarm_sound);

        if (mExistingItem != null) {
            tvTitle.setText(R.string.edit_alarm);
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

            mSelectedRingtoneUri = mExistingItem.ringtoneUri;
            mSelectedRingtoneTitle = mExistingItem.ringtoneTitle != null ? mExistingItem.ringtoneTitle : defaultSoundTitle;
            mTvRingtoneName.setText(mSelectedRingtoneTitle);
            mSwVibrate.setChecked(mExistingItem.vibrateEnabled);

            mBtnDelete.setVisibility(View.VISIBLE);
            mBtnDelete.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onAlarmDeleted(mExistingItem.id);
                }
                dismiss();
            });
        } else {
            tvTitle.setText(R.string.add_alarm_title);
            Calendar now = Calendar.getInstance();
            now.add(Calendar.HOUR_OF_DAY, 1);
            mTpTime.setHour(now.get(Calendar.HOUR_OF_DAY));
            mTpTime.setMinute(0);
            mRgRepeat.check(R.id.rb_repeat_once);

            mSelectedRingtoneUri = null;
            mSelectedRingtoneTitle = defaultSoundTitle;
            mTvRingtoneName.setText(mSelectedRingtoneTitle);
            mSwVibrate.setChecked(true);
        }

        mBtnPickRingtone.setOnClickListener(v -> showRingtonePickerDialog());

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

    private void showRingtonePickerDialog() {
        final java.util.List<String> titles = new java.util.ArrayList<>();
        final java.util.List<String> uris = new java.util.ArrayList<>();

        String defaultSoundTitle = getContext().getString(R.string.default_alarm_sound);
        titles.add(defaultSoundTitle);
        android.net.Uri defaultUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
        uris.add(defaultUri != null ? defaultUri.toString() : "");

        try {
            android.media.RingtoneManager rm = new android.media.RingtoneManager(getContext());
            rm.setType(android.media.RingtoneManager.TYPE_ALARM);
            android.database.Cursor cursor = rm.getCursor();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String title = cursor.getString(android.media.RingtoneManager.TITLE_COLUMN_INDEX);
                    android.net.Uri uri = rm.getRingtoneUri(cursor.getPosition());
                    if (title != null && uri != null) {
                        titles.add(title);
                        uris.add(uri.toString());
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (titles.size() <= 1) {
            titles.add(getContext().getString(R.string.ringtone_classic_bell));
            uris.add("content://settings/system/alarm_alert");
            titles.add(getContext().getString(R.string.ringtone_digital_beep));
            uris.add("android.resource://system/alarm_beep");
        }

        CharSequence[] items = titles.toArray(new CharSequence[0]);
        new android.app.AlertDialog.Builder(getContext())
                .setTitle(getContext().getString(R.string.select_alarm_sound))
                .setItems(items, (dialog, which) -> {
                    mSelectedRingtoneTitle = titles.get(which);
                    mSelectedRingtoneUri = uris.get(which);
                    mTvRingtoneName.setText(mSelectedRingtoneTitle);
                })
                .setNegativeButton(getContext().getString(R.string.cancel), null)
                .show();
    }

    private void saveAlarm() {
        int hour = mTpTime.getHour();
        int minute = mTpTime.getMinute();
        String label = mEtLabel.getText().toString().trim();
        if (label.isEmpty()) {
            label = getContext().getString(R.string.default_alarm_label);
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
                id, hour, minute, isEnabled, repeatMode, daysBitmask, label,
                mExistingItem != null && mExistingItem.isQuickNap,
                mSelectedRingtoneUri,
                mSelectedRingtoneTitle,
                mSwVibrate.isChecked(),
                mExistingItem != null ? mExistingItem.skippedDates : new java.util.ArrayList<>()
        );

        if (mListener != null) {
            mListener.onAlarmSaved(item);
        }
        dismiss();
    }

    private int getDaysBitmask() {
        int mask = 0;
        if (mTbSun.isChecked()) mask |= (1 << 0);
        if (mTbMon.isChecked()) mask |= (1 << 1);
        if (mTbTue.isChecked()) mask |= (1 << 2);
        if (mTbWed.isChecked()) mask |= (1 << 3);
        if (mTbThu.isChecked()) mask |= (1 << 4);
        if (mTbFri.isChecked()) mask |= (1 << 5);
        if (mTbSat.isChecked()) mask |= (1 << 6);
        return mask;
    }

    private void setDaysBitmask(int mask) {
        mTbSun.setChecked((mask & (1 << 0)) != 0);
        mTbMon.setChecked((mask & (1 << 1)) != 0);
        mTbTue.setChecked((mask & (1 << 2)) != 0);
        mTbWed.setChecked((mask & (1 << 3)) != 0);
        mTbThu.setChecked((mask & (1 << 4)) != 0);
        mTbFri.setChecked((mask & (1 << 5)) != 0);
        mTbSat.setChecked((mask & (1 << 6)) != 0);
    }
}
