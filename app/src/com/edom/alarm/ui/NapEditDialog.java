package com.edom.alarm.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.edom.alarm.R;

/**
 * Quick-edit dialog for customizing an individual Quick Nap slot (value + unit).
 * Triggered by long-pressing a Quick Nap button on the dashboard.
 */
public class NapEditDialog extends Dialog {

    public interface OnNapSlotUpdatedListener {
        void onNapSlotUpdated(int slotIndex, int newValue, String newUnit);
    }

    private final int mSlotIndex;
    private final int mCurrentVal;
    private final String mCurrentUnit;
    private final OnNapSlotUpdatedListener mListener;

    public NapEditDialog(Context context, int slotIndex, int currentVal, String currentUnit, OnNapSlotUpdatedListener listener) {
        super(context);
        mSlotIndex = slotIndex;
        mCurrentVal = currentVal;
        mCurrentUnit = currentUnit != null ? currentUnit : SettingsDialog.UNIT_MINUTES;
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_nap_edit);

        TextView tvTitle = findViewById(R.id.tv_nap_edit_title);
        tvTitle.setText(getContext().getString(R.string.nap_edit_title) + " - " + getContext().getString(R.string.nap_slot_1).replace("1", String.valueOf(mSlotIndex)));

        EditText etDuration = findViewById(R.id.et_nap_duration);
        etDuration.setText(String.valueOf(mCurrentVal));

        Spinner spUnit = findViewById(R.id.sp_nap_unit);
        String[] units = new String[]{
                getContext().getString(R.string.unit_minutes),
                getContext().getString(R.string.unit_hours)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, units);
        spUnit.setAdapter(adapter);
        spUnit.setSelection(SettingsDialog.UNIT_HOURS.equals(mCurrentUnit) ? 1 : 0);

        findViewById(R.id.btn_cancel_nap_edit).setOnClickListener(v -> dismiss());

        findViewById(R.id.btn_save_nap_edit).setOnClickListener(v -> {
            String valStr = etDuration.getText().toString().trim();
            int val;
            try {
                val = Integer.parseInt(valStr);
                if (val <= 0) val = 15;
            } catch (Exception e) {
                val = 15;
            }

            String unit = spUnit.getSelectedItemPosition() == 1 ? SettingsDialog.UNIT_HOURS : SettingsDialog.UNIT_MINUTES;

            if (mListener != null) {
                mListener.onNapSlotUpdated(mSlotIndex, val, unit);
            }
            dismiss();
        });
    }
}
