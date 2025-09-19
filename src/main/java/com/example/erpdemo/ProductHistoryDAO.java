package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.time.LocalDate;

public class ProductHistoryDAO {

    /** Ürün geçmişi – tarih aralığı, durum, müşteri (LIKE) filtreleriyle */
    public static ObservableList<ProductHistoryRow> getProductHistory(
            int productId,
            LocalDate fromDate,
            LocalDate toDate,
            String status,          // "" => hepsi
            String customerLike     // "" => filtre yok (LIKE %xxx%)
    ) throws SQLException {

        String sql = """
            SELECT
                t.Id AS RequestId,
                CAST(t.TalepTarihi AS date) AS TalepTarihi,
                t.Durum AS Durum,
                m.FirmaAdi AS CustomerName,
                k.Miktar AS Qty,
                k.TeklifFiyati AS UnitPrice
            FROM dbo.Talepler t
            JOIN dbo.TalepKalemleri k ON k.TalepId = t.Id
            JOIN dbo.Musteriler m     ON m.Id = t.MusteriId
            WHERE k.UrunId = ?
              AND (? IS NULL OR CAST(t.TalepTarihi AS date) >= ?)
              AND (? IS NULL OR CAST(t.TalepTarihi AS date) <= ?)
              AND (? = '' OR t.Durum = ?)
              AND (? = '' OR m.FirmaAdi LIKE ?)
            ORDER BY t.TalepTarihi DESC, t.Id DESC, k.Id
        """;

        ObservableList<ProductHistoryRow> list = FXCollections.observableArrayList();

        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int i = 1;
            ps.setInt(i++, productId);

            // fromDate
            if (fromDate == null) { ps.setNull(i++, Types.DATE); ps.setNull(i++, Types.DATE); }
            else {
                Date d = Date.valueOf(fromDate);
                ps.setDate(i++, d); ps.setDate(i++, d);
            }

            // toDate
            if (toDate == null) { ps.setNull(i++, Types.DATE); ps.setNull(i++, Types.DATE); }
            else {
                Date d = Date.valueOf(toDate);
                ps.setDate(i++, d); ps.setDate(i++, d);
            }

            // status
            ps.setString(i++, status == null ? "" : status);
            ps.setString(i++, status == null ? "" : status);

            // customer like
            String like = (customerLike == null || customerLike.isBlank()) ? "" : "%" + customerLike + "%";
            ps.setString(i++, like.isEmpty() ? "" : like);
            ps.setString(i,   like.isEmpty() ? "" : like);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int reqId = rs.getInt("RequestId");
                    Date d    = rs.getDate("TalepTarihi");
                    LocalDate date = (d != null ? d.toLocalDate() : null);
                    String s  = rs.getString("Durum");
                    String customer = rs.getString("CustomerName");
                    int qty = rs.getInt("Qty");
                    double unit = rs.getDouble("UnitPrice");
                    list.add(new ProductHistoryRow(reqId, date, s, customer, qty, unit));
                }
            }
        }
        return list;
    }
}
