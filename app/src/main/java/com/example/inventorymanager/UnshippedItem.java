package com.example.inventorymanager;

public class UnshippedItem {
    private final int sheetRowNumber;
    private final String dateLabel;
    private final String dateSortKey;
    private final String vendor;
    private final String orderCode;
    private final String productCode;
    private final String orderName;
    private final String skuLocation;
    private final String recipientName;
    private final String recipientPhone;
    private final String reason;
    private final String supplyMemo;
    private final String result;
    private final boolean dateHeader;
    private String sellerFeedback;
    private boolean customInputVisible;
    private String customInputText;
    private boolean recipientNameVisible;
    private boolean recipientPhoneVisible;

    public static UnshippedItem createDateHeader(String dateLabel) {
        return new UnshippedItem(
                -1,
                dateLabel,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                true
        );
    }

    public UnshippedItem(
            int sheetRowNumber,
            String dateLabel,
            String dateSortKey,
            String vendor,
            String orderCode,
            String productCode,
            String orderName,
            String skuLocation,
            String recipientName,
            String recipientPhone,
            String reason,
            String supplyMemo,
            String result,
            String sellerFeedback
    ) {
        this(
                sheetRowNumber,
                dateLabel,
                dateSortKey,
                vendor,
                orderCode,
                productCode,
                orderName,
                skuLocation,
                recipientName,
                recipientPhone,
                reason,
                supplyMemo,
                result,
                sellerFeedback,
                false
        );
    }

    private UnshippedItem(
            int sheetRowNumber,
            String dateLabel,
            String dateSortKey,
            String vendor,
            String orderCode,
            String productCode,
            String orderName,
            String skuLocation,
            String recipientName,
            String recipientPhone,
            String reason,
            String supplyMemo,
            String result,
            String sellerFeedback,
            boolean dateHeader
    ) {
        this.sheetRowNumber = sheetRowNumber;
        this.dateLabel = safe(dateLabel);
        this.dateSortKey = safe(dateSortKey);
        this.vendor = safe(vendor);
        this.orderCode = safe(orderCode);
        this.productCode = safe(productCode);
        this.orderName = safe(orderName);
        this.skuLocation = safe(skuLocation);
        this.recipientName = safe(recipientName);
        this.recipientPhone = safe(recipientPhone);
        this.reason = safe(reason);
        this.supplyMemo = safe(supplyMemo);
        this.result = safe(result);
        this.sellerFeedback = safe(sellerFeedback);
        this.dateHeader = dateHeader;
        this.customInputText = this.sellerFeedback;
    }

    public int getSheetRowNumber() {
        return sheetRowNumber;
    }

    public String getDateLabel() {
        return dateLabel;
    }

    public String getDateSortKey() {
        return dateSortKey;
    }

    public boolean isDateHeader() {
        return dateHeader;
    }

    public String getVendor() {
        return vendor;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getOrderName() {
        return orderName;
    }

    public String getResolvedOrderDisplay() {
        if (!orderName.isEmpty()) {
            return orderName;
        }
        return productCode;
    }

    public String getSkuLocation() {
        return skuLocation;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getReason() {
        return reason;
    }

    public String getSupplyMemo() {
        return supplyMemo;
    }

    public String getResult() {
        return result;
    }

    public String getSellerFeedback() {
        return sellerFeedback;
    }

    public boolean isCustomInputVisible() {
        return customInputVisible;
    }

    public void setCustomInputVisible(boolean customInputVisible) {
        this.customInputVisible = customInputVisible;
    }

    public String getCustomInputText() {
        return customInputText;
    }

    public void setCustomInputText(String customInputText) {
        this.customInputText = safe(customInputText);
    }

    public boolean isRecipientNameVisible() {
        return recipientNameVisible;
    }

    public void setRecipientNameVisible(boolean recipientNameVisible) {
        this.recipientNameVisible = recipientNameVisible;
    }

    public boolean isRecipientPhoneVisible() {
        return recipientPhoneVisible;
    }

    public void setRecipientPhoneVisible(boolean recipientPhoneVisible) {
        this.recipientPhoneVisible = recipientPhoneVisible;
    }

    public void prepareCustomInput(String defaultFeedback) {
        if (sellerFeedback.isEmpty() || sellerFeedback.equals(defaultFeedback)) {
            customInputText = "";
            return;
        }
        customInputText = sellerFeedback;
    }

    public void applySavedFeedback(String feedback) {
        sellerFeedback = safe(feedback);
        customInputText = sellerFeedback;
        customInputVisible = false;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
