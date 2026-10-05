package com.edom.alarm.ui;

import android.content.Context;
import com.edom.alarm.R;

/** Formats the remaining time until the next alarm ring for cards and toasts. */
public final class RingCountdownFormatter {

    private RingCountdownFormatter() {}

    /**
     * Formats a positive duration such as "1 day 2 hours 30 minutes". The minute
     * component is always present so the countdown can be read at minute precision.
     */
    public static String format(Context context, long deltaMs) {
        long safeDelta = Math.max(0L, deltaMs);
        // Round up so a pending ring never renders as "0 minutes".
        long totalMinutes = (safeDelta + 59999L) / 60000L;
        long days = totalMinutes / (24L * 60L);
        long hours = (totalMinutes % (24L * 60L)) / 60L;
        long minutes = totalMinutes % 60L;
        if (days > 0) {
            return context.getString(R.string.ring_in_days_hours_minutes, days, hours, minutes);
        }
        if (hours > 0) {
            return context.getString(R.string.ring_in_hours_minutes, hours, minutes);
        }
        return context.getString(R.string.ring_in_minutes, minutes);
    }
}
