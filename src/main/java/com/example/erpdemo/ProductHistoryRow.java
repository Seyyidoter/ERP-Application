package com.example.erpdemo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ürün sipariş geçmişi satırı (FXML: colReqId, colDate, colStatus, colCustomer, colQty, colUnit, colSubtotal). */
public class ProductHistoryRow {
    private final int requestId;
    private final LocalDate date;
    private final String status;
    private final String customer;
    private final int qty;
    private final BigDecimal unit;
    private final BigDecimal subtotal;

    public ProductHistoryRow(int requestId, LocalDate date, String status,
                             String customer, int qty, BigDecimal unit, BigDecimal subtotal) {
        this.requestId = requestId;
        this.date = date;
        this.status = status;
        this.customer = customer;
        this.qty = qty;
        this.unit = unit == null ? BigDecimal.ZERO : unit;
        this.subtotal = subtotal == null ? BigDecimal.ZERO : subtotal;
    }

    public int getColReqId() { return requestId; }
    public int getRequestId() { return requestId; }

    public LocalDate getColDate() { return date; }
    public LocalDate getDate() { return date; }

    public String getColStatus() { return status; }
    public String getStatus() { return status; }

    public String getColCustomer() { return customer; }
    public String getCustomer() { return customer; }

    public int getColQty() { return qty; }
    public int getQty() { return qty; }

    public BigDecimal getColUnit() { return unit; }
    public BigDecimal getUnit() { return unit; }

    public BigDecimal getColSubtotal() { return subtotal; }
    public BigDecimal getSubtotal() { return subtotal; }
}
