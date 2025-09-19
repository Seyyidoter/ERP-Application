package com.example.erpdemo;

import java.time.LocalDate;

public class ProductHistoryRow {
    private final int requestId;
    private final LocalDate date;
    private final String status;
    private final String customerName;
    private final int quantity;
    private final double unitPrice;

    public ProductHistoryRow(int requestId, LocalDate date, String status,
                             String customerName, int quantity, double unitPrice) {
        this.requestId = requestId;
        this.date = date;
        this.status = status;
        this.customerName = customerName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public int getRequestId() { return requestId; }
    public LocalDate getDate() { return date; }
    public String getStatus() { return status; }
    public String getCustomerName() { return customerName; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getSubtotal() { return unitPrice * quantity; }
}
