package com.example.erpdemo;

import java.time.LocalDate;

public class CustomerHistoryRow {
    private final int requestId;
    private final LocalDate date;
    private final String status;
    private final String productName;
    private final int quantity;
    private final double unitPrice;

    public CustomerHistoryRow(int requestId, LocalDate date, String status,
                              String productName, int quantity, double unitPrice) {
        this.requestId = requestId;
        this.date = date;
        this.status = status;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public int getRequestId() { return requestId; }
    public LocalDate getDate() { return date; }
    public String getStatus() { return status; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getSubtotal() { return unitPrice * quantity; }
}
