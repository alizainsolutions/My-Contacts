package com.alizainsolutions.mycontacts.Adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.provider.CallLog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.alizainsolutions.mycontacts.CreateContactActivity;
import com.alizainsolutions.mycontacts.Model.CallLogModel;
import com.alizainsolutions.mycontacts.R;
import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import de.hdodenhof.circleimageview.CircleImageView;

public class CallLogAdapter extends RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder> {

    private static final String TAG = "CallLogAdapter";

    private Context context;
    private List<CallLogModel> callLogList;
    private OnCallLogInteractionListener listener;

    public interface OnCallLogInteractionListener {
        void onCallLogClick(CallLogModel callLog);
        void onCallLogOptionsClick(CallLogModel callLog, View view, int position);
    }

    public CallLogAdapter(Context context, OnCallLogInteractionListener listener) {
        this.context = context;
        this.callLogList = new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public CallLogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (context == null) {
            Log.e(TAG, "Context is null in onCreateViewHolder!");
            return new CallLogViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_call_log, parent, false));
        }
        View view = LayoutInflater.from(context).inflate(R.layout.item_call_log, parent, false);
        return new CallLogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CallLogViewHolder holder, int position) {
        Log.d(TAG, "onBindViewHolder: Binding position " + position);

        if (callLogList == null || position < 0 || position >= callLogList.size()) {
            Log.w(TAG, "onBindViewHolder: Invalid position " + position + ". List size: " + (callLogList != null ? callLogList.size() : "null"));
            return;
        }

        CallLogModel callLog = callLogList.get(position);
        if (callLog == null) {
            Log.w(TAG, "onBindViewHolder: CallLogModel is null at position " + position);
            return;
        }
        Log.d(TAG, "onBindViewHolder: Data for position " + position + ": Name=" + callLog.getName() + ", Number=" + callLog.getNumber() + ", Type=" + callLog.getType() + ", Duration=" + callLog.getDuration());


        String nameOrNumber = callLog.getName();
        int nameNumberColor = ContextCompat.getColor(context, android.R.color.black);

        if (nameOrNumber == null || nameOrNumber.trim().isEmpty()) {
            nameOrNumber = callLog.getNumber();
            if (callLog.getType() == CallLog.Calls.MISSED_TYPE) {
                nameNumberColor = Color.RED;
            }
        }
        holder.tvNameNumber.setText(nameOrNumber != null && !nameOrNumber.trim().isEmpty() ? nameOrNumber : "Unknown Number");
        holder.tvNameNumber.setTextColor(nameNumberColor);


        String photoUriString = callLog.getPhotoUri();
        if (photoUriString != null && !photoUriString.trim().isEmpty()) {
            try {
                if (context != null) {
                    Glide.with(context)
                            .load(Uri.parse(photoUriString))
                            .placeholder(R.drawable.user)
                            .error(R.drawable.user)
                            .circleCrop()
                            .into(holder.ivPhoto);
                } else {
                    holder.ivPhoto.setImageResource(R.drawable.user);
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid photo URI: " + photoUriString + " - " + e.getMessage());
                holder.ivPhoto.setImageResource(R.drawable.user);
            }
        } else {
            holder.ivPhoto.setImageResource(R.drawable.user);
        }


        if (context != null) {
            setCallTypeInfo(holder, callLog.getType(), callLog.getDuration());
        } else {
            Log.e(TAG, "Context is null in setCallTypeInfo!");
        }


        holder.tvTime.setText(formatCallDate(callLog.getDate()));

        if (callLog.getType() == CallLog.Calls.MISSED_TYPE) {
            holder.tvDuration.setText("Missed");
            if (context != null) {
                holder.tvDuration.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark));
            }
        } else {
            if (callLog.getDuration() > 0) {
                holder.tvDuration.setText(formatDuration(callLog.getDuration()));
            } else {
                holder.tvDuration.setText("00:00");
            }
            if (context != null) {
                holder.tvDuration.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray));
            }
        }


        holder.itemView.setOnClickListener(v -> {
//            if (listener != null) {
//                listener.onCallLogClick(callLog);
//            }
            Intent intent = new Intent(context, CreateContactActivity.class);
            intent.putExtra("recentName", callLog.getName());
            intent.putExtra("recentNumber", callLog.getNumber());
            context.startActivity(intent);
        });

        holder.ivOptions.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCallLogOptionsClick(callLog, v, position);
            }
        });
    }

    private void setCallTypeInfo(CallLogViewHolder holder, int callType, long duration) {
        int iconResId;
        String typeLabel;
        int tintColor;

        if (context == null) {
            Log.e(TAG, "Context is null in setCallTypeInfo!");
            return;
        }

        switch (callType) {
            case CallLog.Calls.INCOMING_TYPE:
                iconResId = R.drawable.incoming_ic;
                typeLabel = "Incoming";
                tintColor = ContextCompat.getColor(context, android.R.color.holo_green_dark);
                break;
            case CallLog.Calls.OUTGOING_TYPE:
                iconResId = R.drawable.outgoing_c;
                typeLabel = "Outgoing";
                tintColor = ContextCompat.getColor(context, android.R.color.holo_blue_dark);
                break;
            case CallLog.Calls.MISSED_TYPE:
                iconResId = R.drawable.missed_call_ic;
                typeLabel = "Missed";
                tintColor = ContextCompat.getColor(context, android.R.color.holo_red_dark);
                break;
            case CallLog.Calls.VOICEMAIL_TYPE:
                iconResId = R.drawable.incoming_ic;
                typeLabel = "Voicemail";
                tintColor = ContextCompat.getColor(context, android.R.color.darker_gray);
                break;
            case CallLog.Calls.REJECTED_TYPE:
                iconResId = R.drawable.missed_call_ic;
                typeLabel = "Rejected";
                tintColor = ContextCompat.getColor(context, android.R.color.holo_red_light);
                break;
            default:
                iconResId = R.drawable.incoming_ic;
                typeLabel = "Unknown";
                tintColor = ContextCompat.getColor(context, android.R.color.darker_gray);
                break;
        }

        holder.ivTypeIcon.setImageResource(iconResId);
        holder.ivTypeIcon.setColorFilter(tintColor);
        holder.tvTypeLabel.setText(typeLabel);
    }

    private String formatCallDate(long timestamp) {
        if (context == null) {
            Log.e(TAG, "Context is null in formatCallDate!");
            return new SimpleDateFormat("MMM d,yyyy, hh:mm a", Locale.getDefault()).format(new Date(timestamp));
        }

        Date callDate = new Date(timestamp);
        if (android.text.format.DateUtils.isToday(timestamp)) {
            return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(callDate);
        } else if (android.text.format.DateUtils.isToday(timestamp + android.text.format.DateUtils.DAY_IN_MILLIS)) {
            return "Yesterday, " + new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(callDate);
        } else {
            return new SimpleDateFormat("MMM d,yyyy, hh:mm a", Locale.getDefault()).format(callDate);
        }
    }

    private String formatDuration(long durationSeconds) {
        if (durationSeconds <= 0) return "00:00";

        long minutes = TimeUnit.SECONDS.toMinutes(durationSeconds);
        long seconds = durationSeconds - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    public int getItemCount() {
        int size = callLogList != null ? callLogList.size() : 0;
        Log.d(TAG, "getItemCount: returning " + size);
        return size;
    }


    public void addCallLogEntryAtTop(CallLogModel newEntry) {
        if (callLogList != null) {
            callLogList.add(0, newEntry);
            notifyItemInserted(0);
            Log.d(TAG, "addCallLogEntryAtTop: Added item at position 0. New count: " + callLogList.size());
        } else {
            Log.e(TAG, "CallLogList is null in addCallLogEntryAtTop. Cannot add item.");
        }
    }


    public void addCallLogEntryAtBottom(CallLogModel newEntry) {
        if (callLogList != null) {
            int position = callLogList.size(); // Add at the current end
            callLogList.add(newEntry);
            notifyItemInserted(position); // Notify adapter that an item was inserted at the end
            Log.d(TAG, "addCallLogEntryAtBottom: Added item at position " + position + ". New count: " + callLogList.size());
        } else {
            Log.e(TAG, "CallLogList is null in addCallLogEntryAtBottom. Cannot add item.");
        }
    }

    public void updateCallLogList(List<CallLogModel> newCallLogList) {
        if (this.callLogList == null) {
            this.callLogList = new ArrayList<>();
        }
//        this.callLogList.clear();
        if (newCallLogList != null) {
            this.callLogList.addAll(newCallLogList);
        }
        notifyDataSetChanged();
        Log.d(TAG, "updateCallLogList: Adapter data set changed with " + (newCallLogList != null ? newCallLogList.size() : 0) + " items. Adapter's internal list size: " + this.callLogList.size());
    }


    public CallLogModel getCallLogAtPosition(int position) {
        if (callLogList != null && position >= 0 && position < callLogList.size()) {
            return callLogList.get(position);
        }
        return null;
    }

    public void removeCallLogEntry(String callLogId) {
        for (int i = 0; i < callLogList.size(); i++) {
            if (callLogList.get(i).getCallLogId() != null && callLogList.get(i).getCallLogId().equals(callLogId)) {
                callLogList.remove(i);
                notifyItemRemoved(i);
                Log.d(TAG, "Removed call log entry with ID: " + callLogId + ". New list size: " + callLogList.size());
                return;
            }
        }
    }


    public static class CallLogViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivPhoto;
        TextView tvNameNumber, tvTypeLabel, tvTime, tvDuration;
        ImageView ivTypeIcon, ivOptions;

        public CallLogViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPhoto = itemView.findViewById(R.id.iv_call_log_photo);
            tvNameNumber = itemView.findViewById(R.id.tv_call_log_name_number);
            ivTypeIcon = itemView.findViewById(R.id.iv_call_log_type_icon);
            tvTypeLabel = itemView.findViewById(R.id.tv_call_log_type_label);
            tvTime = itemView.findViewById(R.id.tv_call_log_time);
            tvDuration = itemView.findViewById(R.id.tv_call_log_duration);
            ivOptions = itemView.findViewById(R.id.iv_call_log_options);
        }
    }

    public void clearAll() {
        if (callLogList != null) {
            callLogList.clear();
            notifyDataSetChanged();
            Log.d(TAG, "clearAll: Adapter data cleared.");
        }
    }
}