package com.edom.alarm.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.edom.alarm.R;

/**
 * Dialog for application settings:
 * 1. Interface language selection (Follow System, Simplified Chinese, English)
 * 2. Advance notification toggle and window duration (15m, 30m, 45m, 60m)
 * 3. 4 customizable Quick Nap preset slots (duration + unit: minutes/hours)
 */
public class SettingsDialog extends Dialog {

    public interface OnSettingsSavedListener {
        void onSettingsSaved(boolean languageChanged);
    }

    public static final String PREFS_NAME = "smart_alarm_prefs";
    public static final String KEY_ADVANCE_ENABLED = "advance_notif_enabled";
    public static final String KEY_ADVANCE_MINUTES = "advance_notif_minutes";

    public static final String KEY_NAP_SLOT_1_VAL = "nap_slot_1_val";
    public static final String KEY_NAP_SLOT_1_UNIT = "nap_slot_1_unit";
    public static final String KEY_NAP_SLOT_2_VAL = "nap_slot_2_val";
    public static final String KEY_NAP_SLOT_2_UNIT = "nap_slot_2_unit";
    public static final String KEY_NAP_SLOT_3_VAL = "nap_slot_3_val";
    public static final String KEY_NAP_SLOT_3_UNIT = "nap_slot_3_unit";
    public static final String KEY_NAP_SLOT_4_VAL = "nap_slot_4_val";
    public static final String KEY_NAP_SLOT_4_UNIT = "nap_slot_4_unit";

    public static final String UNIT_MINUTES = "MINUTES";
    public static final String UNIT_HOURS = "HOURS";

    private final Activity mActivity;
    private final OnSettingsSavedListener mListener;
    private final HolidaySyncManager.OnHolidayRulesUpdatedListener mRulesUpdatedListener;

    public SettingsDialog(Activity activity, OnSettingsSavedListener listener) {
        this(activity, listener, null);
    }

    /**
     * Creates the settings dialog with an optional composition-root callback
     * for reconciling alarms after a successful manual holiday sync.
     */
    public SettingsDialog(
            Activity activity,
            OnSettingsSavedListener listener,
            HolidaySyncManager.OnHolidayRulesUpdatedListener rulesUpdatedListener) {
        super(activity);
        mActivity = activity;
        mListener = listener;
        mRulesUpdatedListener = rulesUpdatedListener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_settings);

        SharedPreferences prefs = mActivity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 1. Language
        RadioGroup rgLanguage = findViewById(R.id.rg_language);
        RadioButton rbLangSystem = findViewById(R.id.rb_lang_system);
        RadioButton rbLangZh = findViewById(R.id.rb_lang_zh);
        RadioButton rbLangEn = findViewById(R.id.rb_lang_en);

        String currentLang = LocalizationManager.getLanguage(mActivity);
        if (LocalizationManager.LANG_ZH.equals(currentLang)) {
            rbLangZh.setChecked(true);
        } else if (LocalizationManager.LANG_EN.equals(currentLang)) {
            rbLangEn.setChecked(true);
        } else {
            rbLangSystem.setChecked(true);
        }

        // 2. Advance Notification
        Switch swAdvance = findViewById(R.id.sw_advance_notification);
        boolean advEnabled = prefs.getBoolean(KEY_ADVANCE_ENABLED, true);
        swAdvance.setChecked(advEnabled);

        RadioGroup rgAdvanceTime = findViewById(R.id.rg_advance_time);
        int advMinutes = prefs.getInt(KEY_ADVANCE_MINUTES, 30);
        if (advMinutes == 15) {
            ((RadioButton) findViewById(R.id.rb_adv_15)).setChecked(true);
        } else if (advMinutes == 45) {
            ((RadioButton) findViewById(R.id.rb_adv_45)).setChecked(true);
        } else if (advMinutes == 60) {
            ((RadioButton) findViewById(R.id.rb_adv_60)).setChecked(true);
        } else {
            ((RadioButton) findViewById(R.id.rb_adv_30)).setChecked(true);
        }

        // 3. Quick Nap Preset Slots
        EditText etSlot1Val = findViewById(R.id.et_nap_slot_1_val);
        Spinner spSlot1Unit = findViewById(R.id.sp_nap_slot_1_unit);
        EditText etSlot2Val = findViewById(R.id.et_nap_slot_2_val);
        Spinner spSlot2Unit = findViewById(R.id.sp_nap_slot_2_unit);
        EditText etSlot3Val = findViewById(R.id.et_nap_slot_3_val);
        Spinner spSlot3Unit = findViewById(R.id.sp_nap_slot_3_unit);
        EditText etSlot4Val = findViewById(R.id.et_nap_slot_4_val);
        Spinner spSlot4Unit = findViewById(R.id.sp_nap_slot_4_unit);

