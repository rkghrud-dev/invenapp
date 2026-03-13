package com.example.inventorymanager;

public class SalesRankingItem {
    private final int rank;
    private final String productCode;
    private final String displayName;
    private final int salesCount;
    private final String salesQuantitySum;

    public SalesRankingItem(int rank, String productCode, String displayName, int salesCount, String salesQuantitySum) {
        this.rank = rank;
        this.productCode = productCode;
        this.displayName = displayName;
        this.salesCount = salesCount;
        this.salesQuantitySum = salesQuantitySum;
    }

    public int getRank() {
        return rank;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSalesCount() {
        return salesCount;
    }

    public String getSalesQuantitySum() {
        return salesQuantitySum;
    }
}
