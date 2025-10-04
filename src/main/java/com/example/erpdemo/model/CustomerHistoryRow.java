package com.example.erpdemo.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Müşteri sipariş geçmişi satırı (FXML: colReqId, colDate, colStatus, colProduct, colQty, colUnit, colSubtotal). */
public class CustomerHistoryRow {
    private final int requestId;
    private final LocalDate date;
    private final String status;
    private final String productName;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal subtotal;

    public CustomerHistoryRow(int requestId, LocalDate date, String status,
                              String productName, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
        this.requestId = requestId;
        this.date = date;
        this.status = status;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        this.subtotal = subtotal == null ? BigDecimal.ZERO : subtotal;
    }

    public int getRequestId() { return requestId; }
    public LocalDate getDate() { return date; }
    public String getStatus() { return status; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getSubtotal() { return subtotal; }
}
