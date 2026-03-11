package com.example.inventorymanager;

public class InventoryItem {
    private final String productCode;
    private final String orderCode;
    private final String productName;
    private final String stockQuantity;
    private final String matchReason;
    private final int score;

    public InventoryItem(
            String productCode,
            String orderCode,
            String productName,
            String stockQuantity,
            String matchReason,
            int score
    ) {
        this.productCode = productCode;
        this.orderCode = orderCode;
        this.productName = productName;
        this.stockQuantity = stockQuantity;
        this.matchReason = matchReason;
        this.score = score;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public String getProductName() {
        return productName;
    }

    public String getStockQuantity() {
        return stockQuantity;
    }

    public String getMatchReason() {
        return matchReason;
    }

    public int getScore() {
        return score;
    }
}
