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
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SheetsRepository {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final String SKU_LOCATION_COLUMN = "G";

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

        String responseBody = requestSheetValues(settings.spreadsheetId, settings.range, accessToken);
        List<InventoryItem> matches = parseAndFilter(responseBody, settings, normalizedQuery);
        if (matches.isEmpty()) {
            return matches;
        }

        Map<String, String> skuLocationByCode = loadSkuLocationByCode(settings, accessToken);
        List<InventoryItem> skuAppliedMatches = applySkuLocations(matches, skuLocationByCode);

        Map<String, BigDecimal> soldQuantityByCode = settings.isSalesReady()
                ? loadSoldQuantityByCode(settings, accessToken)
                : Collections.emptyMap();
        return applySoldQuantities(skuAppliedMatches, soldQuantityByCode);
    }

    private String requestSheetValues(String spreadsheetId, String range, String accessToken) throws IOException {
        Uri.Builder builder = new Uri.Builder()
                .scheme("https")
                .authority("sheets.googleapis.com")
                .appendPath("v4")
                .appendPath("spreadsheets")
                .appendPath(spreadsheetId)
                .appendPath("values")
                .appendPath(range)
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
            int importPriceIndex = getOptionalRelativeColumnIndex("D", rangeStartColumn);
            int supplyPriceIndex = getOptionalRelativeColumnIndex("E", rangeStartColumn);
            int retailPriceIndex = getOptionalRelativeColumnIndex("F", rangeStartColumn);
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
                String importPrice = getCell(row, importPriceIndex);
                String supplyPrice = getCell(row, supplyPriceIndex);
                String retailPrice = getCell(row, retailPriceIndex);
                String stockQuantity = getCell(row, stockIndex);

                MatchResult matchResult = scoreMatch(normalizedQuery, productCode, orderCode, productName);
                if (matchResult.score <= 0) {
                    continue;
                }

                matches.add(new InventoryItem(
                        productCode,
                        orderCode,
                        productName,
                        "",
                        importPrice,
                        supplyPrice,
                        retailPrice,
                        stockQuantity,
                        "0",
                        fallbackActualStock(stockQuantity),
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

    private Map<String, String> loadSkuLocationByCode(SettingsStore.SheetSettings settings, String accessToken) throws IOException {
        if (TextUtils.isEmpty(settings.salesSpreadsheetId) || TextUtils.isEmpty(settings.range)) {
            return Collections.emptyMap();
        }

        String responseBody = requestSheetValues(settings.salesSpreadsheetId, settings.range, accessToken);
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONArray values = root.optJSONArray("values");
            if (values == null || values.length() == 0) {
                return Collections.emptyMap();
            }

            int rangeStartColumn = getRangeStartColumn(settings.range);
            int productCodeIndex = getRelativeColumnIndex(settings.productCodeColumn, rangeStartColumn);
            int skuLocationIndex = getOptionalRelativeColumnIndex(SKU_LOCATION_COLUMN, rangeStartColumn);

            Map<String, String> skuLocationByCode = new HashMap<>();
            for (int i = 0; i < values.length(); i++) {
                JSONArray row = values.optJSONArray(i);
                if (row == null) {
                    continue;
                }

                String productCode = safeLower(getCell(row, productCodeIndex));
                String skuLocation = getCell(row, skuLocationIndex);
                if (TextUtils.isEmpty(productCode) || TextUtils.isEmpty(skuLocation)) {
                    continue;
                }
                if (!skuLocationByCode.containsKey(productCode)) {
                    skuLocationByCode.put(productCode, skuLocation);
                }
            }
            return skuLocationByCode;
        } catch (JSONException exception) {
            throw new IOException("SKU 위치 시트 응답 형식을 해석하지 못했습니다.", exception);
        }
    }

    private List<InventoryItem> applySkuLocations(List<InventoryItem> items, Map<String, String> skuLocationByCode) {
        if (items == null || items.isEmpty() || skuLocationByCode.isEmpty()) {
            return items;
        }

        List<InventoryItem> enrichedItems = new ArrayList<>(items.size());
        for (InventoryItem item : items) {
            String skuLocation = "";
            if (!TextUtils.isEmpty(item.getProductCode())) {
                String mappedValue = skuLocationByCode.get(safeLower(item.getProductCode()));
                if (mappedValue != null) {
                    skuLocation = mappedValue;
                }
            }

            enrichedItems.add(new InventoryItem(
                    item.getProductCode(),
                    item.getOrderCode(),
                    item.getProductName(),
                    skuLocation,
                    item.getImportPrice(),
                    item.getSupplyPrice(),
                    item.getRetailPrice(),
                    item.getStockQuantity(),
                    item.getTodaySoldQuantity(),
                    item.getActualStockQuantity(),
                    item.getMatchReason(),
                    item.getScore()
            ));
        }
        return enrichedItems;
    }

    private Map<String, BigDecimal> loadSoldQuantityByCode(SettingsStore.SheetSettings settings, String accessToken) throws IOException {
        String responseBody = requestSheetValues(settings.salesSpreadsheetId, settings.salesRange, accessToken);
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONArray values = root.optJSONArray("values");
            if (values == null || values.length() == 0) {
                return Collections.emptyMap();
            }

            int rangeStartColumn = getRangeStartColumn(settings.salesRange);
            int productCodeIndex = getRelativeColumnIndex(settings.salesProductCodeColumn, rangeStartColumn);
            int salesQuantityIndex = getRelativeColumnIndex(settings.salesQuantityColumn, rangeStartColumn);

            Map<String, BigDecimal> soldQuantityByCode = new HashMap<>();
            for (int i = 0; i < values.length(); i++) {
                JSONArray row = values.optJSONArray(i);
                if (row == null) {
                    continue;
                }

                String productCode = safeLower(getCell(row, productCodeIndex));
                if (TextUtils.isEmpty(productCode)) {
                    continue;
                }

                BigDecimal soldQuantity = parseQuantity(getCell(row, salesQuantityIndex));
                if (soldQuantity == null) {
                    continue;
                }

                BigDecimal previous = soldQuantityByCode.get(productCode);
                soldQuantityByCode.put(productCode, previous == null ? soldQuantity : previous.add(soldQuantity));
            }
            return soldQuantityByCode;
        } catch (JSONException exception) {
            throw new IOException("오늘 판매 시트 응답 형식을 해석하지 못했습니다.", exception);
        }
    }

    private List<InventoryItem> applySoldQuantities(List<InventoryItem> items, Map<String, BigDecimal> soldQuantityByCode) {
        List<InventoryItem> enrichedItems = new ArrayList<>(items.size());
        for (InventoryItem item : items) {
            BigDecimal soldQuantity = BigDecimal.ZERO;
            if (!TextUtils.isEmpty(item.getProductCode())) {
                BigDecimal mappedQuantity = soldQuantityByCode.get(safeLower(item.getProductCode()));
                if (mappedQuantity != null) {
                    soldQuantity = mappedQuantity;
                }
            }

            enrichedItems.add(new InventoryItem(
                    item.getProductCode(),
                    item.getOrderCode(),
                    item.getProductName(),
                    item.getSkuLocation(),
                    item.getImportPrice(),
                    item.getSupplyPrice(),
                    item.getRetailPrice(),
                    item.getStockQuantity(),
                    formatQuantity(soldQuantity),
                    calculateActualStock(item.getStockQuantity(), soldQuantity),
                    item.getMatchReason(),
                    item.getScore()
            ));
        }
        return enrichedItems;
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

    private int getOptionalRelativeColumnIndex(String columnLabel, int rangeStartColumn) throws IOException {
        if (TextUtils.isEmpty(columnLabel)) {
            return -1;
        }

        int absoluteIndex = columnToIndex(columnLabel);
        int relativeIndex = absoluteIndex - rangeStartColumn;
        return Math.max(relativeIndex, -1);
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

    private String calculateActualStock(String stockQuantity, BigDecimal soldQuantity) {
        BigDecimal parsedStock = parseQuantity(stockQuantity);
        if (parsedStock == null) {
            return "";
        }
        return formatQuantity(parsedStock.subtract(soldQuantity == null ? BigDecimal.ZERO : soldQuantity));
    }

    private String fallbackActualStock(String stockQuantity) {
        BigDecimal parsedStock = parseQuantity(stockQuantity);
        if (parsedStock == null) {
            return "";
        }
        return formatQuantity(parsedStock);
    }

    private BigDecimal parseQuantity(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }

        String normalized = value.trim().replace(",", "");
        if (TextUtils.isEmpty(normalized)) {
            return null;
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatQuantity(BigDecimal value) {
        if (value == null) {
            return "";
        }

        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
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
