package com.example.inventorymanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsStore {
    private static final String PREFS_NAME = "inventory_manager_prefs";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_SPREADSHEET_ID = "spreadsheet_id";
    private static final String KEY_RANGE = "sheet_range";
    private static final String KEY_PRODUCT_CODE_COLUMN = "product_code_column";
    private static final String KEY_ORDER_CODE_COLUMN = "order_code_column";
    private static final String KEY_PRODUCT_NAME_COLUMN = "product_name_column";
    private static final String KEY_STOCK_COLUMN = "stock_column";
    private static final String KEY_LAST_AUTHORIZED_EMAIL = "last_authorized_email";
    private static final String KEY_DARK_MODE_ENABLED = "dark_mode_enabled";

    private static final String DEFAULT_SPREADSHEET_ID = "1HWR8zdvx0DYbl4ac9hmGuaaIA47nMO0v1CtO99PyC6w";
    private static final String DEFAULT_RANGE = "상품정보!A2:M";
    private static final String DEFAULT_PRODUCT_CODE_COLUMN = "A";
    private static final String DEFAULT_ORDER_CODE_COLUMN = "B";
    private static final String DEFAULT_PRODUCT_NAME_COLUMN = "B";
    private static final String DEFAULT_STOCK_COLUMN = "M";

    private final SharedPreferences preferences;

    public SettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isDarkModeEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_DARK_MODE_ENABLED, false);
    }

    public SheetSettings load() {
        String orderCodeColumn = preferences.getString(KEY_ORDER_CODE_COLUMN, DEFAULT_ORDER_CODE_COLUMN);
        String productNameColumn = preferences.getString(KEY_PRODUCT_NAME_COLUMN, DEFAULT_PRODUCT_NAME_COLUMN);
        if (TextUtils.isEmpty(productNameColumn)) {
            productNameColumn = orderCodeColumn;
        }

        return new SheetSettings(
                preferences.getString(KEY_API_KEY, ""),
                preferences.getString(KEY_SPREADSHEET_ID, DEFAULT_SPREADSHEET_ID),
                preferences.getString(KEY_RANGE, DEFAULT_RANGE),
                preferences.getString(KEY_PRODUCT_CODE_COLUMN, DEFAULT_PRODUCT_CODE_COLUMN),
                orderCodeColumn,
                productNameColumn,
                preferences.getString(KEY_STOCK_COLUMN, DEFAULT_STOCK_COLUMN)
        );
    }

    public void save(SheetSettings settings) {
        preferences.edit()
                .putString(KEY_API_KEY, safe(settings.apiKey))
                .putString(KEY_SPREADSHEET_ID, safe(settings.spreadsheetId))
                .putString(KEY_RANGE, safe(settings.range))
                .putString(KEY_PRODUCT_CODE_COLUMN, safe(settings.productCodeColumn))
                .putString(KEY_ORDER_CODE_COLUMN, safe(settings.orderCodeColumn))
                .putString(KEY_PRODUCT_NAME_COLUMN, safe(settings.productNameColumn))
                .putString(KEY_STOCK_COLUMN, safe(settings.stockColumn))
                .apply();
    }

    public boolean isDarkModeEnabled() {
        return preferences.getBoolean(KEY_DARK_MODE_ENABLED, false);
    }

    public void setDarkModeEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_DARK_MODE_ENABLED, enabled).apply();
    }

    public void saveLastAuthorizedEmail(String email) {
        preferences.edit().putString(KEY_LAST_AUTHORIZED_EMAIL, safe(email)).apply();
    }

    public String getLastAuthorizedEmail() {
        return preferences.getString(KEY_LAST_AUTHORIZED_EMAIL, "");
    }

    public void clearLastAuthorizedEmail() {
        preferences.edit().remove(KEY_LAST_AUTHORIZED_EMAIL).apply();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static String normalizeColumn(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String sanitizeSpreadsheetId(String input) {
        if (input == null) {
            return "";
        }

        String trimmed = input.trim();
        Matcher matcher = Pattern.compile("/spreadsheets/d/([a-zA-Z0-9-_]+)").matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return trimmed;
    }

    public static final class SheetSettings {
        public final String apiKey;
        public final String spreadsheetId;
        public final String range;
        public final String productCodeColumn;
        public final String orderCodeColumn;
        public final String productNameColumn;
        public final String stockColumn;

        public SheetSettings(
                String apiKey,
                String spreadsheetId,
                String range,
                String productCodeColumn,
                String orderCodeColumn,
                String productNameColumn,
                String stockColumn
        ) {
            this.apiKey = clean(apiKey);
            this.spreadsheetId = clean(spreadsheetId);
            this.range = clean(range);
            this.productCodeColumn = normalizeColumn(productCodeColumn);
            this.orderCodeColumn = normalizeColumn(orderCodeColumn);
            this.productNameColumn = normalizeColumn(productNameColumn);
            this.stockColumn = normalizeColumn(stockColumn);
        }

        public boolean isReady() {
            return !TextUtils.isEmpty(spreadsheetId)
                    && !TextUtils.isEmpty(range)
                    && !TextUtils.isEmpty(stockColumn)
                    && (!TextUtils.isEmpty(productCodeColumn)
                    || !TextUtils.isEmpty(orderCodeColumn)
                    || !TextUtils.isEmpty(productNameColumn));
        }

        public String toSummary() {
            if (!isReady()) {
                return "시트 설정이 아직 비어 있습니다.";
            }

            StringBuilder builder = new StringBuilder();
            builder.append(range);
            builder.append("  ·  상품코드 ").append(emptyFallback(productCodeColumn));
            builder.append("  ·  주문코드 ").append(emptyFallback(orderCodeColumn));
            if (!TextUtils.isEmpty(productNameColumn)) {
                builder.append("  ·  상품명 ").append(productNameColumn);
            }
            builder.append("  ·  재고 ").append(emptyFallback(stockColumn));
            return builder.toString();
        }

        private static String clean(String value) {
            return value == null ? "" : value.trim();
        }

        private static String emptyFallback(String value) {
            return TextUtils.isEmpty(value) ? "-" : value;
        }
    }
}