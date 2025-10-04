package com.example.erpdemo.services;
import java.math.BigDecimal;
public interface PaymentService {
    void takePayment(int customerId, BigDecimal amount); // + makbuz no vs. ileride
}
