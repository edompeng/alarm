package com.edom.alarm.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import com.edom.alarm.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Vacation multi-day calendar dismissal dialog.
 * Allows navigating months and toggling highlighted dates to schedule vacation skips.
 */
public class VacationCalendarDialog extends Dialog {

    private final long mAlarmId;
    private final List<String> mSkippedDates = new ArrayList<>();

    public VacationCalendarDialog(Context context, long alarmId) {
        super(context);
        mAlarmId = alarmId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_vacation_calendar);

        TextView tvTitle = findViewById(R.id.tv_month_title);
        tvTitle.setText("October 2026");

        findViewById(R.id.btn_cancel_calendar).setOnClickListener(v -> dismiss());
        findViewById(R.id.btn_save_calendar).setOnClickListener(v -> {
            // Secondary confirmation dialog
            SkipConfirmationDialog.show(getContext(), mSkippedDates, () -> {
                dismiss();
            });
        });
    }

    public void addSkippedDate(String dateStr) {
        if (!mSkippedDates.contains(dateStr)) {
            mSkippedDates.add(dateStr);
        }
    }
}
