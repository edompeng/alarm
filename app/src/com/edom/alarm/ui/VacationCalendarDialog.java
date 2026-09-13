package com.edom.alarm.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;
import com.edom.alarm.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Vacation multi-day calendar dismissal dialog.
 * Layout: Sunday is first column (Sun-Sat), 7-column day-of-week header,
 * scaled navigation controls, and highlighted skipped dates.
 */
public class VacationCalendarDialog extends Dialog {

    public interface OnVacationDatesSavedListener {
        void onVacationDatesSaved(long alarmId, List<String> skippedDates);
    }

    private final long mAlarmId;
    private final List<String> mSkippedDates = new ArrayList<>();
    private final OnVacationDatesSavedListener mListener;

    private Calendar mCalendar;
    private TextView mTvMonthTitle;
    private GridView mGvCalendarDays;
    private CalendarGridAdapter mAdapter;

    public VacationCalendarDialog(Context context, long alarmId, List<String> existingSkippedDates,
                                  OnVacationDatesSavedListener listener) {
        super(context);
        mAlarmId = alarmId;
        if (existingSkippedDates != null) {
            mSkippedDates.addAll(existingSkippedDates);
        }
        mListener = listener;
    }

    public VacationCalendarDialog(Context context, long alarmId) {
        this(context, alarmId, null, null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_vacation_calendar);

        mCalendar = Calendar.getInstance();

        mTvMonthTitle = findViewById(R.id.tv_month_title);
        mGvCalendarDays = findViewById(R.id.gv_calendar_days);

        Button btnPrevMonth = findViewById(R.id.btn_prev_month);
        Button btnNextMonth = findViewById(R.id.btn_next_month);

        btnPrevMonth.setOnClickListener(v -> {
            mCalendar.add(Calendar.MONTH, -1);
            updateCalendarDisplay();
        });

        btnNextMonth.setOnClickListener(v -> {
            mCalendar.add(Calendar.MONTH, 1);
            updateCalendarDisplay();
        });

        mAdapter = new CalendarGridAdapter();
        mGvCalendarDays.setAdapter(mAdapter);

        updateCalendarDisplay();

        Button btnClear = findViewById(R.id.btn_clear_calendar);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                new android.app.AlertDialog.Builder(getContext())
                        .setTitle(R.string.clear_skips)
                        .setMessage(R.string.clear_skips_confirm)
                        .setPositiveButton(R.string.confirm, (d, w) -> {
                            mSkippedDates.clear();
                            if (mListener != null) {
                                mListener.onVacationDatesSaved(mAlarmId, mSkippedDates);
                            }
                            dismiss();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
        }

        findViewById(R.id.btn_cancel_calendar).setOnClickListener(v -> dismiss());
        findViewById(R.id.btn_save_calendar).setOnClickListener(v -> {
            if (mSkippedDates.isEmpty()) {
                if (mListener != null) {
                    mListener.onVacationDatesSaved(mAlarmId, mSkippedDates);
                }
                dismiss();
            } else {
                SkipConfirmationDialog.show(getContext(), mSkippedDates, () -> {
                    if (mListener != null) {
                        mListener.onVacationDatesSaved(mAlarmId, mSkippedDates);
                    }
                    dismiss();
                });
            }
        });
    }

    private void updateCalendarDisplay() {
        Locale targetLocale = LocalizationManager.getTargetLocale(getContext());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", targetLocale);
        if ("zh".equals(targetLocale.getLanguage())) {
            sdf = new SimpleDateFormat("yyyy年M月", targetLocale);
        } else {
            sdf = new SimpleDateFormat("MMMM yyyy", targetLocale);
        }
        mTvMonthTitle.setText(sdf.format(mCalendar.getTime()));
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    public void addSkippedDate(String dateStr) {
        if (!mSkippedDates.contains(dateStr)) {
            mSkippedDates.add(dateStr);
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    public void removeSkippedDate(String dateStr) {
        if (mSkippedDates.remove(dateStr)) {
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    public List<String> getSkippedDates() {
        return new ArrayList<>(mSkippedDates);
    }

    private class CalendarGridAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            // Days in month + first day of week offset (Sunday is first day = 0 offset)
            Calendar cal = (Calendar) mCalendar.clone();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int offset = (firstDayOfWeek - Calendar.SUNDAY + 7) % 7;
            int maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            return offset + maxDays;
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(getContext());
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(14);
                tv.setPadding(4, 6, 4, 6);
            }

            Calendar cal = (Calendar) mCalendar.clone();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int offset = (firstDayOfWeek - Calendar.SUNDAY + 7) % 7;

            if (position < offset) {
                tv.setText("");
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setOnClickListener(null);
            } else {
                int day = position - offset + 1;
                String dateStr = String.format(Locale.US, "%04d-%02d-%02d",
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, day);

                boolean isSkipped = mSkippedDates.contains(dateStr);
                tv.setText(String.valueOf(day));
                if (isSkipped) {
                    tv.setBackgroundColor(Color.parseColor("#FFE0B2")); // Light orange
                    tv.setTextColor(Color.parseColor("#E65100")); // Dark orange
                } else {
                    tv.setBackgroundColor(Color.TRANSPARENT);
                    tv.setTextColor(Color.parseColor("#212121"));
                }

                tv.setOnClickListener(v -> {
                    if (mSkippedDates.contains(dateStr)) {
                        mSkippedDates.remove(dateStr);
                    } else {
                        mSkippedDates.add(dateStr);
                    }
                    notifyDataSetChanged();
                });
            }

            return tv;
        }
    }
}
