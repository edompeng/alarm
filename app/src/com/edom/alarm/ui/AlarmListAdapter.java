package com.edom.alarm.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Switch;
import android.widget.TextView;
import com.edom.alarm.R;

import java.util.ArrayList;
import java.util.List;

public class AlarmListAdapter extends BaseAdapter {

    public static class AlarmItemModel {
        public long id;
        public int hour;
        public int minute;
        public boolean isEnabled;
        public int repeatMode; // 0=Once, 1=Custom, 2=Statutory Workdays
        public int daysBitmask;
        public String label;
        public boolean isQuickNap;
        public String ringtoneUri;
        public String ringtoneTitle;
        public boolean vibrateEnabled;
        public List<String> skippedDates;

        public AlarmItemModel(long id, int hour, int minute, boolean isEnabled,
                              int repeatMode, int daysBitmask, String label) {
            this(id, hour, minute, isEnabled, repeatMode, daysBitmask, label, false, null, "Default Alarm Sound", true, new ArrayList<>());
        }

        public AlarmItemModel(long id, int hour, int minute, boolean isEnabled,
                              int repeatMode, int daysBitmask, String label,
                              boolean isQuickNap, String ringtoneUri, String ringtoneTitle,
                              boolean vibrateEnabled, List<String> skippedDates) {
            this.id = id;
            this.hour = hour;
            this.minute = minute;
            this.isEnabled = isEnabled;
            this.repeatMode = repeatMode;
            this.daysBitmask = daysBitmask;
            this.label = label;
            this.isQuickNap = isQuickNap;
            this.ringtoneUri = ringtoneUri;
            this.ringtoneTitle = ringtoneTitle != null ? ringtoneTitle : "Default Alarm Sound";
            this.vibrateEnabled = vibrateEnabled;
            this.skippedDates = skippedDates != null ? new ArrayList<>(skippedDates) : new ArrayList<>();
        }
    }

    public interface OnAlarmToggleListener {
        void onToggle(AlarmItemModel item, boolean isChecked);
    }

    public interface OnAlarmClickListener {
        void onClick(AlarmItemModel item);
        void onLongClick(AlarmItemModel item);
    }

    public interface OnVacationBadgeClickListener {
        void onVacationBadgeClick(AlarmItemModel item);
    }

    private final Context mContext;
    private final List<AlarmItemModel> mItems = new ArrayList<>();
    private OnAlarmToggleListener mToggleListener;
    private OnAlarmClickListener mClickListener;
    private OnVacationBadgeClickListener mVacationBadgeClickListener;

    public AlarmListAdapter(Context context) {
        mContext = context;
    }

    public void setItems(List<AlarmItemModel> items) {
        mItems.clear();
        if (items != null) {
            mItems.addAll(items);
        }
        notifyDataSetChanged();
    }

    public void setOnAlarmToggleListener(OnAlarmToggleListener listener) {
        mToggleListener = listener;
    }

    public void setOnAlarmClickListener(OnAlarmClickListener listener) {
        mClickListener = listener;
    }

    public void setOnVacationBadgeClickListener(OnVacationBadgeClickListener listener) {
        mVacationBadgeClickListener = listener;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public AlarmItemModel getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mItems.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.item_alarm_card, parent, false);
            holder = new ViewHolder();
            holder.tvTime = convertView.findViewById(R.id.tv_alarm_time);
            holder.tvLabel = convertView.findViewById(R.id.tv_alarm_label);
            holder.tvRepeat = convertView.findViewById(R.id.tv_alarm_repeat);
            holder.tvVacation = convertView.findViewById(R.id.tv_alarm_vacation);
            holder.tvNapBadge = convertView.findViewById(R.id.tv_alarm_nap_badge);
            holder.swEnabled = convertView.findViewById(R.id.sw_alarm_enabled);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        AlarmItemModel item = getItem(position);
        holder.tvTime.setText(String.format("%02d:%02d", item.hour, item.minute));
        holder.tvLabel.setText(item.label != null && !item.label.isEmpty() ? item.label : "Alarm");

        // Format recurrence summary
        if (item.isQuickNap) {
            holder.tvRepeat.setText("Quick Nap");
        } else if (item.repeatMode == 2) {
            holder.tvRepeat.setText("Statutory Workdays (法定工作日)");
        } else if (item.repeatMode == 0) {
            holder.tvRepeat.setText("Ring Once");
        } else {
            holder.tvRepeat.setText(formatDaysBitmask(item.daysBitmask));
        }

        // Render Vacation and Nap Badges
        if (item.skippedDates != null && !item.skippedDates.isEmpty()) {
            holder.tvVacation.setVisibility(View.VISIBLE);
            holder.tvVacation.setText(String.format("Vacation (%d skipped)", item.skippedDates.size()));
            holder.tvVacation.setOnClickListener(v -> {
                if (mVacationBadgeClickListener != null) {
                    mVacationBadgeClickListener.onVacationBadgeClick(item);
                }
            });
        } else {
            holder.tvVacation.setVisibility(View.GONE);
            holder.tvVacation.setOnClickListener(null);
        }

        if (item.isQuickNap) {
            holder.tvNapBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvNapBadge.setVisibility(View.GONE);
        }

        // Avoid triggering listener during view recycling
        holder.swEnabled.setOnCheckedChangeListener(null);
        holder.swEnabled.setChecked(item.isEnabled);
        holder.swEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.isEnabled = isChecked;
            if (mToggleListener != null) {
                mToggleListener.onToggle(item, isChecked);
            }
        });

        convertView.setOnClickListener(v -> {
            if (mClickListener != null) {
                mClickListener.onClick(item);
            }
        });

        convertView.setOnLongClickListener(v -> {
            if (mClickListener != null) {
                mClickListener.onLongClick(item);
                return true;
            }
            return false;
        });

        return convertView;
    }

    private String formatDaysBitmask(int bitmask) {
        if (bitmask == 0x7F) return "Every Day";
        if (bitmask == 0x3E) return "Mon - Fri";
        if (bitmask == 0x41) return "Weekends";
        StringBuilder sb = new StringBuilder();
        String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int i = 0; i < 7; i++) {
            if ((bitmask & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(days[i]);
            }
        }
        return sb.length() > 0 ? sb.toString() : "Ring Once";
    }

    private static class ViewHolder {
        TextView tvTime;
        TextView tvLabel;
        TextView tvRepeat;
        TextView tvVacation;
        TextView tvNapBadge;
        Switch swEnabled;
    }
}
