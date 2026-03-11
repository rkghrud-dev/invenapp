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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SheetsRepository {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;

    public List<InventoryItem> search(SettingsStore.SheetSettings settings, String query, String accessToken) throws IOException {
        if (!settings.isReady()) {
            throw new IOException("시트 설정이 비어 있습니다.");
        }
        if (TextUtils.isEmpty(accessToken)) {
            throw new IOException("Google 연결이 완료되지 않았습니다.");
        }

        String normalizedQuery = safeLower(query);
        if (TextUtils.isEmpty(normalizedQuery)) {
            return Collections.emptyList();
        }

        String responseBody = requestSheetValues(settings, accessToken);
        return parseAndFilter(responseBody, settings, normalizedQuery);
    }

    private String requestSheetValues(SettingsStore.SheetSettings settings, String accessToken) throws IOException {
        Uri.Builder builder = new Uri.Builder()
                .scheme("https")
                .authority("sheets.googleapis.com")
                .appendPath("v4")
                .appendPath("spreadsheets")
                .appendPath(settings.spreadsheetId)
                .appendPath("values")
                .appendPath(settings.range)
                .appendQueryParameter("majorDimension", "ROWS");

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(builder.build().toString()).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readStream(stream);

            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException(mapApiError(responseCode, body));
            }

            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<InventoryItem> parseAndFilter(String responseBody, SettingsStore.SheetSettings settings, String normalizedQuery) throws IOException {
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONArray values = root.optJSONArray("values");
            if (values == null || values.length() == 0) {
                return Collections.emptyList();
            }

            int rangeStartColumn = getRangeStartColumn(settings.range);
            int productCodeIndex = getRelativeColumnIndex(settings.productCodeColumn, rangeStartColumn);
            int orderCodeIndex = getRelativeColumnIndex(settings.orderCodeColumn, rangeStartColumn);
            int productNameIndex = getRelativeColumnIndex(settings.productNameColumn, rangeStartColumn);
            int stockIndex = getRelativeColumnIndex(settings.stockColumn, rangeStartColumn);

            List<InventoryItem> matches = new ArrayList<>();
            for (int i = 0; i < values.length(); i++) {
                JSONArray row = values.optJSONArray(i);
                if (row == null) {
                    continue;
                }

                String productCode = getCell(row, productCodeIndex);
                String orderCode = getCell(row, orderCodeIndex);
                String productName = getCell(row, productNameIndex);
                if (TextUtils.isEmpty(productName)) {
                    productName = orderCode;
                }
                String stockQuantity = getCell(row, stockIndex);

                MatchResult matchResult = scoreMatch(normalizedQuery, productCode, orderCode, productName);
                if (matchResult.score <= 0) {
                    continue;
                }

                matches.add(new InventoryItem(
                        productCode,
                        orderCode,
                        productName,
                        stockQuantity,
                        matchResult.reason,
                        matchResult.score
                ));
            }

            matches.sort(Comparator.comparingInt(InventoryItem::getScore).reversed());
            return matches;
        } catch (JSONException exception) {
            throw new IOException("시트 응답 형식을 해석하지 못했습니다.", exception);
        }
    }

    private MatchResult scoreMatch(String normalizedQuery, String productCode, String orderCode, String productName) {
        String productCodeLower = safeLower(productCode);
        String orderCodeLower = safeLower(orderCode);
        String productNameLower = safeLower(productName);

        if (!TextUtils.isEmpty(productCodeLower) && productCodeLower.equals(normalizedQuery)) {
            return new MatchResult(120, "상품코드 정확히 일치");
        }
        if (!TextUtils.isEmpty(orderCodeLower) && orderCodeLower.equals(normalizedQuery)) {
            return new MatchResult(110, "주문코드 정확히 일치");
        }
        if (!TextUtils.isEmpty(productNameLower) && productNameLower.equals(normalizedQuery)) {
            return new MatchResult(100, "상품명 정확히 일치");
        }
        if (!TextUtils.isEmpty(productCodeLower) && productCodeLower.contains(normalizedQuery)) {
            return new MatchResult(90, "상품코드 부분 일치");
        }
        if (!TextUtils.isEmpty(orderCodeLower) && orderCodeLower.contains(normalizedQuery)) {
            return new MatchResult(80, "주문코드 부분 일치");
        }
        if (!TextUtils.isEmpty(productNameLower) && productNameLower.contains(normalizedQuery)) {
            return new MatchResult(70, "상품명 부분 일치");
        }
        return new MatchResult(0, "");
    }

    private String getCell(JSONArray row, int relativeIndex) {
        if (relativeIndex < 0 || relativeIndex >= row.length()) {
            return "";
        }
        return row.optString(relativeIndex, "").trim();
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
        String message = extractApiMessage(body);
        if (code == 400) {
            return "시트 범위 또는 컬럼 설정을 확인하세요. " + message;
        }
        if (code == 401) {
            return "Google 연결이 만료되었거나 승인되지 않았습니다. 다시 연결해 주세요. " + message;
        }
        if (code == 403) {
            return "현재 Google 계정에 이 스프레드시트 권한이 없습니다. " + message;
        }
        if (code == 404) {
            return "스프레드시트를 찾지 못했습니다. ID를 확인하세요. " + message;
        }
        return "시트 조회에 실패했습니다. HTTP " + code + ". " + message;
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

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class MatchResult {
        private final int score;
        private final String reason;

        private MatchResult(int score, String reason) {
            this.score = score;
            this.reason = reason;
        }
    }
}