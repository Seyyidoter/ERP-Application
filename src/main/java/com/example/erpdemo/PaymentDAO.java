package com.example.erpdemo;

import java.sql.*;

public class PaymentDAO {

    /** Tahsilat: ödeme kaydını ekler ve aynı transaction içinde bakiyeyi ARTIRIR. */
    public static void addPayment(int customerId, double amount, String note) throws SQLException {
        if (amount <= 0) throw new IllegalArgumentException("Ödeme tutarı sıfırdan büyük olmalı.");

        String insertSql = "INSERT INTO dbo.Odemeler (MusteriId, Tutar, Aciklama) VALUES (?, ?, ?)";
        String updateBal = "UPDATE dbo.Musteriler SET Bakiye = Bakiye + ? WHERE Id = ?";

        try (Connection c = DatabaseManager.getConnection()) {
            boolean old = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement ins = c.prepareStatement(insertSql);
                 PreparedStatement up  = c.prepareStatement(updateBal)) {

                ins.setInt(1, customerId);
                ins.setDouble(2, amount);
                ins.setString(3, note);
                ins.executeUpdate();

                up.setDouble(1, amount);      // ödeme → bakiye artar (negatif borç azalır)
                up.setInt(2, customerId);
                up.executeUpdate();

                c.commit();
                c.setAutoCommit(old);
            } catch (SQLException ex) {
                c.rollback();
                c.setAutoCommit(true);
                throw ex;
            }
        }
    }
}
