package com.example.inventorymanager;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class SearchResultAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final List<InventoryItem> items = new ArrayList<>();

    public SearchResultAdapter(Context context) {
        this.context = context;
        inflater = LayoutInflater.from(context);
    }

    public void setItems(List<InventoryItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public InventoryItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_inventory_result, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        InventoryItem item = getItem(position);
        boolean actualStockAlert = isZeroOrBelow(item.getActualStockQuantity());

        holder.productName.setText(primaryTitle(item));
        holder.actualStockBadge.setText(buildBadgeText(
                context.getString(R.string.result_actual_stock_label),
                item.getActualStockQuantity(),
                context.getString(R.string.result_unavailable_value)
        ));
        holder.actualStockBadge.setBackgroundResource(actualStockAlert
                ? R.drawable.bg_stock_badge_alert
                : R.drawable.bg_stock_badge);
        holder.matchReason.setText(valueOrFallback(item.getMatchReason(), "일치 항목"));
        holder.currentStockQuantity.setText(buildLabeledText(
                context.getString(R.string.result_stock_label),
                item.getStockQuantity(),
                context.getString(R.string.result_unavailable_value)
        ));
        holder.todaySoldQuantity.setText(buildLabeledText(
                context.getString(R.string.result_today_sold_label),
                item.getTodaySoldQuantity(),
                "0"
        ));
        holder.productCode.setText(buildLabeledText("상품코드", item.getProductCode()));
        holder.orderCode.setText(buildLabeledText("주문코드", item.getOrderCode()));
        return convertView;
    }

    private String primaryTitle(InventoryItem item) {
        String productName = item.getProductName();
        if (!TextUtils.isEmpty(productName)) {
            return productName;
        }
        return valueOrFallback(item.getOrderCode(), "상품명 없음");
    }

    private String buildLabeledText(String label, String value) {
        return label + "  " + valueOrFallback(value, "없음");
    }

    private String buildLabeledText(String label, String value, String fallback) {
        return label + "  " + valueOrFallback(value, fallback);
    }

    private String buildBadgeText(String label, String value, String fallback) {
        return label + " " + valueOrFallback(value, fallback);
    }

    private String valueOrFallback(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private boolean isZeroOrBelow(String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }

        try {
            return new BigDecimal(value.trim()).compareTo(BigDecimal.ZERO) <= 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static final class ViewHolder {
        private final TextView productName;
        private final TextView actualStockBadge;
        private final TextView matchReason;
        private final TextView currentStockQuantity;
        private final TextView todaySoldQuantity;
        private final TextView productCode;
        private final TextView orderCode;

        private ViewHolder(View root) {
            productName = root.findViewById(R.id.item_product_name);
            actualStockBadge = root.findViewById(R.id.item_actual_stock_badge);
            matchReason = root.findViewById(R.id.item_match_reason);
            currentStockQuantity = root.findViewById(R.id.item_current_stock_quantity);
            todaySoldQuantity = root.findViewById(R.id.item_today_sold_quantity);
            productCode = root.findViewById(R.id.item_product_code);
            orderCode = root.findViewById(R.id.item_order_code);
        }
    }
}
