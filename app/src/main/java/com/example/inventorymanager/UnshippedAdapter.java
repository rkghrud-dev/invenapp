package com.example.inventorymanager;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class UnshippedAdapter extends BaseAdapter {
    public interface FeedbackListener {
        void onApplyDefaultFeedback(UnshippedItem item);

        void onSaveCustomFeedback(UnshippedItem item, String feedback);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final FeedbackListener listener;
    private final List<UnshippedItem> items = new ArrayList<>();
    private boolean controlsEnabled = true;

    public UnshippedAdapter(Context context, FeedbackListener listener) {
        this.context = context;
        this.listener = listener;
        this.inflater = LayoutInflater.from(context);
    }

    public void setItems(List<UnshippedItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void setControlsEnabled(boolean controlsEnabled) {
        this.controlsEnabled = controlsEnabled;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public UnshippedItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getSheetRowNumber();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_unshipped_result, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        UnshippedItem item = getItem(position);
        holder.orderCode.setText(valueOrFallback(item.getOrderCode(), context.getString(R.string.unshipped_order_code_fallback)));
        holder.reason.setText(context.getString(R.string.unshipped_reason_line, valueOrFallback(item.getReason(), context.getString(R.string.unshipped_none))));
        holder.supplyMemo.setText(context.getString(R.string.unshipped_supply_memo_line, valueOrFallback(item.getSupplyMemo(), context.getString(R.string.unshipped_none))));
        holder.result.setText(context.getString(R.string.unshipped_result_line, valueOrFallback(item.getResult(), context.getString(R.string.unshipped_none))));
        holder.currentFeedback.setText(context.getString(R.string.unshipped_current_feedback_line, valueOrFallback(item.getSellerFeedback(), context.getString(R.string.unshipped_none))));

        holder.recipientNameToggle.setOnCheckedChangeListener(null);
        holder.recipientPhoneToggle.setOnCheckedChangeListener(null);
        holder.recipientNameToggle.setChecked(item.isRecipientNameVisible());
        holder.recipientPhoneToggle.setChecked(item.isRecipientPhoneVisible());
        holder.recipientName.setText(context.getString(R.string.unshipped_recipient_name_line, valueOrFallback(item.getRecipientName(), context.getString(R.string.unshipped_none))));
        holder.recipientPhone.setText(context.getString(R.string.unshipped_phone_line, valueOrFallback(item.getRecipientPhone(), context.getString(R.string.unshipped_none))));
        holder.recipientName.setVisibility(item.isRecipientNameVisible() ? View.VISIBLE : View.GONE);
        boolean phoneVisible = item.isRecipientPhoneVisible();
        holder.recipientPhone.setVisibility(phoneVisible ? View.VISIBLE : View.GONE);
        boolean hasPhone = !TextUtils.isEmpty(item.getRecipientPhone());
        holder.phoneActions.setVisibility(phoneVisible && hasPhone ? View.VISIBLE : View.GONE);

        holder.customInputContainer.setVisibility(item.isCustomInputVisible() ? View.VISIBLE : View.GONE);
        holder.customInput.setText(item.getCustomInputText());

        holder.defaultButton.setEnabled(controlsEnabled);
        holder.directInputButton.setEnabled(controlsEnabled);
        holder.saveButton.setEnabled(controlsEnabled);
        holder.customInput.setEnabled(controlsEnabled);
        holder.recipientNameToggle.setEnabled(controlsEnabled);
        holder.recipientPhoneToggle.setEnabled(controlsEnabled);
        holder.callButton.setEnabled(controlsEnabled && hasPhone);
        holder.smsButton.setEnabled(controlsEnabled && hasPhone);

        holder.recipientNameToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setRecipientNameVisible(isChecked);
            notifyDataSetChanged();
        });
        holder.recipientPhoneToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setRecipientPhoneVisible(isChecked);
            notifyDataSetChanged();
        });
        holder.callButton.setOnClickListener(v -> openDialer(item.getRecipientPhone()));
        holder.smsButton.setOnClickListener(v -> openSms(item.getRecipientPhone()));
        holder.defaultButton.setOnClickListener(v -> listener.onApplyDefaultFeedback(item));
        holder.directInputButton.setOnClickListener(v -> {
            if (item.isCustomInputVisible()) {
                item.setCustomInputVisible(false);
            } else {
                item.setCustomInputVisible(true);
                item.prepareCustomInput(context.getString(R.string.unshipped_default_feedback_value));
            }
            notifyDataSetChanged();
        });
        holder.saveButton.setOnClickListener(v -> {
            String feedback = holder.customInput.getText().toString().trim();
            item.setCustomInputText(feedback);
            listener.onSaveCustomFeedback(item, feedback);
        });

        return convertView;
    }

    private void openDialer(String phone) {
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(context, R.string.unshipped_phone_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone.trim())));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startIntentSafely(intent);
    }

    private void openSms(String phone) {
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(context, R.string.unshipped_phone_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone.trim())));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startIntentSafely(intent);
    }

    private void startIntentSafely(Intent intent) {
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(context, R.string.unshipped_phone_action_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private String valueOrFallback(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private static final class ViewHolder {
        private final TextView orderCode;
        private final TextView reason;
        private final TextView supplyMemo;
        private final TextView result;
        private final TextView currentFeedback;
        private final CheckBox recipientNameToggle;
        private final TextView recipientName;
        private final CheckBox recipientPhoneToggle;
        private final TextView recipientPhone;
        private final LinearLayout phoneActions;
        private final Button callButton;
        private final Button smsButton;
        private final Button defaultButton;
        private final Button directInputButton;
        private final LinearLayout customInputContainer;
        private final EditText customInput;
        private final Button saveButton;

        private ViewHolder(View root) {
            orderCode = root.findViewById(R.id.item_order_code);
            reason = root.findViewById(R.id.item_reason);
            supplyMemo = root.findViewById(R.id.item_supply_memo);
            result = root.findViewById(R.id.item_result);
            currentFeedback = root.findViewById(R.id.item_current_feedback);
            recipientNameToggle = root.findViewById(R.id.item_recipient_name_toggle);
            recipientName = root.findViewById(R.id.item_recipient_name);
            recipientPhoneToggle = root.findViewById(R.id.item_recipient_phone_toggle);
            recipientPhone = root.findViewById(R.id.item_recipient_phone);
            phoneActions = root.findViewById(R.id.item_phone_actions);
            callButton = root.findViewById(R.id.item_call_button);
            smsButton = root.findViewById(R.id.item_sms_button);
            defaultButton = root.findViewById(R.id.item_default_feedback_button);
            directInputButton = root.findViewById(R.id.item_direct_input_button);
            customInputContainer = root.findViewById(R.id.item_custom_input_container);
            customInput = root.findViewById(R.id.item_custom_feedback_input);
            saveButton = root.findViewById(R.id.item_custom_save_button);
        }
    }
}
