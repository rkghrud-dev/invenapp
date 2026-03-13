package com.example.inventorymanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final String KEY_SALES_SPREADSHEET_ID = "sales_spreadsheet_id";
    private static final String KEY_SALES_RANGE = "sales_range";
    private static final String KEY_SALES_PRODUCT_CODE_COLUMN = "sales_product_code_column";
    private static final String KEY_SALES_QUANTITY_COLUMN = "sales_quantity_column";
    private static final String KEY_RANKING_SPREADSHEET_ID = "ranking_spreadsheet_id";
    private static final String KEY_LAST_AUTHORIZED_EMAIL = "last_authorized_email";
    private static final String KEY_DARK_MODE_ENABLED = "dark_mode_enabled";
    private static final String KEY_THEME_PALETTE = "theme_palette";
    private static final String KEY_ADMIN_MODE_ENABLED = "admin_mode_enabled";
    private static final String KEY_ADMIN_REMINDER_ENABLED = "admin_reminder_enabled";
    private static final String KEY_ADMIN_REMINDER_HOUR = "admin_reminder_hour";
    private static final String KEY_ADMIN_REMINDER_MINUTE = "admin_reminder_minute";
    private static final String KEY_ADMIN_PASSWORD_HASH = "admin_password_hash";

    private static final String DEFAULT_SPREADSHEET_ID = "1HWR8zdvx0DYbl4ac9hmGuaaIA47nMO0v1CtO99PyC6w";
    private static final String DEFAULT_RANGE = "상품정보!A2:M";
    private static final String DEFAULT_PRODUCT_CODE_COLUMN = "A";
    private static final String DEFAULT_ORDER_CODE_COLUMN = "B";
    private static final String DEFAULT_PRODUCT_NAME_COLUMN = "B";
    private static final String DEFAULT_STOCK_COLUMN = "M";
    private static final String DEFAULT_SALES_SPREADSHEET_ID = "1WgLu0RciK6NKnPjKrcxmwIq0RouUsgv1Ib06eR4SI9A";
    private static final String DEFAULT_SALES_RANGE = "cj발주서!B:E";
    private static final String DEFAULT_SALES_PRODUCT_CODE_COLUMN = "B";
    private static final String DEFAULT_SALES_QUANTITY_COLUMN = "E";
    private static final String DEFAULT_RANKING_SPREADSHEET_ID = "19Ahq0nB5zclSFVUEx_FfRuJ3DcftKZNPh-JD2Sn1X_Q";
    private static final int DEFAULT_ADMIN_REMINDER_HOUR = 15;
    private static final int DEFAULT_ADMIN_REMINDER_MINUTE = 30;

    public static final int THEME_PALETTE_1 = 1;
    public static final int THEME_PALETTE_2 = 2;
    public static final int THEME_PALETTE_3 = 3;
    public static final int THEME_PALETTE_4 = 4;

    private final SharedPreferences preferences;

    public SettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isDarkModeEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_DARK_MODE_ENABLED, false);
    }

    public static int getThemePaletteId(Context context) {
        int paletteId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_THEME_PALETTE, THEME_PALETTE_1);
        return sanitizeThemePaletteId(paletteId);
    }

    public static int resolveThemeResId(Context context) {
        return resolveThemeResId(getThemePaletteId(context));
    }

    public static int resolveThemeResId(int paletteId) {
        switch (sanitizeThemePaletteId(paletteId)) {
            case THEME_PALETTE_2:
                return R.style.Theme_InventoryManager_Palette2;
            case THEME_PALETTE_3:
                return R.style.Theme_InventoryManager_Palette3;
            case THEME_PALETTE_4:
                return R.style.Theme_InventoryManager_Palette4;
            case THEME_PALETTE_1:
            default:
                return R.style.Theme_InventoryManager_Palette1;
        }
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
                preferences.getString(KEY_STOCK_COLUMN, DEFAULT_STOCK_COLUMN),
                preferences.getString(KEY_SALES_SPREADSHEET_ID, DEFAULT_SALES_SPREADSHEET_ID),
                preferences.getString(KEY_SALES_RANGE, DEFAULT_SALES_RANGE),
                preferences.getString(KEY_SALES_PRODUCT_CODE_COLUMN, DEFAULT_SALES_PRODUCT_CODE_COLUMN),
                preferences.getString(KEY_SALES_QUANTITY_COLUMN, DEFAULT_SALES_QUANTITY_COLUMN),
                preferences.getString(KEY_RANKING_SPREADSHEET_ID, DEFAULT_RANKING_SPREADSHEET_ID)
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
                .putString(KEY_SALES_SPREADSHEET_ID, safe(settings.salesSpreadsheetId))
                .putString(KEY_SALES_RANGE, safe(settings.salesRange))
                .putString(KEY_SALES_PRODUCT_CODE_COLUMN, safe(settings.salesProductCodeColumn))
                .putString(KEY_SALES_QUANTITY_COLUMN, safe(settings.salesQuantityColumn))
                .putString(KEY_RANKING_SPREADSHEET_ID, safe(settings.rankingSpreadsheetId))
                .apply();
    }

    public boolean isDarkModeEnabled() {
        return preferences.getBoolean(KEY_DARK_MODE_ENABLED, false);
    }

    public void setDarkModeEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_DARK_MODE_ENABLED, enabled).apply();
    }

    public int getThemePaletteId() {
        return sanitizeThemePaletteId(preferences.getInt(KEY_THEME_PALETTE, THEME_PALETTE_1));
    }

    public void setThemePaletteId(int paletteId) {
        preferences.edit().putInt(KEY_THEME_PALETTE, sanitizeThemePaletteId(paletteId)).apply();
    }

    public boolean isAdminModeEnabled() {
        return preferences.getBoolean(KEY_ADMIN_MODE_ENABLED, false);
    }

    public void setAdminModeEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ADMIN_MODE_ENABLED, enabled).apply();
    }

    public boolean isAdminReminderEnabled() {
        return preferences.getBoolean(KEY_ADMIN_REMINDER_ENABLED, false);
    }

    public void setAdminReminderEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ADMIN_REMINDER_ENABLED, enabled).apply();
    }

    public int getAdminReminderHour() {
        return preferences.getInt(KEY_ADMIN_REMINDER_HOUR, DEFAULT_ADMIN_REMINDER_HOUR);
    }

    public int getAdminReminderMinute() {
        return preferences.getInt(KEY_ADMIN_REMINDER_MINUTE, DEFAULT_ADMIN_REMINDER_MINUTE);
    }

    public void setAdminReminderTime(int hour, int minute) {
        preferences.edit()
                .putInt(KEY_ADMIN_REMINDER_HOUR, hour)
                .putInt(KEY_ADMIN_REMINDER_MINUTE, minute)
                .apply();
    }

    public boolean hasAdminPassword() {
        return !TextUtils.isEmpty(preferences.getString(KEY_ADMIN_PASSWORD_HASH, ""));
    }

    public void saveAdminPassword(String rawPassword) {
        preferences.edit().putString(KEY_ADMIN_PASSWORD_HASH, hashPassword(rawPassword)).apply();
    }

    public boolean verifyAdminPassword(String rawPassword) {
        String storedHash = preferences.getString(KEY_ADMIN_PASSWORD_HASH, "");
        if (TextUtils.isEmpty(storedHash)) {
            return false;
        }
        return storedHash.equals(hashPassword(rawPassword));
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

    private static int sanitizeThemePaletteId(int paletteId) {
        switch (paletteId) {
            case THEME_PALETTE_2:
            case THEME_PALETTE_3:
            case THEME_PALETTE_4:
                return paletteId;
            case THEME_PALETTE_1:
            default:
                return THEME_PALETTE_1;
        }
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

    private static String hashPassword(String rawPassword) {
        String source = rawPassword == null ? "" : rawPassword.trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    public static final class SheetSettings {
        public final String apiKey;
        public final String spreadsheetId;
        public final String range;
        public final String productCodeColumn;
        public final String orderCodeColumn;
        public final String productNameColumn;
        public final String stockColumn;
        public final String salesSpreadsheetId;
        public final String salesRange;
        public final String salesProductCodeColumn;
        public final String salesQuantityColumn;
        public final String rankingSpreadsheetId;

        public SheetSettings(
                String apiKey,
                String spreadsheetId,
                String range,
                String productCodeColumn,
                String orderCodeColumn,
                String productNameColumn,
                String stockColumn,
                String salesSpreadsheetId,
                String salesRange,
                String salesProductCodeColumn,
                String salesQuantityColumn,
                String rankingSpreadsheetId
        ) {
            this.apiKey = clean(apiKey);
            this.spreadsheetId = clean(spreadsheetId);
            this.range = clean(range);
            this.productCodeColumn = normalizeColumn(productCodeColumn);
            this.orderCodeColumn = normalizeColumn(orderCodeColumn);
            this.productNameColumn = normalizeColumn(productNameColumn);
            this.stockColumn = normalizeColumn(stockColumn);
            this.salesSpreadsheetId = clean(salesSpreadsheetId);
            this.salesRange = clean(salesRange);
            this.salesProductCodeColumn = normalizeColumn(salesProductCodeColumn);
            this.salesQuantityColumn = normalizeColumn(salesQuantityColumn);
            this.rankingSpreadsheetId = clean(rankingSpreadsheetId);
        }

        public boolean isReady() {
            return !TextUtils.isEmpty(spreadsheetId)
                    && !TextUtils.isEmpty(range)
                    && !TextUtils.isEmpty(stockColumn)
                    && (!TextUtils.isEmpty(productCodeColumn)
                    || !TextUtils.isEmpty(orderCodeColumn)
                    || !TextUtils.isEmpty(productNameColumn));
        }

        public boolean isSalesReady() {
            return !TextUtils.isEmpty(salesSpreadsheetId)
                    && !TextUtils.isEmpty(salesRange)
                    && !TextUtils.isEmpty(salesProductCodeColumn)
                    && !TextUtils.isEmpty(salesQuantityColumn);
        }

        public boolean isRankingReady() {
            return !TextUtils.isEmpty(rankingSpreadsheetId);
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
            if (isSalesReady()) {
                builder.append("\n");
                builder.append("오늘판매 ").append(salesRange);
                builder.append("  ·  상품코드 ").append(emptyFallback(salesProductCodeColumn));
                builder.append("  ·  판매수량 ").append(emptyFallback(salesQuantityColumn));
            }
            if (isRankingReady()) {
                builder.append("\n");
                builder.append("판매량DB ").append(rankingSpreadsheetId);
            }
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