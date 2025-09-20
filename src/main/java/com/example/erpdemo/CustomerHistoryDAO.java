package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

/** Müşteri sipariş geçmişi sorguları (BigDecimal uyumlu). */
public class CustomerHistoryDAO {

    public static ObservableList<CustomerHistoryRow> findHistoryForCustomer(
            int customerId,
            LocalDate from, LocalDate to,
            String statusLike,
            String productLike) throws SQLException {

        StringBuilder sb = new StringBuilder("""
            SELECT t.Id AS ReqId,
                   CAST(t.TalepTarihi AS date) AS Tarih,
                   t.Durum,
                   s.UrunAdi,
                   k.Miktar,
                   k.TeklifFiyati
            FROM dbo.Talepler t
            JOIN dbo.TalepKalemleri k ON k.TalepId = t.Id
            JOIN dbo.Stoklar s        ON s.Id = k.UrunId
            WHERE t.MusteriId = ?
        """);

        if (from != null) sb.append(" AND CAST(t.TalepTarihi AS date) >= ? ");
        if (to   != null) sb.append(" AND CAST(t.TalepTarihi AS date) <= ? ");
        if (statusLike != null && !statusLike.isBlank()) sb.append(" AND t.Durum = ? ");
        if (productLike != null && !productLike.isBlank()) sb.append(" AND s.UrunAdi LIKE ? ");
        sb.append(" ORDER BY t.Id DESC, k.Id ");

        ObservableList<CustomerHistoryRow> rows = FXCollections.observableArrayList();

        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {
            int i = 1;
            ps.setInt(i++, customerId);
            if (from != null) ps.setDate(i++, Date.valueOf(from));
            if (to   != null) ps.setDate(i++, Date.valueOf(to));
            if (statusLike != null && !statusLike.isBlank()) ps.setString(i++, statusLike);
            if (productLike != null && !productLike.isBlank()) ps.setString(i++, "%" + productLike + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int reqId = rs.getInt("ReqId");
                    Date d = rs.getDate("Tarih");
                    LocalDate date = d == null ? null : d.toLocalDate();
                    String status = rs.getString("Durum");
                    String product = rs.getString("UrunAdi");
                    int qty = rs.getInt("Miktar");
                    BigDecimal unit = rs.getBigDecimal("TeklifFiyati");
                    BigDecimal subtotal = (unit == null ? BigDecimal.ZERO : unit).multiply(BigDecimal.valueOf(qty));

                    rows.add(new CustomerHistoryRow(reqId, date, status, product, qty, unit, subtotal));
                }
            }
        }
        return rows;
    }
}
