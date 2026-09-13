package com.edom.alarm.ui;

import android.app.AlertDialog;
import android.content.Context;
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
        msg.append("The alarm will be skipped on the following dates:\n\n");
        for (String date : datesToSkip) {
            msg.append("• ").append(date).append("\n");
        }
        msg.append("\nAll other scheduled dates will ring normally. Confirm?");

        new AlertDialog.Builder(context)
            .setTitle("Confirm Vacation Skip (确认跳过日期)")
            .setMessage(msg.toString())
            .setPositiveButton("Confirm (确认)", (dialog, which) -> {
                if (listener != null) {
                    listener.onConfirmed();
                }
            })
            .setNegativeButton("Cancel (取消)", null)
            .show();
    }
}
