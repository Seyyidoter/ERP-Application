package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

/** Ürün sipariş geçmişi sorguları – tarih filtrelerinde CAST YOK (indeks-dostu). */
public class ProductHistoryDAO {

    public static ObservableList<ProductHistoryRow> findHistoryForProduct(
            int productId,
            LocalDate from, LocalDate to,
            String statusLike,
            String customerLike
    ) throws SQLException {

        // null/boş-string normalize
        statusLike   = norm(statusLike);
        customerLike = norm(customerLike);

        StringBuilder sb = new StringBuilder("""
            SELECT
                t.Id                                  AS ReqId,
                CAST(t.TalepTarihi AS date)           AS Tarih,   -- SELECT tarafındaki CAST yalnız görsellik için
                t.Durum                               AS Durum,
                m.FirmaAdi                            AS Musteri,
                k.Miktar                              AS Miktar,
                k.TeklifFiyati                        AS TeklifFiyati
            FROM dbo.Talepler t
            JOIN dbo.TalepKalemleri k ON k.TalepId  = t.Id
            JOIN dbo.Musteriler   m ON m.Id         = t.MusteriId
            WHERE k.UrunId = ?
        """);

        // --- Tarih aralığı: indeks-dostu (>= from 00:00, < to+1 00:00) ---
        if (from != null) sb.append(" AND t.TalepTarihi >= ? ");
        if (to   != null) sb.append(" AND t.TalepTarihi <  ? ");

        if (statusLike   != null) sb.append(" AND t.Durum = ? ");
        if (customerLike != null) sb.append(" AND m.FirmaAdi LIKE ? ");

        sb.append(" ORDER BY t.Id DESC, k.Id ");

        ObservableList<ProductHistoryRow> rows = FXCollections.observableArrayList();

        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {

            int i = 1;
            ps.setInt(i++, productId);

            if (from != null) {
                ps.setTimestamp(i++, Timestamp.valueOf(from.atStartOfDay()));
            }
            if (to != null) {
                LocalDate next = to.plusDays(1);
                ps.setTimestamp(i++, Timestamp.valueOf(next.atStartOfDay()));
            }

            if (statusLike != null) {
                ps.setString(i++, statusLike);
            }
            if (customerLike != null) {
                ps.setString(i++, "%" + customerLike + "%");
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int reqId       = rs.getInt("ReqId");
                    Date d          = rs.getDate("Tarih");
                    LocalDate date  = (d == null ? null : d.toLocalDate());
                    String status   = rs.getString("Durum");
                    String customer = rs.getString("Musteri");
                    int qty         = rs.getInt("Miktar");

                    // Parasal alanları 2 ondalığa normalize et
                    BigDecimal unit = rs.getBigDecimal("TeklifFiyati");
                    if (unit == null) unit = BigDecimal.ZERO;
                    else unit = Money.scale2(unit);

                    BigDecimal subtotal = Money.scale2(unit.multiply(BigDecimal.valueOf(qty)));

                    rows.add(new ProductHistoryRow(
                            reqId, date, status, customer, qty, unit, subtotal
                    ));
                }
            }
        }
        return rows;
    }

    /** null-safe trim; boşsa null döndür. */
    private static String norm(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
