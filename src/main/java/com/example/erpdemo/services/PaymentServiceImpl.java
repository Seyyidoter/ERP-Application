package com.example.erpdemo.services;

import com.example.erpdemo.util.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class PaymentServiceImpl implements PaymentService {
    @Override
    public void takePayment(int customerId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Tutar pozitif olmalı.");
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE dbo.Musteriler SET Bakiye = Bakiye - ? WHERE Id = ?")) {
            ps.setBigDecimal(1, amount);
            ps.setInt(2, customerId);
            int ok = ps.executeUpdate();
            if (ok != 1) throw new RuntimeException("Müşteri bulunamadı.");
        } catch (Exception e) {
            throw new RuntimeException("Ödeme alınamadı", e);
        }
    }
}
