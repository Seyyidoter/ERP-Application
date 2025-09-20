package com.example.erpdemo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;

/**
 * Tahsilat: ödeme kaydını ekler ve AYNI transaction içinde bakiyeyi ARTIRIR.
 * amount > 0 olmalı. note null olabilir.
 *
 * Not: Para tutarları için double yerine BigDecimal kullanıyoruz (kayan nokta hatalarını önlemek için).
 */
public class PaymentDAO {

    /**
     * BigDecimal tabanlı güvenli ödeme ekleme.
     */
    public static void addPayment(int customerId, BigDecimal amount, String note) throws SQLException {
        // ---- Giriş kontrolleri ----
        if (amount == null) {
            throw new IllegalArgumentException("Ödeme tutarı boş olamaz.");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Ödeme tutarı 0'dan büyük olmalı.");
        }

        // Java 9+ : RoundingMode kullan (deprecated integer sabitler yerine)
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        final String insertSql =
                "INSERT INTO dbo.Odemeler (MusteriId, Tutar, Aciklama) VALUES (?, ?, ?)";
        final String updateBal =
                "UPDATE dbo.Musteriler SET Bakiye = Bakiye + ? WHERE Id = ?";

        try (Connection c = DatabaseManager.getConnection()) {
            final boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false); // ---- Transaction başlat ----

            try (PreparedStatement ins = c.prepareStatement(insertSql);
                 PreparedStatement up  = c.prepareStatement(updateBal)) {

                // INSERT Odemeler
                ins.setInt(1, customerId);
                ins.setBigDecimal(2, amount);
                if (note == null || note.isBlank()) {
                    ins.setNull(3, Types.NVARCHAR); // SQL Server NVARCHAR varsayımı
                } else {
                    ins.setString(3, note);
                }
                int insAffected = ins.executeUpdate();
                if (insAffected != 1) {
                    throw new SQLException("Ödeme kaydı eklenemedi (etkilenen satır: " + insAffected + ").");
                }

                // UPDATE Musteriler (bakiye artar → borç azalır)
                up.setBigDecimal(1, amount);
                up.setInt(2, customerId);
                int updAffected = up.executeUpdate();
                if (updAffected != 1) {
                    throw new SQLException("Müşteri bakiyesi güncellenemedi (Id=" + customerId + ").");
                }

                c.commit(); // ---- Transaction commit ----

            } catch (SQLException ex) {
                // ---- Transaction rollback ----
                try { c.rollback(); } catch (SQLException ignore) { /* loglanabilir */ }
                throw ex;
            } finally {
                // ---- Transaction modunu eski haline getir ----
                try { c.setAutoCommit(oldAutoCommit); } catch (SQLException ignore) { /* loglanabilir */ }
            }
        }
    }

    /**
     * double tabanlı çağrılar için kolaylık overload’ı.
     * İçeride güvenli BigDecimal'a çevirir.
     */
    public static void addPayment(int customerId, double amount, String note) throws SQLException {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Ödeme tutarı geçerli bir sayı olmalı.");
        }
        addPayment(customerId, BigDecimal.valueOf(amount), note);
    }
}
