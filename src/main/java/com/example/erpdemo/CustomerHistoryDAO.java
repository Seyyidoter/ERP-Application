package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.time.LocalDate;

public class CustomerHistoryDAO {

    /** Müşteri geçmişi – tarih aralığı, durum ve ürün adı (LIKE) filtreleri */
    public static ObservableList<CustomerHistoryRow> getCustomerHistory(
            int customerId,
            LocalDate fromDate,
            LocalDate toDate,
            String status,        // "" => hepsi
            String productLike    // "" => hepsi (LIKE %xxx%)
    ) throws SQLException {

        String sql = """
            SELECT
                t.Id                          AS RequestId,
                CAST(t.TalepTarihi AS date)   AS TalepTarihi,
                t.Durum                       AS Durum,
                s.UrunAdi                     AS ProductName,
                k.Miktar                      AS Qty,
                k.TeklifFiyati                AS UnitPrice
            FROM dbo.Talepler t
            JOIN dbo.TalepKalemleri k ON k.TalepId = t.Id
            JOIN dbo.Stoklar s        ON s.Id       = k.UrunId
            WHERE t.MusteriId = ?
              AND (? IS NULL OR CAST(t.TalepTarihi AS date) >= ?)
              AND (? IS NULL OR CAST(t.TalepTarihi AS date) <= ?)
              AND (? = '' OR t.Durum = ?)
              AND (? = '' OR s.UrunAdi LIKE ?)
            ORDER BY t.TalepTarihi DESC, t.Id DESC, k.Id
        """;

        ObservableList<CustomerHistoryRow> list = FXCollections.observableArrayList();

        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int i = 1;
            ps.setInt(i++, customerId);

            // fromDate
            if (fromDate == null) { ps.setNull(i++, Types.DATE); ps.setNull(i++, Types.DATE); }
            else { Date d = Date.valueOf(fromDate); ps.setDate(i++, d); ps.setDate(i++, d); }

            // toDate
            if (toDate == null) { ps.setNull(i++, Types.DATE); ps.setNull(i++, Types.DATE); }
            else { Date d = Date.valueOf(toDate); ps.setDate(i++, d); ps.setDate(i++, d); }

            // status
            ps.setString(i++, status == null ? "" : status);
            ps.setString(i++, status == null ? "" : status);

            // product like
            String like = (productLike == null || productLike.isBlank()) ? "" : "%" + productLike + "%";
            ps.setString(i++, like.isEmpty() ? "" : like);
            ps.setString(i,   like.isEmpty() ? "" : like);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int reqId = rs.getInt("RequestId");
                    Date d    = rs.getDate("TalepTarihi");
                    LocalDate date = (d != null ? d.toLocalDate() : null);
                    String s  = rs.getString("Durum");
                    String product = rs.getString("ProductName");
                    int qty = rs.getInt("Qty");
                    double unit = rs.getDouble("UnitPrice");
                    list.add(new CustomerHistoryRow(reqId, date, s, product, qty, unit));
                }
            }
        }
        return list;
    }
}
