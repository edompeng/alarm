package com.edom.alarm.ui;

import android.app.AlertDialog;
import android.content.Context;
import com.edom.alarm.R;
import java.util.List;

/**
 * Secondary confirmation dialog presenting the exact list of skipped dates
 * before persisting changes to the database.
 */
public class SkipConfirmationDialog {

    public interface OnConfirmListener {
        void onConfirmed();
    }

    public static void show(Context context, List<String> datesToSkip, OnConfirmListener listener) {
        StringBuilder msg = new StringBuilder();
        msg.append(context.getString(R.string.confirm_vacation_skip_msg_header));
        for (String date : datesToSkip) {
            msg.append("• ").append(date).append("\n");
        }
        msg.append(context.getString(R.string.confirm_vacation_skip_msg_footer));

        new AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.confirm_vacation_skip_title))
            .setMessage(msg.toString())
            .setPositiveButton(context.getString(R.string.confirm), (dialog, which) -> {
                if (listener != null) {
                    listener.onConfirmed();
                }
            })
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show();
    }
}
