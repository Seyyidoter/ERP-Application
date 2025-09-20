package com.example.erpdemo;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard metrikleri – tarih filtresi indeks-dostu olacak şekilde güncellendi.
 * NOT: Sunucu saatine göre "bugün" hesaplanır (GETDATE()).
 */
public class DashboardDAO {

    /** Bugün oluşturulan talep sayısı */
    public static int getTodayRequestCount() throws SQLException {
        // Kolona CAST yok: SARGable aralık filtresi
        String sql = """
            SELECT COUNT(*)
            FROM dbo.Talepler
            WHERE TalepTarihi >= CAST(GETDATE() AS date)
              AND TalepTarihi <  DATEADD(day, 1, CAST(GETDATE() AS date))
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
            WHERE t.TalepTarihi >= CAST(GETDATE() AS date)
              AND t.TalepTarihi <  DATEADD(day, 1, CAST(GETDATE() AS date))
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
            WHERE t.TalepTarihi >= CAST(GETDATE() AS date)
              AND t.TalepTarihi <  DATEADD(day, 1, CAST(GETDATE() AS date))
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
            WHERE t.TalepTarihi >= CAST(GETDATE() AS date)
              AND t.TalepTarihi <  DATEADD(day, 1, CAST(GETDATE() AS date))
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
