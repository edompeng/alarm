package com.edom.alarm.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;
import com.edom.alarm.R;

/**
 * One-tap Quick Nap dialog offering 15, 30, 45, and 60-minute countdown alarms.
 */
public class QuickNapDialog extends Dialog {

    public interface OnNapSelectedListener {
        void onNapSelected(int minutes);
    }

    private final OnNapSelectedListener mListener;

    public QuickNapDialog(Context context, OnNapSelectedListener listener) {
        super(context);
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_quick_nap);

        findViewById(R.id.btn_nap_choice_15).setOnClickListener(v -> selectNap(15));
        findViewById(R.id.btn_nap_choice_30).setOnClickListener(v -> selectNap(30));
        findViewById(R.id.btn_nap_choice_45).setOnClickListener(v -> selectNap(45));
        findViewById(R.id.btn_nap_choice_60).setOnClickListener(v -> selectNap(60));
    }

    private void selectNap(int minutes) {
        if (mListener != null) {
            mListener.onNapSelected(minutes);
        }
        Toast.makeText(getContext(), "Quick Nap set for " + minutes + " minutes", Toast.LENGTH_SHORT).show();
        dismiss();
    }
}
