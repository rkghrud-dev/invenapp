package com.example.inventorymanager;

public class InventoryItem {
    private final String productCode;
    private final String orderCode;
    private final String productName;
    private final String skuLocation;
    private final String importPrice;
    private final String supplyPrice;
    private final String retailPrice;
    private final String stockQuantity;
    private final String todaySoldQuantity;
    private final String actualStockQuantity;
    private final String matchReason;
    private final int score;

    public InventoryItem(
            String productCode,
            String orderCode,
            String productName,
            String skuLocation,
            String importPrice,
            String supplyPrice,
            String retailPrice,
            String stockQuantity,
            String todaySoldQuantity,
            String actualStockQuantity,
            String matchReason,
            int score
    ) {
        this.productCode = productCode;
        this.orderCode = orderCode;
        this.productName = productName;
        this.skuLocation = skuLocation;
        this.importPrice = importPrice;
        this.supplyPrice = supplyPrice;
        this.retailPrice = retailPrice;
        this.stockQuantity = stockQuantity;
        this.todaySoldQuantity = todaySoldQuantity;
        this.actualStockQuantity = actualStockQuantity;
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

    public String getSkuLocation() {
        return skuLocation;
    }

    public String getImportPrice() {
        return importPrice;
    }

    public String getSupplyPrice() {
        return supplyPrice;
    }

    public String getRetailPrice() {
        return retailPrice;
    }

    public String getStockQuantity() {
        return stockQuantity;
    }

    public String getTodaySoldQuantity() {
        return todaySoldQuantity;
    }

    public String getActualStockQuantity() {
        return actualStockQuantity;
    }

    public String getMatchReason() {
        return matchReason;
    }

    public int getScore() {
        return score;
    }
}
