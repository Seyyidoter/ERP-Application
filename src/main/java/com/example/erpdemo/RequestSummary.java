package com.example.erpdemo;

import java.time.LocalDate;

/** Liste ekranı için özet satır modeli (JOIN ile müşteri adı dahil). */
public class RequestSummary {
    private final int id;
    private final int customerId;
    private final String customerName;
    private final LocalDate requestDate;
    private final String status;

    public RequestSummary(int id, int customerId, String customerName,
                          LocalDate requestDate, String status) {
        this.id = id;
        this.customerId = customerId;
        this.customerName = customerName;
        this.requestDate = requestDate;
        this.status = status;
    }

    public int getId() { return id; }
    public int getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public LocalDate getRequestDate() { return requestDate; }
    public String getStatus() { return status; }
}
