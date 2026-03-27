package com.example.inventorymanager;

import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnshippedRepository {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;

    private static final String UNSHIPPED_SHEET = "미출고정보";
    private static final String UNSHIPPED_MARKER_RANGE = UNSHIPPED_SHEET + "!A2:A";
    private static final String PRODUCT_INFO_SKU_COLUMN = "G";
    private static final int TITLE_INDEX = 0;
    private static final int PRODUCT_CODE_INDEX = 1;
    private static final int VENDOR_INDEX = 2;
    private static final int ORDER_DATE_INDEX = 3;
    private static final int RECIPIENT_NAME_INDEX = 5;
    private static final int RECIPIENT_PHONE_INDEX = 6;
    private static final int REASON_INDEX = 14;
    private static final int SUPPLY_MEMO_INDEX = 15;
    private static final int SELLER_FEEDBACK_INDEX = 16;
    private static final int RESULT_INDEX = 17;
    private static final Pattern DATE_MARKER_PATTERN = Pattern.compile("(20\\d{2})\\D+(\\d{1,2})\\D+(\\d{1,2})");
    private static final Pattern PRODUCT_CODE_PATTERN = Pattern.compile("(?i)\\bGS[0-9A-Z]+\\b");

    public DateSnapshot loadDateSnapshot(SettingsStore.SheetSettings settings, Calendar targetDate, String accessToken) throws IOException {
        if (settings == null || TextUtils.isEmpty(settings.salesSpreadsheetId)) {
            throw new IOException("미출고 조회용 스프레드시트 ID가 비어 있습니다.");
        }
        if (TextUtils.isEmpty(accessToken)) {
            throw new IOException("Google 연결이 완료되지 않았습니다.");
        }

        Calendar normalizedTarget = copyOf(targetDate);
        Calendar today = copyOf(Calendar.getInstance(Locale.KOREA));

        JSONObject markerRoot = requestJson("GET", buildValuesUrl(settings.salesSpreadsheetId, UNSHIPPED_MARKER_RANGE), accessToken, null);
        JSONArray markerValues = markerRoot.optJSONArray("values");
        if (markerValues == null || markerValues.length() == 0) {
            return new DateSnapshot(Collections.emptyList(), Collections.emptyList());
        }

        int startRow = findStartRowForSelectedDate(markerValues, normalizedTarget);
        int endRow = markerValues.length() + 1;
        if (startRow > endRow) {
            return new DateSnapshot(Collections.emptyList(), Collections.emptyList());
        }

        ProductInfoLookup productInfoLookup = loadProductInfoLookup(settings, accessToken);
        String dataRange = UNSHIPPED_SHEET + "!A" + startRow + ":R" + endRow;
        JSONObject root = requestJson("GET", buildValuesUrl(settings.salesSpreadsheetId, dataRange), accessToken, null);
        JSONArray values = root.optJSONArray("values");
        if (values == null || values.length() == 0) {
            return new DateSnapshot(Collections.emptyList(), Collections.emptyList());
        }

        List<UnshippedItem> items = new ArrayList<>();
        Calendar previousMarkerDate = null;
        for (int i = 0; i < values.length(); i++) {
            JSONArray row = values.optJSONArray(i);
            if (row == null) {
                continue;
            }

            Calendar markerDate = parseDateMarker(getCell(row, TITLE_INDEX));
            if (markerDate != null) {
                previousMarkerDate = markerDate;
                continue;
            }

            if (!hasItemContent(row)) {
                continue;
            }

            Calendar actualOrderDate = parseDateCell(getCell(row, ORDER_DATE_INDEX));
            Calendar displayDate = resolveDisplayDate(actualOrderDate, previousMarkerDate, today);
            if (displayDate == null) {
                continue;
            }
            if (displayDate.compareTo(normalizedTarget) < 0 || displayDate.compareTo(today) > 0) {
                continue;
            }

            String rawTitle = getCell(row, TITLE_INDEX);
            String productCode = getCell(row, PRODUCT_CODE_INDEX);
            ProductInfo productInfo = productInfoLookup.find(productCode, rawTitle);
            items.add(new UnshippedItem(
                    startRow + i,
                    formatDateLabel(displayDate),
                    formatDateSortKey(displayDate),
                    valueOrNone(getCell(row, VENDOR_INDEX)),
                    rawTitle,
                    productCode,
                    productInfo.orderName,
                    productInfo.skuLocation,
                    getCell(row, RECIPIENT_NAME_INDEX),
                    getCell(row, RECIPIENT_PHONE_INDEX),
                    getCell(row, REASON_INDEX),
                    getCell(row, SUPPLY_MEMO_INDEX),
                    getCell(row, RESULT_INDEX),
                    getCell(row, SELLER_FEEDBACK_INDEX)
            ));
        }

        items.sort(Comparator
                .comparing(UnshippedItem::getDateSortKey)
                .thenComparingInt(UnshippedItem::getSheetRowNumber));

        Set<String> vendors = new LinkedHashSet<>();
        for (UnshippedItem item : items) {
            vendors.add(item.getVendor());
        }
        return new DateSnapshot(new ArrayList<>(vendors), items);
    }

    public void saveSellerFeedback(String spreadsheetId, int sheetRowNumber, String feedback, String accessToken) throws IOException {
        if (TextUtils.isEmpty(spreadsheetId)) {
            throw new IOException("미출고 조회용 스프레드시트 ID가 비어 있습니다.");
        }
        if (sheetRowNumber < 2) {
            throw new IOException("판매자 피드백 저장 위치를 계산하지 못했습니다.");
        }
        if (TextUtils.isEmpty(accessToken)) {
            throw new IOException("Google 연결이 완료되지 않았습니다.");
        }

        String normalizedFeedback = feedback == null ? "" : feedback.trim();
        if (TextUtils.isEmpty(normalizedFeedback)) {
            throw new IOException("저장할 판매자 피드백이 비어 있습니다.");
        }

        JSONObject body = new JSONObject();
        try {
            body.put("range", UNSHIPPED_SHEET + "!Q" + sheetRowNumber);
            body.put("majorDimension", "ROWS");
            JSONArray values = new JSONArray();
            values.put(new JSONArray().put(normalizedFeedback));
            body.put("values", values);
        } catch (JSONException exception) {
            throw new IOException("판매자 피드백 저장 값을 만들지 못했습니다.", exception);
        }

        String url = buildValuesUrl(spreadsheetId, UNSHIPPED_SHEET + "!Q" + sheetRowNumber)
                + "?valueInputOption=RAW";
        requestJson("PUT", url, accessToken, body.toString());
    }

    private int findStartRowForSelectedDate(JSONArray markerValues, Calendar targetDate) {
        int previousMarkerRow = 1;
        for (int i = 0; i < markerValues.length(); i++) {
            JSONArray row = markerValues.optJSONArray(i);
            Calendar markerDate = parseDateMarker(getCell(row, 0));
            if (markerDate != null && markerDate.compareTo(targetDate) < 0) {
                previousMarkerRow = i + 2;
            }
        }
        return Math.max(previousMarkerRow + 1, 2);
    }

    private ProductInfoLookup loadProductInfoLookup(SettingsStore.SheetSettings settings, String accessToken) throws IOException {
        if (!canLoadProductInfoLookup(settings)) {
            return ProductInfoLookup.empty();
        }

        JSONObject productRoot = requestJson("GET", buildValuesUrl(settings.salesSpreadsheetId, settings.range), accessToken, null);
        JSONArray values = productRoot.optJSONArray("values");
        if (values == null || values.length() == 0) {
            return ProductInfoLookup.empty();
        }

        int rangeStartColumn = getRangeStartColumn(settings.range);
        int productCodeIndex = getRelativeColumnIndex(settings.productCodeColumn, rangeStartColumn);
        int orderNameIndex = getRelativeColumnIndex(settings.orderCodeColumn, rangeStartColumn);
        int skuIndex = getOptionalRelativeColumnIndex(PRODUCT_INFO_SKU_COLUMN, rangeStartColumn);

        Map<String, ProductInfo> byProductCode = new HashMap<>();
        Map<String, ProductInfo> byOrderName = new HashMap<>();
        for (int i = 0; i < values.length(); i++) {
            JSONArray row = values.optJSONArray(i);
            if (row == null) {
                continue;
            }

            String productCode = safeLower(getCell(row, productCodeIndex));
            String orderName = getCell(row, orderNameIndex);
            String skuLocation = getCell(row, skuIndex);
            ProductInfo productInfo = new ProductInfo(orderName, skuLocation);

            if (!TextUtils.isEmpty(productCode) && !byProductCode.containsKey(productCode)) {
                byProductCode.put(productCode, productInfo);
            }

            String orderNameKey = safeLower(orderName);
            if (!TextUtils.isEmpty(orderNameKey) && !byOrderName.containsKey(orderNameKey)) {
                byOrderName.put(orderNameKey, productInfo);
            }
        }
        return new ProductInfoLookup(byProductCode, byOrderName);
    }

    private boolean canLoadProductInfoLookup(SettingsStore.SheetSettings settings) {
        return settings != null
                && !TextUtils.isEmpty(settings.salesSpreadsheetId)
                && !TextUtils.isEmpty(settings.range)
                && !TextUtils.isEmpty(settings.productCodeColumn)
                && !TextUtils.isEmpty(settings.orderCodeColumn);
    }

    private Calendar resolveDisplayDate(Calendar actualOrderDate, Calendar previousMarkerDate, Calendar today) {
        if (actualOrderDate != null) {
            Calendar normalizedActualDate = copyOf(actualOrderDate);
            if (normalizedActualDate.compareTo(today) > 0) {
                return null;
            }
            return normalizedActualDate;
        }
        if (previousMarkerDate == null) {
            return null;
        }

        Calendar inferredDate = copyOf(previousMarkerDate);
        inferredDate.add(Calendar.DATE, 1);
        if (inferredDate.compareTo(today) > 0) {
            return copyOf(today);
        }
        return inferredDate;
    }

    private Calendar parseDateMarker(String rawValue) {
        if (TextUtils.isEmpty(rawValue)) {
            return null;
        }

        Matcher matcher = DATE_MARKER_PATTERN.matcher(rawValue.trim());
        if (!matcher.find()) {
            return null;
        }

        try {
            return buildCalendar(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Calendar parseDateCell(String rawDate) {
        if (TextUtils.isEmpty(rawDate)) {
            return null;
        }

        String value = rawDate.trim();
        for (String pattern : supportedPatterns()) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.KOREA);
            format.setLenient(false);
            try {
                java.util.Date parsedDate = format.parse(value);
                if (parsedDate == null) {
                    continue;
                }
                Calendar calendar = Calendar.getInstance(Locale.KOREA);
                calendar.setTime(parsedDate);
                clearTime(calendar);
                return calendar;
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    private Calendar copyOf(Calendar source) {
        Calendar calendar = source == null
                ? Calendar.getInstance(Locale.KOREA)
                : (Calendar) source.clone();
        clearTime(calendar);
        return calendar;
    }

    private Calendar buildCalendar(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(Locale.KOREA);
        calendar.clear();
        calendar.set(year, month - 1, day, 0, 0, 0);
        return calendar;
    }

    private boolean hasItemContent(JSONArray row) {
        if (row == null) {
            return false;
        }
        int maxIndex = Math.min(row.length() - 1, RESULT_INDEX);
        for (int i = 0; i <= maxIndex; i++) {
            if (!TextUtils.isEmpty(row.optString(i, "").trim())) {
                return true;
            }
        }
        return false;
    }

    private String formatDateLabel(Calendar calendar) {
        return new SimpleDateFormat("M월 d일", Locale.KOREA).format(calendar.getTime());
    }

    private String formatDateSortKey(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(calendar.getTime());
    }

    private int getRangeStartColumn(String range) throws IOException {
        String working = range;
        int bangIndex = working.indexOf('!');
        if (bangIndex >= 0) {
            working = working.substring(bangIndex + 1);
        }

        String start = working.split(":")[0];
        Matcher matcher = Pattern.compile("([A-Za-z]+)").matcher(start);
        if (!matcher.find()) {
            throw new IOException("조회 범위를 해석하지 못했습니다. 예: 상품정보!A2:M");
        }
        return columnToIndex(matcher.group(1));
    }

    private int getRelativeColumnIndex(String columnLabel, int rangeStartColumn) throws IOException {
        if (TextUtils.isEmpty(columnLabel)) {
            return -1;
        }

        int absoluteIndex = columnToIndex(columnLabel);
        int relativeIndex = absoluteIndex - rangeStartColumn;
        if (relativeIndex < 0) {
            throw new IOException("컬럼 설정이 조회 범위보다 앞에 있습니다. 범위와 컬럼 문자를 확인하세요.");
        }
        return relativeIndex;
    }

    private int getOptionalRelativeColumnIndex(String columnLabel, int rangeStartColumn) throws IOException {
        if (TextUtils.isEmpty(columnLabel)) {
            return -1;
        }

        int absoluteIndex = columnToIndex(columnLabel);
        return Math.max(absoluteIndex - rangeStartColumn, -1);
    }

    private int columnToIndex(String columnLabel) throws IOException {
        String normalized = columnLabel.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]+")) {
            throw new IOException("컬럼 문자는 A, B, C 같은 형식으로 입력하세요.");
        }

        int value = 0;
        for (int i = 0; i < normalized.length(); i++) {
            value = (value * 26) + (normalized.charAt(i) - 'A' + 1);
        }
        return value - 1;
    }

    private String buildValuesUrl(String spreadsheetId, String range) {
        return "https://sheets.googleapis.com/v4/spreadsheets/"
                + Uri.encode(spreadsheetId)
                + "/values/"
                + Uri.encode(range);
    }

    private JSONObject requestJson(String method, String urlValue, String accessToken, String body) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlValue).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream stream = connection.getOutputStream()) {
                    stream.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseBody = readStream(stream);

            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException(mapApiError(responseCode, responseBody));
            }

            if (TextUtils.isEmpty(responseBody)) {
                return new JSONObject();
            }
            return new JSONObject(responseBody);
        } catch (JSONException exception) {
            throw new IOException("Google Sheets 응답을 해석하지 못했습니다.", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private String[] supportedPatterns() {
        return new String[]{
                "yyyy-MM-dd",
                "yyyy.MM.dd",
                "yyyy.M.d",
                "yyyy. M. d",
                "yyyy/MM/dd",
                "yyyy/M/d",
                "M/d/yyyy",
                "MM/dd/yyyy",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd a h:mm:ss",
                "yyyy-MM-dd a h:mm",
                "yyyy.MM.dd HH:mm:ss",
                "yyyy.MM.dd HH:mm",
                "yyyy. M. d HH:mm:ss",
                "yyyy. M. d HH:mm",
                "yyyy.MM.dd a h:mm:ss",
                "yyyy. M. d a h:mm:ss",
                "yyyy. M. d a h:mm",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd HH:mm",
                "yyyy/MM/dd a h:mm:ss",
                "M/d/yyyy HH:mm:ss",
                "M/d/yyyy HH:mm",
                "M/d/yyyy a h:mm:ss"
        };
    }

    private String getCell(JSONArray row, int index) {
        if (row == null || index < 0 || index >= row.length()) {
            return "";
        }
        return row.optString(index, "").trim();
    }

    private String valueOrNone(String value) {
        return TextUtils.isEmpty(value) ? "없음" : value.trim();
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private String mapApiError(int code, String body) {
        String detail = extractApiMessage(body);
        if (code == 400) {
            return "미출고정보 시트 요청 형식을 확인하세요. " + detail;
        }
        if (code == 401) {
            return "Google 연결이 만료되었거나 승인되지 않았습니다. 다시 연결해 주세요. " + detail;
        }
        if (code == 403) {
            return "미출고정보 시트 읽기/쓰기 권한이 없습니다. " + detail;
        }
        if (code == 404) {
            return "미출고정보 시트 또는 스프레드시트를 찾지 못했습니다. " + detail;
        }
        return "미출고정보 시트 처리에 실패했습니다. HTTP " + code + ". " + detail;
    }

    private String extractApiMessage(String body) {
        if (TextUtils.isEmpty(body)) {
            return "";
        }
        try {
            JSONObject root = new JSONObject(body);
            JSONObject error = root.optJSONObject("error");
            if (error == null) {
                return body;
            }
            String message = error.optString("message", body);
            return TextUtils.isEmpty(message) ? body : message;
        } catch (JSONException ignored) {
            return body;
        }
    }

    private static final class ProductInfoLookup {
        private final Map<String, ProductInfo> byProductCode;
        private final Map<String, ProductInfo> byOrderName;

        private ProductInfoLookup(Map<String, ProductInfo> byProductCode, Map<String, ProductInfo> byOrderName) {
            this.byProductCode = byProductCode;
            this.byOrderName = byOrderName;
        }

        private static ProductInfoLookup empty() {
            return new ProductInfoLookup(Collections.emptyMap(), Collections.emptyMap());
        }

        private ProductInfo find(String productCode, String rawTitle) {
            ProductInfo match = findByProductCode(productCode);
            if (match != null) {
                return match;
            }

            match = findByProductCode(extractEmbeddedProductCode(rawTitle));
            if (match != null) {
                return match;
            }

            String rawTitleKey = rawTitle == null ? "" : rawTitle.trim().toLowerCase(Locale.ROOT);
            if (!TextUtils.isEmpty(rawTitleKey)) {
                match = byOrderName.get(rawTitleKey);
                if (match != null) {
                    return match;
                }
            }
            return ProductInfo.EMPTY;
        }

        private ProductInfo findByProductCode(String productCode) {
            String productCodeKey = productCode == null ? "" : productCode.trim().toLowerCase(Locale.ROOT);
            if (TextUtils.isEmpty(productCodeKey)) {
                return null;
            }
            return byProductCode.get(productCodeKey);
        }

        private String extractEmbeddedProductCode(String rawTitle) {
            if (TextUtils.isEmpty(rawTitle)) {
                return "";
            }
            Matcher matcher = PRODUCT_CODE_PATTERN.matcher(rawTitle);
            if (!matcher.find()) {
                return "";
            }
            return matcher.group();
        }
    }

    private static final class ProductInfo {
        private static final ProductInfo EMPTY = new ProductInfo("", "");

        private final String orderName;
        private final String skuLocation;

        private ProductInfo(String orderName, String skuLocation) {
            this.orderName = orderName == null ? "" : orderName.trim();
            this.skuLocation = skuLocation == null ? "" : skuLocation.trim();
        }
    }

    public static final class DateSnapshot {
        private final List<String> vendors;
        private final List<UnshippedItem> items;

        private DateSnapshot(List<String> vendors, List<UnshippedItem> items) {
            this.vendors = vendors;
            this.items = items;
        }

        public List<String> getVendors() {
            return vendors;
        }

        public List<UnshippedItem> getItems() {
            return items;
        }
    }
}
