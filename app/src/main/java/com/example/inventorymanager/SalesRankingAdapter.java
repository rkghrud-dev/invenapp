package com.example.inventorymanager;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class SalesRankingAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final List<SalesRankingItem> items = new ArrayList<>();

    public SalesRankingAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    public void setItems(List<SalesRankingItem> newItems) {
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
    public SalesRankingItem getItem(int position) {
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
            convertView = inflater.inflate(R.layout.item_sales_ranking, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        SalesRankingItem item = getItem(position);
        holder.rankBadge.setText(context.getString(R.string.ranking_rank_badge, item.getRank()));
        holder.displayName.setText(valueOrFallback(item.getDisplayName(), item.getProductCode()));
        holder.salesCountBadge.setText(context.getString(R.string.ranking_sales_count_badge, item.getSalesCount()));
        holder.salesQtySum.setText(context.getString(R.string.ranking_sales_qty_sum_line, valueOrFallback(item.getSalesQuantitySum(), "0")));
        holder.productCode.setText(context.getString(R.string.ranking_product_code_line, valueOrFallback(item.getProductCode(), "-")));
        return convertView;
    }

    private String valueOrFallback(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private static final class ViewHolder {
        private final TextView rankBadge;
        private final TextView displayName;
        private final TextView salesCountBadge;
        private final TextView salesQtySum;
        private final TextView productCode;

        private ViewHolder(View root) {
            rankBadge = root.findViewById(R.id.item_rank_badge);
            displayName = root.findViewById(R.id.item_display_name);
            salesCountBadge = root.findViewById(R.id.item_sales_count_badge);
            salesQtySum = root.findViewById(R.id.item_sales_qty_sum);
            productCode = root.findViewById(R.id.item_product_code);
        }
    }
}