        String[] unitOptions = new String[]{
                mActivity.getString(R.string.unit_minutes),
                mActivity.getString(R.string.unit_hours)
        };
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(mActivity, android.R.layout.simple_spinner_dropdown_item, unitOptions);

        spSlot1Unit.setAdapter(unitAdapter);
        spSlot2Unit.setAdapter(unitAdapter);
        spSlot3Unit.setAdapter(unitAdapter);
        spSlot4Unit.setAdapter(unitAdapter);

        etSlot1Val.setText(String.valueOf(prefs.getInt(KEY_NAP_SLOT_1_VAL, 15)));
        spSlot1Unit.setSelection(UNIT_HOURS.equals(prefs.getString(KEY_NAP_SLOT_1_UNIT, UNIT_MINUTES)) ? 1 : 0);

        etSlot2Val.setText(String.valueOf(prefs.getInt(KEY_NAP_SLOT_2_VAL, 30)));
        spSlot2Unit.setSelection(UNIT_HOURS.equals(prefs.getString(KEY_NAP_SLOT_2_UNIT, UNIT_MINUTES)) ? 1 : 0);

        etSlot3Val.setText(String.valueOf(prefs.getInt(KEY_NAP_SLOT_3_VAL, 45)));
        spSlot3Unit.setSelection(UNIT_HOURS.equals(prefs.getString(KEY_NAP_SLOT_3_UNIT, UNIT_MINUTES)) ? 1 : 0);

        etSlot4Val.setText(String.valueOf(prefs.getInt(KEY_NAP_SLOT_4_VAL, 60)));
        spSlot4Unit.setSelection(UNIT_HOURS.equals(prefs.getString(KEY_NAP_SLOT_4_UNIT, UNIT_MINUTES)) ? 1 : 0);

        // 4. Statutory Holiday Remote Sync
        EditText etHolidaySyncUrl = findViewById(R.id.et_holiday_sync_url);
        Button btnResetSyncUrl = findViewById(R.id.btn_reset_sync_url);
        Button btnSyncHolidaysNow = findViewById(R.id.btn_sync_holidays_now);

        String savedSyncUrl = prefs.getString(HolidaySyncManager.KEY_HOLIDAY_SYNC_URL, HolidaySyncManager.DEFAULT_SYNC_URL);
        if (HolidaySyncManager.LEGACY_SYNC_URL.equals(savedSyncUrl)) {
            savedSyncUrl = HolidaySyncManager.DEFAULT_SYNC_URL;
            prefs.edit().putString(HolidaySyncManager.KEY_HOLIDAY_SYNC_URL, HolidaySyncManager.DEFAULT_SYNC_URL).apply();
        }
        etHolidaySyncUrl.setText(savedSyncUrl);

        btnResetSyncUrl.setOnClickListener(v -> etHolidaySyncUrl.setText(HolidaySyncManager.DEFAULT_SYNC_URL));

