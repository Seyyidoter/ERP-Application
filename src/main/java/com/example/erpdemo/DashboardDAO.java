package com.example.erpdemo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DashboardDAO {

    /** Bugün oluşturulan talep sayısı */
    public static int getTodayRequestCount() throws SQLException {
        String sql = """
            SELECT COUNT(*)
            FROM dbo.Talepler
            WHERE CAST(TalepTarihi AS date) = CAST(GETDATE() AS date)
            """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Bugünkü taleplerdeki toplam ürün adedi (Miktar toplamı) */
    public static int getTodayProductQuantity() throws SQLException {
        String sql = """
            SELECT COALESCE(SUM(tk.Miktar), 0)
            FROM dbo.TalepKalemleri tk
            JOIN dbo.Talepler t ON t.Id = tk.TalepId
            WHERE CAST(t.TalepTarihi AS date) = CAST(GETDATE() AS date)
            """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Bugünkü taleplerin toplam geliri (Miktar * TeklifFiyati) */
    public static double getTodayRevenue() throws SQLException {
        String sql = """
            SELECT COALESCE(SUM(tk.Miktar * tk.TeklifFiyati), 0)
            FROM dbo.TalepKalemleri tk
            JOIN dbo.Talepler t ON t.Id = tk.TalepId
            WHERE CAST(t.TalepTarihi AS date) = CAST(GETDATE() AS date)
            """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    /** Bugün, ürün bazında toplam talep miktarı listesi */
    public static List<ProductDemandStat> getTodayDemandByProduct() throws SQLException {
        String sql = """
            SELECT s.UrunAdi AS ProductName, COALESCE(SUM(tk.Miktar),0) AS TotalQty
            FROM dbo.TalepKalemleri tk
            JOIN dbo.Talepler t ON t.Id = tk.TalepId
            JOIN dbo.Stoklar  s ON s.Id = tk.UrunId
            WHERE CAST(t.TalepTarihi AS date) = CAST(GETDATE() AS date)
            GROUP BY s.UrunAdi
            ORDER BY s.UrunAdi
            """;
        List<ProductDemandStat> list = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new ProductDemandStat(
                        rs.getString("ProductName"),
                        rs.getInt("TotalQty")
                ));
            }
        }
        return list;
    }
}
