package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

/** Ürün sipariş geçmişi sorguları (BigDecimal uyumlu). */
public class ProductHistoryDAO {

    public static ObservableList<ProductHistoryRow> findHistoryForProduct(
            int productId,
            LocalDate from, LocalDate to,
            String statusLike,
            String customerLike) throws SQLException {

        StringBuilder sb = new StringBuilder("""
            SELECT t.Id AS ReqId,
                   CAST(t.TalepTarihi AS date) AS Tarih,
                   t.Durum,
                   m.FirmaAdi AS Musteri,
                   k.Miktar,
                   k.TeklifFiyati
            FROM dbo.Talepler t
            JOIN dbo.TalepKalemleri k ON k.TalepId = t.Id
            JOIN dbo.Musteriler m      ON m.Id = t.MusteriId
            WHERE k.UrunId = ?
        """);

        if (from != null) sb.append(" AND CAST(t.TalepTarihi AS date) >= ? ");
        if (to   != null) sb.append(" AND CAST(t.TalepTarihi AS date) <= ? ");
        if (statusLike != null && !statusLike.isBlank()) sb.append(" AND t.Durum = ? ");
        if (customerLike != null && !customerLike.isBlank()) sb.append(" AND m.FirmaAdi LIKE ? ");
        sb.append(" ORDER BY t.Id DESC, k.Id ");

        ObservableList<ProductHistoryRow> rows = FXCollections.observableArrayList();

        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {

            int i = 1;
            ps.setInt(i++, productId);
            if (from != null) ps.setDate(i++, Date.valueOf(from));
            if (to   != null) ps.setDate(i++, Date.valueOf(to));
            if (statusLike != null && !statusLike.isBlank()) ps.setString(i++, statusLike);
            if (customerLike != null && !customerLike.isBlank()) ps.setString(i++, "%" + customerLike + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int reqId = rs.getInt("ReqId");
                    Date d = rs.getDate("Tarih");
                    LocalDate date = d == null ? null : d.toLocalDate();
                    String status = rs.getString("Durum");
                    String customer = rs.getString("Musteri");
                    int qty = rs.getInt("Miktar");
                    BigDecimal unit = rs.getBigDecimal("TeklifFiyati");
                    BigDecimal subtotal = unit == null ? BigDecimal.ZERO : unit.multiply(BigDecimal.valueOf(qty));

                    rows.add(new ProductHistoryRow(reqId, date, status, customer, qty, unit, subtotal));
                }
            }
        }
        return rows;
    }
}
