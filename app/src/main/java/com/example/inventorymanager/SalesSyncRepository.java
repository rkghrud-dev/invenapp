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
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SalesSyncRepository {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;

    private static final String DAILY_DB_SHEET = "daily_sales_db";
    private static final String TODAY_RANK_VIEW_SHEET = "today_rank_view";
    private static final String SYNC_LOG_SHEET = "sync_log";

    private final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA);

    public SyncResult syncToday(SettingsStore.SheetSettings settings, String accessToken) throws IOException {
        if (!settings.isReady()) {
            throw new IOException("상품정보 시트 설정이 비어 있습니다.");
        }
        if (!settings.isSalesReady()) {
            throw new IOException("오늘 판매 시트 설정이 비어 있습니다.");
        }
        if (!settings.isRankingReady()) {
            throw new IOException("판매량 DB 스프레드시트 ID가 비어 있습니다.");
        }
        if (TextUtils.isEmpty(accessToken)) {
            throw new IOException("Google 연결이 완료되지 않았습니다.");
        }

        Calendar now = Calendar.getInstance();
        String snapshotDate = dayFormat.format(now.getTime());
        String capturedAt = timestampFormat.format(now.getTime());

        Map<String, String> displayNameByCode = loadDisplayNameByCode(settings, accessToken);
        SourceSalesSnapshot snapshot = loadSalesSnapshot(settings, accessToken);

        List<SalesAggregate> aggregates = new ArrayList<>(snapshot.aggregateByCode.values());
        for (SalesAggregate aggregate : aggregates) {
            String displayName = displayNameByCode.get(normalizeKey(aggregate.productCode));
            if (TextUtils.isEmpty(displayName)) {
                displayName = aggregate.productCode;
            }
            aggregate.displayName = displayName;
        }
        sortAggregates(aggregates);

        ensureSheetsExist(settings.rankingSpreadsheetId, accessToken);
        rewriteDailyDatabase(settings.rankingSpreadsheetId, snapshotDate, capturedAt, aggregates, accessToken);
        rewriteTodayRankView(settings.rankingSpreadsheetId, snapshotDate, aggregates, accessToken);
        appendSyncLog(settings.rankingSpreadsheetId, capturedAt, snapshotDate, aggregates.size(), accessToken);

        return new SyncResult(snapshotDate, snapshot.sourceRowCount, aggregates.size(), capturedAt);
    }

    private Map<String, String> loadDisplayNameByCode(SettingsStore.SheetSettings settings, String accessToken) throws IOException {
        String responseBody = requestSheetValues(settings.spreadsheetId, settings.range, accessToken);
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONArray values = root.optJSONArray("values");
            if (values == null || values.length() == 0) {
                return Collections.emptyMap();
            }

            int rangeStartColumn = getRangeStartColumn(settings.range);
            int productCodeIndex = getRelativeColumnIndex(settings.productCodeColumn, rangeStartColumn);
            int productNameIndex = getRelativeColumnIndex(settings.productNameColumn, rangeStartColumn);
            int orderCodeIndex = getRelativeColumnIndex(settings.orderCodeColumn, rangeStartColumn);

            Map<String, String> displayNameByCode = new HashMap<>();
            for (int i = 0; i < values.length(); i++) {
                JSONArray row = values.optJSONArray(i);
                if (row == null) {
                    continue;
                }

                String productCode = getCell(row, productCodeIndex);
                if (TextUtils.isEmpty(productCode)) {
                    continue;
                }

                String displayName = getCell(row, productNameIndex);
                if (TextUtils.isEmpty(displayName)) {
                    displayName = getCell(row, orderCodeIndex);
                }
                if (TextUtils.isEmpty(displayName)) {
                    displayName = productCode;
                }
                displayNameByCode.put(normalizeKey(productCode), displayName);
            }
            return displayNameByCode;
        } catch (JSONException exception) {
            throw new IOException("상품정보 시트 응답 형식을 해석하지 못했습니다.", exception);
        }
    }

    private SourceSalesSnapshot loadSalesSnapshot(SettingsStore.SheetSettings settings, String accessToken) throws IOException {
        String responseBody = requestSheetValues(settings.salesSpreadsheetId, settings.salesRange, accessToken);
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONArray values = root.optJSONArray("values");
            if (values == null || values.length() == 0) {
                return new SourceSalesSnapshot(0, Collections.emptyMap());
            }

            int rangeStartColumn = getRangeStartColumn(settings.salesRange);
            int productCodeIndex = getRelativeColumnIndex(settings.salesProductCodeColumn, rangeStartColumn);
            int salesQuantityIndex = getRelativeColumnIndex(settings.salesQuantityColumn, rangeStartColumn);

            int sourceRowCount = 0;
            Map<String, SalesAggregate> aggregateByCode = new HashMap<>();
            for (int i = 0; i < values.length(); i++) {
                JSONArray row = values.optJSONArray(i);
                if (row == null) {
                    continue;
                }

                String productCode = getCell(row, productCodeIndex);
                if (TextUtils.isEmpty(productCode)) {
                    continue;
                }
                sourceRowCount += 1;

                String key = normalizeKey(productCode);
                SalesAggregate aggregate = aggregateByCode.get(key);
                if (aggregate == null) {
                    aggregate = new SalesAggregate(productCode);
                    aggregateByCode.put(key, aggregate);
                }
                aggregate.salesCount += 1;

                BigDecimal quantity = parseQuantity(getCell(row, salesQuantityIndex));
                if (quantity != null) {
                    aggregate.salesQuantitySum = aggregate.salesQuantitySum.add(quantity);
                }
            }
            return new SourceSalesSnapshot(sourceRowCount, aggregateByCode);
        } catch (JSONException exception) {
            throw new IOException("오늘 판매 시트 응답 형식을 해석하지 못했습니다.", exception);
        }
    }

    private void ensureSheetsExist(String spreadsheetId, String accessToken) throws IOException {
        String metadataUrl = "https://sheets.googleapis.com/v4/spreadsheets/"
                + Uri.encode(spreadsheetId)
                + "?fields=sheets.properties.title";
        JSONObject metadata = requestJson("GET", metadataUrl, accessToken, null);

        Set<String> existingTitles = new HashSet<>();
        JSONArray sheets = metadata.optJSONArray("sheets");
        if (sheets != null) {
            for (int i = 0; i < sheets.length(); i++) {
                JSONObject sheet = sheets.optJSONObject(i);
                if (sheet == null) {
                    continue;
                }
                JSONObject properties = sheet.optJSONObject("properties");
                if (properties == null) {
                    continue;
                }
                String title = properties.optString("title", "").trim();
                if (!TextUtils.isEmpty(title)) {
                    existingTitles.add(title);
                }
            }
        }

        JSONArray requests = new JSONArray();
        addMissingSheetRequest(existingTitles, requests, DAILY_DB_SHEET);
        addMissingSheetRequest(existingTitles, requests, TODAY_RANK_VIEW_SHEET);
        addMissingSheetRequest(existingTitles, requests, SYNC_LOG_SHEET);
        if (requests.length() == 0) {
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("requests", requests);
        } catch (JSONException exception) {
            throw new IOException("시트 생성 요청을 만들지 못했습니다.", exception);
        }

        String batchUrl = "https://sheets.googleapis.com/v4/spreadsheets/"
                + Uri.encode(spreadsheetId)
                + ":batchUpdate";
        requestJson("POST", batchUrl, accessToken, body.toString());
    }

    private void addMissingSheetRequest(Set<String> existingTitles, JSONArray requests, String title) throws IOException {
        if (existingTitles.contains(title)) {
            return;
        }
        try {
            requests.put(new JSONObject()
                    .put("addSheet", new JSONObject()
                            .put("properties", new JSONObject().put("title", title))));
        } catch (JSONException exception) {
            throw new IOException("시트 생성 요청을 만들지 못했습니다.", exception);
        }
    }

    private void rewriteDailyDatabase(String spreadsheetId, String snapshotDate, String capturedAt, List<SalesAggregate> aggregates, String accessToken) throws IOException {
        List<List<String>> existingRows = readValues(spreadsheetId, DAILY_DB_SHEET + "!A:F", accessToken);
        List<List<String>> rowsToWrite = new ArrayList<>();
        rowsToWrite.add(rowOf("snapshot_date", "product_code", "display_name", "sales_count", "sales_qty_sum", "captured_at"));

        for (int i = 1; i < existingRows.size(); i++) {
            List<String> row = normalizeRow(existingRows.get(i), 6);
            if (snapshotDate.equals(getCell(row, 0))) {
                continue;
            }
            rowsToWrite.add(row);
        }

        for (SalesAggregate aggregate : aggregates) {
            rowsToWrite.add(rowOf(
                    snapshotDate,
                    aggregate.productCode,
                    aggregate.displayName,
                    String.valueOf(aggregate.salesCount),
                    formatQuantity(aggregate.salesQuantitySum),
                    capturedAt
            ));
        }

        clearValues(spreadsheetId, DAILY_DB_SHEET + "!A:F", accessToken);
        updateValues(spreadsheetId, DAILY_DB_SHEET + "!A1:F", rowsToWrite, accessToken);
    }

    private void rewriteTodayRankView(String spreadsheetId, String snapshotDate, List<SalesAggregate> aggregates, String accessToken) throws IOException {
        List<List<String>> rowsToWrite = new ArrayList<>();
        rowsToWrite.add(rowOf("rank", "snapshot_date", "product_code", "display_name", "sales_count", "sales_qty_sum"));

        for (int i = 0; i < aggregates.size(); i++) {
            SalesAggregate aggregate = aggregates.get(i);
            rowsToWrite.add(rowOf(
                    String.valueOf(i + 1),
                    snapshotDate,
                    aggregate.productCode,
                    aggregate.displayName,
                    String.valueOf(aggregate.salesCount),
                    formatQuantity(aggregate.salesQuantitySum)
            ));
        }

        clearValues(spreadsheetId, TODAY_RANK_VIEW_SHEET + "!A:F", accessToken);
        updateValues(spreadsheetId, TODAY_RANK_VIEW_SHEET + "!A1:F", rowsToWrite, accessToken);
    }

    private void appendSyncLog(String spreadsheetId, String runAt, String targetDate, int writtenRows, String accessToken) throws IOException {
        List<List<String>> existingRows = readValues(spreadsheetId, SYNC_LOG_SHEET + "!A:E", accessToken);
        if (existingRows.isEmpty()) {
            updateValues(spreadsheetId, SYNC_LOG_SHEET + "!A1:E", Collections.singletonList(
                    rowOf("run_at", "target_date", "written_rows", "status", "message")
            ), accessToken);
        }

        appendValues(spreadsheetId, SYNC_LOG_SHEET + "!A:E", Collections.singletonList(
                rowOf(runAt, targetDate, String.valueOf(writtenRows), "success", "manual sync")
        ), accessToken);
    }

    private List<List<String>> readValues(String spreadsheetId, String range, String accessToken) throws IOException {
        String body = requestSheetValues(spreadsheetId, range, accessToken);
        try {
            JSONObject root = new JSONObject(body);
            JSONArray values = root.optJSONArray("values");
            if (values == null || values.length() == 0) {
                return Collections.emptyList();
            }

            List<List<String>> rows = new ArrayList<>();
            for (int i = 0; i < values.length(); i++) {
                JSONArray rowArray = values.optJSONArray(i);
                List<String> row = new ArrayList<>();
                if (rowArray != null) {
                    for (int j = 0; j < rowArray.length(); j++) {
                        row.add(rowArray.optString(j, "").trim());
                    }
                }
                rows.add(row);
            }
            return rows;
        } catch (JSONException exception) {
            throw new IOException("스프레드시트 값을 해석하지 못했습니다.", exception);
        }
    }

    private String requestSheetValues(String spreadsheetId, String range, String accessToken) throws IOException {
        String url = "https://sheets.googleapis.com/v4/spreadsheets/"
                + Uri.encode(spreadsheetId)
                + "/values/"
                + Uri.encode(range)
                + "?majorDimension=ROWS";
        JSONObject root = requestJson("GET", url, accessToken, null);
        return root.toString();
    }

    private void clearValues(String spreadsheetId, String range, String accessToken) throws IOException {
        String url = "https://sheets.googleapis.com/v4/spreadsheets/"
                + Uri.encode(spreadsheetId)
                + "/values/"
                + Uri.encode(range)
                + ":clear";
        requestJson("POST", url, accessToken, "{}");
    }

    private void updateValues(String spreadsheetId, String range, List<List<String>> rows, String accessToken) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("range", range);
            body.put("majorDimension", "ROWS");
            JSONArray values = new JSONArray();
            for (List<String> row : rows) {
                JSONArray rowArray = new JSONArray();
                for (String value : row) {
                    rowArray.put(value == null ? "" : value);
                }
                values.put(rowArray);
            }
            body.put("values", values);
        } catch (JSONException exception) {
            throw new IOException("스프레드시트 저장 값을 만들지 못했습니다.", exception);
        }

        String url = "https://sheets.googleapis.com/v4/spreadsheets/"
                + Uri.encode(spreadsheetId)
                + "/values/"
                + Uri.encode(range)
                + "?valueInputOption=RAW";
        requestJson("PUT", url, accessToken, body.toString());
    }

    private void appendValues(String spreadsheetId, String range, List<List<String>> rows, String accessToken) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("majorDimension", "ROWS");
            JSONArray values = new JSONArray();
            for (List<String> row : rows) {
                JSONArray rowArray = new JSONArray();
                for (String value : row) {
                    rowArray.put(value == null ? "" : value);
                }
                values.put(rowArray);
            }
            body.put("values", values);
        } catch (JSONException exception) {
            throw new IOException("스프레드시트 추가 값을 만들지 못했습니다.", exception);
        }

        String url = "https://sheets.googleapis.com/v4/spreadsheets/"
                + Uri.encode(spreadsheetId)
                + "/values/"
                + Uri.encode(range)
                + ":append?valueInputOption=RAW&insertDataOption=INSERT_ROWS";
        requestJson("POST", url, accessToken, body.toString());
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

    private void sortAggregates(List<SalesAggregate> aggregates) {
        aggregates.sort(Comparator
                .comparingInt(SalesAggregate::getSalesCount).reversed()
                .thenComparing(SalesAggregate::getSalesQuantitySum, Comparator.reverseOrder())
                .thenComparing(SalesAggregate::getProductCode));
    }

    private String getCell(JSONArray row, int relativeIndex) {
        if (relativeIndex < 0 || relativeIndex >= row.length()) {
            return "";
        }
        return row.optString(relativeIndex, "").trim();
    }

    private String getCell(List<String> row, int index) {
        if (row == null || index < 0 || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value.trim();
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
            throw new IOException("조회 범위를 해석하지 못했습니다.");
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

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal parseQuantity(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatQuantity(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
    }

    private List<String> rowOf(String... values) {
        List<String> row = new ArrayList<>();
        if (values == null) {
            return row;
        }
        Collections.addAll(row, values);
        return row;
    }

    private List<String> normalizeRow(List<String> row, int size) {
        List<String> normalized = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            normalized.add(getCell(row, i));
        }
        return normalized;
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
            return "시트 요청 형식을 확인하세요. " + detail;
        }
        if (code == 401) {
            return "Google 연결이 만료되었거나 승인되지 않았습니다. 다시 연결해 주세요. " + detail;
        }
        if (code == 403) {
            return "판매량 DB 시트 쓰기 권한이 없습니다. " + detail;
        }
        if (code == 404) {
            return "판매량 DB 스프레드시트를 찾지 못했습니다. " + detail;
        }
        return "판매량 DB 동기화에 실패했습니다. HTTP " + code + ". " + detail;
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

    public static final class SyncResult {
        private final String snapshotDate;
        private final int sourceRowCount;
        private final int groupedProductCount;
        private final String capturedAt;

        private SyncResult(String snapshotDate, int sourceRowCount, int groupedProductCount, String capturedAt) {
            this.snapshotDate = snapshotDate;
            this.sourceRowCount = sourceRowCount;
            this.groupedProductCount = groupedProductCount;
            this.capturedAt = capturedAt;
        }

        public String getSnapshotDate() {
            return snapshotDate;
        }

        public int getSourceRowCount() {
            return sourceRowCount;
        }

        public int getGroupedProductCount() {
            return groupedProductCount;
        }

        public String getCapturedAt() {
            return capturedAt;
        }
    }

    private static final class SourceSalesSnapshot {
        private final int sourceRowCount;
        private final Map<String, SalesAggregate> aggregateByCode;

        private SourceSalesSnapshot(int sourceRowCount, Map<String, SalesAggregate> aggregateByCode) {
            this.sourceRowCount = sourceRowCount;
            this.aggregateByCode = aggregateByCode;
        }
    }

    private static final class SalesAggregate {
        private final String productCode;
        private String displayName;
        private int salesCount;
        private BigDecimal salesQuantitySum = BigDecimal.ZERO;

        private SalesAggregate(String productCode) {
            this.productCode = productCode == null ? "" : productCode.trim();
        }

        private int getSalesCount() {
            return salesCount;
        }

        private BigDecimal getSalesQuantitySum() {
            return salesQuantitySum;
        }

        private String getProductCode() {
            return productCode;
        }
    }
}