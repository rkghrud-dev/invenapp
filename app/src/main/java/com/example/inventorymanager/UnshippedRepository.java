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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class UnshippedRepository {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;

    private static final String UNSHIPPED_SHEET = "미출고정보";
    private static final String UNSHIPPED_DATE_RANGE = UNSHIPPED_SHEET + "!D2:D";
    private static final int LOOKBACK_DAYS = 3;
    private static final int ORDER_CODE_INDEX = 0;
    private static final int VENDOR_INDEX = 2;
    private static final int ORDER_DATE_INDEX = 3;
    private static final int RECIPIENT_NAME_INDEX = 5;
    private static final int RECIPIENT_PHONE_INDEX = 6;
    private static final int REASON_INDEX = 14;
    private static final int SUPPLY_MEMO_INDEX = 15;
    private static final int SELLER_FEEDBACK_INDEX = 16;
    private static final int RESULT_INDEX = 17;

    public TodaySnapshot loadTodaySnapshot(String spreadsheetId, String accessToken) throws IOException {
        if (TextUtils.isEmpty(spreadsheetId)) {
            throw new IOException("미출고 조회용 스프레드시트 ID가 비어 있습니다.");
        }
        if (TextUtils.isEmpty(accessToken)) {
            throw new IOException("Google 연결이 완료되지 않았습니다.");
        }

        Calendar today = Calendar.getInstance(Locale.KOREA);
        clearTime(today);
        Calendar threshold = (Calendar) today.clone();
        threshold.add(Calendar.DAY_OF_YEAR, -LOOKBACK_DAYS);

        JSONObject dateRoot = requestJson("GET", buildValuesUrl(spreadsheetId, UNSHIPPED_DATE_RANGE), accessToken, null);
        JSONArray dateValues = dateRoot.optJSONArray("values");
        if (dateValues == null || dateValues.length() == 0) {
            return new TodaySnapshot(Collections.emptyList(), Collections.emptyList());
        }

        int startOffset = findRecentStartOffset(dateValues, threshold);
        if (startOffset < 0) {
            return new TodaySnapshot(Collections.emptyList(), Collections.emptyList());
        }

        int startRow = startOffset + 2;
        int endRow = dateValues.length() + 1;
        String dataRange = UNSHIPPED_SHEET + "!A" + startRow + ":R" + endRow;
        JSONObject root = requestJson("GET", buildValuesUrl(spreadsheetId, dataRange), accessToken, null);
        JSONArray values = root.optJSONArray("values");
        if (values == null || values.length() == 0) {
            return new TodaySnapshot(Collections.emptyList(), Collections.emptyList());
        }

        List<UnshippedItem> items = new ArrayList<>();
        Set<String> vendors = new LinkedHashSet<>();
        for (int i = 0; i < values.length(); i++) {
            JSONArray row = values.optJSONArray(i);
            if (row == null) {
                continue;
            }
            if (!isToday(getCell(row, ORDER_DATE_INDEX), today)) {
                continue;
            }

            String vendor = valueOrNone(getCell(row, VENDOR_INDEX));
            items.add(new UnshippedItem(
                    startRow + i,
                    vendor,
                    getCell(row, ORDER_CODE_INDEX),
                    getCell(row, RECIPIENT_NAME_INDEX),
                    getCell(row, RECIPIENT_PHONE_INDEX),
                    getCell(row, REASON_INDEX),
                    getCell(row, SUPPLY_MEMO_INDEX),
                    getCell(row, RESULT_INDEX),
                    getCell(row, SELLER_FEEDBACK_INDEX)
            ));
            vendors.add(vendor);
        }
        return new TodaySnapshot(new ArrayList<>(vendors), items);
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

    private int findRecentStartOffset(JSONArray dateValues, Calendar threshold) {
        int earliestRecent = -1;
        for (int i = dateValues.length() - 1; i >= 0; i--) {
            JSONArray row = dateValues.optJSONArray(i);
            String value = getCell(row, 0);
            if (TextUtils.isEmpty(value)) {
                continue;
            }
            if (isSameOrAfterThreshold(value, threshold)) {
                earliestRecent = i;
                continue;
            }
            if (earliestRecent >= 0) {
                break;
            }
        }
        return earliestRecent;
    }

    private boolean isSameOrAfterThreshold(String rawDate, Calendar threshold) {
        Calendar parsed = parseDateCell(rawDate);
        if (parsed == null) {
            return false;
        }
        return !parsed.before(threshold);
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

    private boolean isToday(String rawDate, Calendar today) {
        if (TextUtils.isEmpty(rawDate)) {
            return false;
        }
        String value = rawDate.trim();
        for (String todayText : buildTodayVariants(today)) {
            if (value.startsWith(todayText)) {
                return true;
            }
        }

        Calendar parsed = parseDateCell(value);
        if (parsed == null) {
            return false;
        }
        return parsed.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && parsed.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
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

    private void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private List<String> buildTodayVariants(Calendar today) {
        List<String> variants = new ArrayList<>();
        variants.add(new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(today.getTime()));
        variants.add(new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(today.getTime()));
        variants.add(new SimpleDateFormat("yyyy. M. d", Locale.KOREA).format(today.getTime()));
        variants.add(new SimpleDateFormat("yyyy/M/d", Locale.KOREA).format(today.getTime()));
        variants.add(new SimpleDateFormat("M/d/yyyy", Locale.KOREA).format(today.getTime()));
        return variants;
    }

    private String[] supportedPatterns() {
        return new String[]{
                "yyyy-MM-dd",
                "yyyy.MM.dd",
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

    public static final class TodaySnapshot {
        private final List<String> vendors;
        private final List<UnshippedItem> items;

        private TodaySnapshot(List<String> vendors, List<UnshippedItem> items) {
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
