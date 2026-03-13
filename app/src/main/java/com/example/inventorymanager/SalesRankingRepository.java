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
import java.util.Map;

public class SalesRankingRepository {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final String RANKING_RANGE = "daily_sales_db!A2:F";

    public List<SalesRankingItem> loadByDate(String rankingSpreadsheetId, String targetDate, int limit, String accessToken) throws IOException {
        List<RankingRow> rows = loadRows(rankingSpreadsheetId, accessToken);
        if (TextUtils.isEmpty(targetDate)) {
            return Collections.emptyList();
        }

        List<RankingRow> filtered = new ArrayList<>();
        for (RankingRow row : rows) {
            if (targetDate.equals(row.snapshotDate)) {
                filtered.add(row);
            }
        }
        sortRows(filtered);
        return toItems(filtered, limit);
    }

    public List<SalesRankingItem> loadByMonth(String rankingSpreadsheetId, String targetMonth, int limit, String accessToken) throws IOException {
        List<RankingRow> rows = loadRows(rankingSpreadsheetId, accessToken);
        if (TextUtils.isEmpty(targetMonth)) {
            return Collections.emptyList();
        }

        Map<String, RankingAggregate> aggregateByCode = new HashMap<>();
        for (RankingRow row : rows) {
            if (!row.snapshotDate.startsWith(targetMonth + "-")) {
                continue;
            }

            RankingAggregate aggregate = aggregateByCode.get(row.productCode);
            if (aggregate == null) {
                aggregate = new RankingAggregate(row.productCode, row.displayName);
                aggregateByCode.put(row.productCode, aggregate);
            }
            aggregate.displayName = TextUtils.isEmpty(aggregate.displayName) ? row.displayName : aggregate.displayName;
            aggregate.salesCount += row.salesCount;
            aggregate.salesQuantitySum = aggregate.salesQuantitySum.add(row.salesQuantitySum);
        }

        List<RankingRow> aggregatedRows = new ArrayList<>();
        for (RankingAggregate aggregate : aggregateByCode.values()) {
            aggregatedRows.add(new RankingRow(
                    targetMonth,
                    aggregate.productCode,
                    aggregate.displayName,
                    aggregate.salesCount,
                    aggregate.salesQuantitySum
            ));
        }
        sortRows(aggregatedRows);
        return toItems(aggregatedRows, limit);
    }

    private List<RankingRow> loadRows(String rankingSpreadsheetId, String accessToken) throws IOException {
        if (TextUtils.isEmpty(accessToken)) {
            throw new IOException("Google 연결이 완료되지 않았습니다.");
        }
        if (TextUtils.isEmpty(rankingSpreadsheetId)) {
            throw new IOException("판매량 DB 스프레드시트 ID가 비어 있습니다. 옵션에서 먼저 입력해 주세요.");
        }

        String responseBody = requestSheetValues(rankingSpreadsheetId, accessToken);
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONArray values = root.optJSONArray("values");
            if (values == null || values.length() == 0) {
                return Collections.emptyList();
            }

            List<RankingRow> rows = new ArrayList<>();
            for (int i = 0; i < values.length(); i++) {
                JSONArray row = values.optJSONArray(i);
                if (row == null) {
                    continue;
                }

                String snapshotDate = getCell(row, 0);
                String productCode = getCell(row, 1);
                String displayName = getCell(row, 2);
                int salesCount = parseInt(getCell(row, 3));
                BigDecimal salesQuantitySum = parseDecimal(getCell(row, 4));

                if (TextUtils.isEmpty(snapshotDate) || TextUtils.isEmpty(productCode)) {
                    continue;
                }
                rows.add(new RankingRow(snapshotDate, productCode, displayName, salesCount, salesQuantitySum));
            }
            return rows;
        } catch (JSONException exception) {
            throw new IOException("판매량 DB 응답 형식을 해석하지 못했습니다.", exception);
        }
    }

    private String requestSheetValues(String rankingSpreadsheetId, String accessToken) throws IOException {
        String url = "https://sheets.googleapis.com/v4/spreadsheets/"
                + Uri.encode(rankingSpreadsheetId)
                + "/values/"
                + Uri.encode(RANKING_RANGE)
                + "?majorDimension=ROWS";

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
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

    private void sortRows(List<RankingRow> rows) {
        rows.sort(Comparator
                .comparingInt(RankingRow::getSalesCount).reversed()
                .thenComparing(RankingRow::getSalesQuantitySum, Comparator.reverseOrder())
                .thenComparing(RankingRow::getProductCode));
    }

    private List<SalesRankingItem> toItems(List<RankingRow> rows, int limit) {
        List<SalesRankingItem> items = new ArrayList<>();
        int size = Math.min(limit, rows.size());
        for (int i = 0; i < size; i++) {
            RankingRow row = rows.get(i);
            items.add(new SalesRankingItem(
                    i + 1,
                    row.productCode,
                    TextUtils.isEmpty(row.displayName) ? row.productCode : row.displayName,
                    row.salesCount,
                    formatDecimal(row.salesQuantitySum)
            ));
        }
        return items;
    }

    private String getCell(JSONArray row, int index) {
        if (index < 0 || index >= row.length()) {
            return "";
        }
        return row.optString(index, "").trim();
    }

    private int parseInt(String value) {
        if (TextUtils.isEmpty(value)) {
            return 0;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim()).intValue();
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (TextUtils.isEmpty(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String formatDecimal(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
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
        if (code == 401) {
            return "Google 연결이 만료되었거나 승인되지 않았습니다. 다시 연결해 주세요.";
        }
        if (code == 403) {
            return "판매량 DB 시트 접근 권한이 없습니다.";
        }
        if (code == 404) {
            return "판매량 DB 시트를 찾지 못했습니다.";
        }
        return "판매량 DB 조회에 실패했습니다. HTTP " + code + ". " + body;
    }

    private static final class RankingRow {
        private final String snapshotDate;
        private final String productCode;
        private final String displayName;
        private final int salesCount;
        private final BigDecimal salesQuantitySum;

        private RankingRow(String snapshotDate, String productCode, String displayName, int salesCount, BigDecimal salesQuantitySum) {
            this.snapshotDate = snapshotDate;
            this.productCode = productCode;
            this.displayName = displayName;
            this.salesCount = salesCount;
            this.salesQuantitySum = salesQuantitySum == null ? BigDecimal.ZERO : salesQuantitySum;
        }

        private int getSalesCount() {
            return salesCount;
        }

        private BigDecimal getSalesQuantitySum() {
            return salesQuantitySum;
        }

        private String getProductCode() {
            return productCode == null ? "" : productCode;
        }
    }

    private static final class RankingAggregate {
        private final String productCode;
        private String displayName;
        private int salesCount;
        private BigDecimal salesQuantitySum = BigDecimal.ZERO;

        private RankingAggregate(String productCode, String displayName) {
            this.productCode = productCode;
            this.displayName = displayName;
        }
    }
}