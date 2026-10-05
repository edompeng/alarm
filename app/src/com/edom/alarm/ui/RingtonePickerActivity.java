package com.edom.alarm.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import com.edom.alarm.R;

/**
 * Ringtone & Soundscape picker supporting local presets, dynamic weather,
 * and online streaming integration (QQ Music / NetEase Cloud Music).
 */
public class RingtonePickerActivity extends Activity {

    public static final String EXTRA_SELECTED_URI = "extra_selected_uri";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ringtone_picker);

        RadioGroup rg = findViewById(R.id.rg_ringtone_source);
        Button btnConfirm = findViewById(R.id.btn_confirm_ringtone);

        btnConfirm.setOnClickListener(v -> {
            String selectedUri = "content://settings/system/alarm_alert";
            int checkedId = rg.getCheckedRadioButtonId();
            if (checkedId == R.id.rb_dynamic_weather) {
                selectedUri = "weather://dynamic_soundscape";
            } else if (checkedId == R.id.rb_qq_music) {
                selectedUri = "qqmusic://daily_radar";
            } else if (checkedId == R.id.rb_netease_music) {
                selectedUri = "cloudmusic://daily_recommend";
            }

            Intent result = new Intent();
            result.putExtra(EXTRA_SELECTED_URI, selectedUri);
            setResult(RESULT_OK, result);
            finish();
        });
    }
}