        btnSyncHolidaysNow.setOnClickListener(v -> {
            String url = etHolidaySyncUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(mActivity, R.string.toast_sync_url_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            btnSyncHolidaysNow.setEnabled(false);
            btnSyncHolidaysNow.setText(mActivity.getString(R.string.sync_in_progress));

            HolidaySyncManager.syncManual(mActivity, url, new HolidaySyncManager.OnSyncCallback() {
                @Override
                public void onSuccess(int year, int holidayCount, int workdayCount) {
                    btnSyncHolidaysNow.setEnabled(true);
                    btnSyncHolidaysNow.setText(mActivity.getString(R.string.sync_now));
                    String successMsg = String.format(mActivity.getString(R.string.sync_success), holidayCount, workdayCount);
                    Toast.makeText(mActivity, successMsg, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String errorMessage) {
                    btnSyncHolidaysNow.setEnabled(true);
                    btnSyncHolidaysNow.setText(mActivity.getString(R.string.sync_now));

                    String errorBody = String.format(mActivity.getString(R.string.sync_failed_msg), errorMessage);
                    new AlertDialog.Builder(mActivity)
                            .setTitle(mActivity.getString(R.string.sync_failed_title))
                            .setMessage(errorBody)
                            .setPositiveButton(mActivity.getString(R.string.confirm), null)
                            .show();
                }
            }, mRulesUpdatedListener);
        });

        // 5. OEM Background Settings Guide (Vivo/iQOO OriginOS or Samsung One UI)
        View layoutOem = findViewById(R.id.layout_oem_settings);
        Button btnOemGuide = findViewById(R.id.btn_oem_whitelist_guide);
        TextView tvOemTitle = findViewById(R.id.tv_oem_settings_title);
        if (OemPermissionHelper.isVivoOrIqoo()) {
            layoutOem.setVisibility(View.VISIBLE);
            if (tvOemTitle != null) {
                tvOemTitle.setText(R.string.oem_settings_title);
            }
            btnOemGuide.setText(R.string.oem_settings_btn);
            btnOemGuide.setOnClickListener(v -> OemPermissionHelper.openOriginOsBackgroundSettings(mActivity));
        } else if (OemPermissionHelper.isSamsung()) {
            layoutOem.setVisibility(View.VISIBLE);
            if (tvOemTitle != null) {
                tvOemTitle.setText(R.string.oem_settings_samsung_title);
            }
            btnOemGuide.setText(R.string.oem_settings_samsung_btn);
            btnOemGuide.setOnClickListener(v -> OemPermissionHelper.openSamsungBackgroundSettings(mActivity));
        } else {
            layoutOem.setVisibility(View.GONE);
        }

        // Version display
        TextView tvSettingsVersion = findViewById(R.id.tv_settings_version);
        if (tvSettingsVersion != null) {
            tvSettingsVersion.setText(AppVersionUtils.getFormattedVersion(mActivity));
        }

        // Buttons
        findViewById(R.id.btn_cancel_settings).setOnClickListener(v -> dismiss());

        findViewById(R.id.btn_save_settings).setOnClickListener(v -> {
            String selectedLang = LocalizationManager.LANG_SYSTEM;
            if (rbLangZh.isChecked()) {
                selectedLang = LocalizationManager.LANG_ZH;
            } else if (rbLangEn.isChecked()) {
                selectedLang = LocalizationManager.LANG_EN;
            }

            int selectedAdvMinutes = 30;
            int advCheckId = rgAdvanceTime.getCheckedRadioButtonId();
            if (advCheckId == R.id.rb_adv_15) selectedAdvMinutes = 15;
            else if (advCheckId == R.id.rb_adv_45) selectedAdvMinutes = 45;
            else if (advCheckId == R.id.rb_adv_60) selectedAdvMinutes = 60;

            int s1 = parseInt(etSlot1Val.getText().toString(), 15);
            int s2 = parseInt(etSlot2Val.getText().toString(), 30);
            int s3 = parseInt(etSlot3Val.getText().toString(), 45);
            int s4 = parseInt(etSlot4Val.getText().toString(), 60);

            String u1 = spSlot1Unit.getSelectedItemPosition() == 1 ? UNIT_HOURS : UNIT_MINUTES;
            String u2 = spSlot2Unit.getSelectedItemPosition() == 1 ? UNIT_HOURS : UNIT_MINUTES;
            String u3 = spSlot3Unit.getSelectedItemPosition() == 1 ? UNIT_HOURS : UNIT_MINUTES;
            String u4 = spSlot4Unit.getSelectedItemPosition() == 1 ? UNIT_HOURS : UNIT_MINUTES;

            String syncUrlToSave = etHolidaySyncUrl.getText().toString().trim();
            if (syncUrlToSave.isEmpty()) {
                syncUrlToSave = HolidaySyncManager.DEFAULT_SYNC_URL;
            }

            boolean langChanged = !selectedLang.equals(currentLang);

            prefs.edit()
                    .putBoolean(KEY_ADVANCE_ENABLED, swAdvance.isChecked())
                    .putInt(KEY_ADVANCE_MINUTES, selectedAdvMinutes)
                    .putInt(KEY_NAP_SLOT_1_VAL, s1)
                    .putString(KEY_NAP_SLOT_1_UNIT, u1)
                    .putInt(KEY_NAP_SLOT_2_VAL, s2)
                    .putString(KEY_NAP_SLOT_2_UNIT, u2)
                    .putInt(KEY_NAP_SLOT_3_VAL, s3)
                    .putString(KEY_NAP_SLOT_3_UNIT, u3)
                    .putInt(KEY_NAP_SLOT_4_VAL, s4)
                    .putString(KEY_NAP_SLOT_4_UNIT, u4)
                    .putString(HolidaySyncManager.KEY_HOLIDAY_SYNC_URL, syncUrlToSave)
                    .apply();

            if (langChanged) {
                LocalizationManager.setLanguage(mActivity, selectedLang);
            }

            Toast.makeText(mActivity, R.string.toast_save_success, Toast.LENGTH_SHORT).show();
            dismiss();

            if (mListener != null) {
                mListener.onSettingsSaved(langChanged);
            }
        });
    }

    private int parseInt(String str, int defaultVal) {
        try {
            int val = Integer.parseInt(str.trim());
            return val > 0 ? val : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
