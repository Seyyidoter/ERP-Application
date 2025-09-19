package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.time.LocalDate;

public class OrderHistoryDAO {

    /** Belirli bir müşterinin tüm talep kalemlerini (geçmiş) döner. */
    public static ObservableList<OrderHistoryRow> getCustomerHistory(int customerId) throws SQLException {
        String sql = """
            SELECT
                t.Id                AS RequestId,
                CAST(t.TalepTarihi AS date) AS TalepTarihi,
                t.Durum             AS Durum,
                s.UrunAdi           AS ProductName,
                k.Miktar            AS Qty,
                k.TeklifFiyati      AS UnitPrice
            FROM dbo.Talepler t
            JOIN dbo.TalepKalemleri k ON k.TalepId = t.Id
            JOIN dbo.Stoklar      s ON s.Id = k.UrunId
            WHERE t.MusteriId = ?
            ORDER BY t.TalepTarihi DESC, t.Id DESC, k.Id
        """;

        ObservableList<OrderHistoryRow> list = FXCollections.observableArrayList();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int reqId = rs.getInt("RequestId");
                    Date d    = rs.getDate("TalepTarihi");
                    LocalDate date = (d != null ? d.toLocalDate() : null);
                    String status = rs.getString("Durum");
                    String product = rs.getString("ProductName");
                    int qty = rs.getInt("Qty");
                    double unit = rs.getDouble("UnitPrice");
                    list.add(new OrderHistoryRow(reqId, date, status, product, qty, unit));
                }
            }
        }
        return list;
    }
}
