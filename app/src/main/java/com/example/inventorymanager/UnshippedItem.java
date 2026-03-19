package com.example.inventorymanager;

public class UnshippedItem {
    private final int sheetRowNumber;
    private final String vendor;
    private final String orderCode;
    private final String recipientName;
    private final String recipientPhone;
    private final String reason;
    private final String supplyMemo;
    private final String result;
    private String sellerFeedback;
    private boolean customInputVisible;
    private String customInputText;
    private boolean recipientNameVisible;
    private boolean recipientPhoneVisible;

    public UnshippedItem(
            int sheetRowNumber,
            String vendor,
            String orderCode,
            String recipientName,
            String recipientPhone,
            String reason,
            String supplyMemo,
            String result,
            String sellerFeedback
    ) {
        this.sheetRowNumber = sheetRowNumber;
        this.vendor = safe(vendor);
        this.orderCode = safe(orderCode);
        this.recipientName = safe(recipientName);
        this.recipientPhone = safe(recipientPhone);
        this.reason = safe(reason);
        this.supplyMemo = safe(supplyMemo);
        this.result = safe(result);
        this.sellerFeedback = safe(sellerFeedback);
        this.customInputText = this.sellerFeedback;
    }

    public int getSheetRowNumber() {
        return sheetRowNumber;
    }

    public String getVendor() {
        return vendor;
    }

    public String getOrderCode() {
        return orderCode;
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
