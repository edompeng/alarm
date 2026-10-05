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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Vacation multi-day dismissal dialog.
 *
 * <p>Every date on which the alarm would still ring is highlighted; tapping a
 * highlighted date removes the highlight, which skips that occurrence. Tapping a
 * skipped date restores it. When no ringing date remains the alarm is reported as
 * exhausted so the dashboard can switch it off.
 */
public class VacationCalendarDialog extends Dialog {

    public interface OnVacationDatesSavedListener {
        /**
         * @param hasFutureRing false when every remaining occurrence is skipped and the
         *                      alarm can therefore never ring again.
         */
        void onVacationDatesSaved(long alarmId, List<String> skippedDates, boolean hasFutureRing);
    }

    private static final int MAX_SEARCH_DAYS = 370;
    private static final int COLOR_RING_TODAY = Color.parseColor("#FFB74D");
    private static final int COLOR_RING = Color.parseColor("#FFE0B2");
    private static final int COLOR_RING_TEXT = Color.parseColor("#E65100");
    private static final int COLOR_TODAY = Color.parseColor("#E0E0E0");
    private static final int COLOR_PLAIN_TEXT = Color.parseColor("#212121");
    private static final int COLOR_SKIPPED_TEXT = Color.parseColor("#9E9E9E");

    private final long mAlarmId;
    private final AlarmListAdapter.AlarmItemModel mAlarm;
    private final Set<String> mSkippedDates = new TreeSet<>();
    private final Set<String> mHolidayDates;
    private final Set<String> mWorkdayDates;
    private final OnVacationDatesSavedListener mListener;

    private Calendar mCalendar;
    private TextView mTvMonthTitle;
    private GridView mGvCalendarDays;
    private CalendarGridAdapter mAdapter;

    public VacationCalendarDialog(Context context, AlarmListAdapter.AlarmItemModel alarm,
                                  OnVacationDatesSavedListener listener) {
        super(context);
        mAlarm = alarm;
        mAlarmId = alarm != null ? alarm.id : -1L;
        if (alarm != null && alarm.skippedDates != null) {
            mSkippedDates.addAll(alarm.skippedDates);
        }
        mListener = listener;
        mHolidayDates = HolidaySyncManager.getCachedHolidays(context);
        mWorkdayDates = HolidaySyncManager.getCachedWorkdays(context);
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
                mSkippedDates.clear();
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
            });
        }

        findViewById(R.id.btn_cancel_calendar).setOnClickListener(v -> dismiss());
        findViewById(R.id.btn_save_calendar).setOnClickListener(v -> saveSkips());
    }

    private void saveSkips() {
        List<String> skippedDates = getSkippedDates();
        if (skippedDates.isEmpty()) {
            notifySaved(true);
            dismiss();
            return;
        }
        SkipConfirmationDialog.show(getContext(), skippedDates,
                () -> {
                    notifySaved(hasFutureRingingDate());
                    dismiss();
                });
    }

    private void notifySaved(boolean hasFutureRing) {
        if (mListener != null) {
            mListener.onVacationDatesSaved(mAlarmId, getSkippedDates(), hasFutureRing);
        }
    }

    public List<String> getSkippedDates() {
        return new ArrayList<>(mSkippedDates);
    }

    private void updateCalendarDisplay() {
        Locale targetLocale = LocalizationManager.getTargetLocale(getContext());
        SimpleDateFormat sdf;
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

    private void toggleDate(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        if (!isEditableDate(date)) {
            return;
        }
        if (!mSkippedDates.remove(dateStr)) {
            mSkippedDates.add(dateStr);
        }
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private static boolean isFutureDate(LocalDate date) {
        return !date.isBefore(LocalDate.now());
    }

    /**
     * A date can be toggled only while its occurrence is still ahead: today counts
     * only until the alarm time has passed.
     */
    private boolean isEditableDate(LocalDate date) {
        if (!isFutureDate(date) || !wouldRingOn(date)) {
            return false;
        }
        if (date.equals(LocalDate.now()) && mAlarm != null) {
            return LocalTime.now().isBefore(LocalTime.of(mAlarm.hour, mAlarm.minute));
        }
        return true;
    }

    /** Mirrors the scheduling rule used by DefaultNextOccurrenceCalculator. */
    private boolean wouldRingOn(LocalDate date) {
        if (mAlarm == null) {
            return false;
        }
        if (mAlarm.repeatMode == 2) {
            return isStatutoryWorkday(date);
        }
        if (mAlarm.repeatMode == 1) {
            int sundayBasedIndex = date.getDayOfWeek().getValue() % 7;
            return (mAlarm.daysBitmask & (1 << sundayBasedIndex)) != 0;
        }
        return date.equals(singleOccurrenceDate());
    }

    private boolean isStatutoryWorkday(LocalDate date) {
        String dateStr = date.toString();
        if (mWorkdayDates.contains(dateStr)) {
            return true;
        }
        if (mHolidayDates.contains(dateStr)) {
            return false;
        }
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    /** The single occurrence of a "Ring Once" alarm or Quick Nap. */
    private LocalDate singleOccurrenceDate() {
        if (mAlarm.nextTriggerAtMs > System.currentTimeMillis()) {
            return Instant.ofEpochMilli(mAlarm.nextTriggerAtMs)
                    .atZone(ZoneId.systemDefault()).toLocalDate();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayAt = LocalDateTime.of(
                now.toLocalDate(), LocalTime.of(mAlarm.hour, mAlarm.minute));
        return todayAt.isAfter(now) ? now.toLocalDate() : now.toLocalDate().plusDays(1);
    }

    private boolean hasFutureRingingDate() {
        LocalDate today = LocalDate.now();
        for (int offset = 0; offset <= MAX_SEARCH_DAYS; offset++) {
            LocalDate date = today.plusDays(offset);
            if (!isEditableDate(date)) {
                continue;
            }
            if (!mSkippedDates.contains(date.toString())) {
                return true;
            }
        }
        return false;
    }

    private class CalendarGridAdapter extends BaseAdapter {

        @Override
        public int getCount() {
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
                tv.setTextSize(15);
                tv.setPadding(4, 10, 4, 10);
            }

            Calendar cal = (Calendar) mCalendar.clone();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int offset = (firstDayOfWeek - Calendar.SUNDAY + 7) % 7;

            if (position < offset) {
                tv.setText("");
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setTextColor(COLOR_PLAIN_TEXT);
                tv.setOnClickListener(null);
                return tv;
            }

            int day = position - offset + 1;
            LocalDate date = LocalDate.of(
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, day);
            String dateStr = date.toString();
            boolean isToday = date.equals(LocalDate.now());
            boolean editable = isEditableDate(date);
            boolean skipped = mSkippedDates.contains(dateStr);

            tv.setText(String.valueOf(day));
            if (editable && !skipped) {
                // Highlights are the dates that will actually ring.
                tv.setBackgroundColor(isToday ? COLOR_RING_TODAY : COLOR_RING);
                tv.setTextColor(COLOR_RING_TEXT);
            } else {
                tv.setBackgroundColor(isToday ? COLOR_TODAY : Color.TRANSPARENT);
                tv.setTextColor(editable ? COLOR_SKIPPED_TEXT : COLOR_PLAIN_TEXT);
            }

            if (editable) {
                tv.setOnClickListener(v -> toggleDate(dateStr));
            } else {
                tv.setOnClickListener(null);
            }
            return tv;
        }
    }
}
