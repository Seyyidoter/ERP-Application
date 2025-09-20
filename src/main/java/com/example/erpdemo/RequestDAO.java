package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Talepler için DAO. Onay işlemi atomik; reddetme yalnızca statü değiştirir. */
public class RequestDAO {

    /** Eski: yalın liste (JOIN yok). Yeni kodlarda {@link #findAllSummaries()} tercih edin. */
    @Deprecated
    public static List<Request> findAll() {
        List<Request> list = new ArrayList<>();
        String sql = """
            SELECT Id, MusteriId, TalepTarihi, Durum, OnaylayanKullaniciId, OnayTarihi
            FROM dbo.Talepler
            ORDER BY Id DESC
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRowToRequest(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** JOIN ile müşteri adını da getirir; liste ekranları için önerilen yöntem. */
    public static List<RequestSummary> findAllSummaries() throws SQLException {
        List<RequestSummary> list = new ArrayList<>();
        String sql = """
            SELECT t.Id,
                   t.MusteriId,
                   m.FirmaAdi       AS CustomerName,
                   CAST(t.TalepTarihi AS date) AS TalepTarihi,
                   t.Durum
            FROM dbo.Talepler t
            LEFT JOIN dbo.Musteriler m ON m.Id = t.MusteriId
            ORDER BY t.Id DESC
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("Id");
                int customerId = rs.getInt("MusteriId");
                Date d = rs.getDate("TalepTarihi");
                LocalDate date = (d != null ? d.toLocalDate() : null);
                String status = rs.getString("Durum");
                String customerName = rs.getString("CustomerName");
                list.add(new RequestSummary(id, customerId, customerName, date, status));
            }
        }
        return list;
    }

    /** Yeni talep başlığı ekler – durum 'Onay Bekliyor'. */
    public static int addRequest(int customerId) throws SQLException {
        String sql = """
            INSERT INTO dbo.Talepler (MusteriId, TalepTarihi, Durum)
            VALUES (?, GETDATE(), N'Onay Bekliyor');
            SELECT SCOPE_IDENTITY();
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1).intValue(); // SCOPE_IDENTITY() decimal döner
            }
        }
        return -1;
    }

    /** Talep kalemi ekler (fiyat = iskontolu). */
    public static void addRequestItem(int requestId, int productId, int qty, double fiyat) throws SQLException {
        String sql = """
            INSERT INTO dbo.TalepKalemleri (TalepId, UrunId, Miktar, TeklifFiyati)
            VALUES (?, ?, ?, ?)
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            ps.setInt(2, productId);
            ps.setInt(3, qty);
            ps.setDouble(4, fiyat);
            ps.executeUpdate();
        }
    }

    /** Bekleyen talepler. */
    public static ObservableList<Request> getPendingRequests() throws SQLException {
        ObservableList<Request> list = FXCollections.observableArrayList();
        String sql = """
            SELECT Id, MusteriId, TalepTarihi, Durum, OnaylayanKullaniciId, OnayTarihi
            FROM dbo.Talepler
            WHERE Durum = N'Onay Bekliyor'
            ORDER BY TalepTarihi DESC, Id DESC
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRowToRequest(rs));
        }
        return list;
    }

    /** Raporlar için: onaylanmış talepler. */
    public static ObservableList<Request> getApprovedRequests() throws SQLException {
        ObservableList<Request> list = FXCollections.observableArrayList();
        String sql = """
            SELECT Id, MusteriId, TalepTarihi, Durum, OnaylayanKullaniciId, OnayTarihi
            FROM dbo.Talepler
            WHERE Durum = N'Onaylandı'
            ORDER BY TalepTarihi DESC, Id DESC
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRowToRequest(rs));
        }
        return list;
    }

    /** Talep kalemleri (görüntüleme için). */
    public static ObservableList<RequestItem> getRequestItemsByRequestId(int requestId) throws SQLException {
        ObservableList<RequestItem> items = FXCollections.observableArrayList();
        String sql = """
            SELECT k.Id, k.TalepId, k.UrunId, s.UrunAdi AS ProductName,
                   k.Miktar AS Quantity, k.TeklifFiyati AS Price
            FROM dbo.TalepKalemleri k
            JOIN dbo.Stoklar s ON s.Id = k.UrunId
            WHERE k.TalepId = ?
            ORDER BY k.Id
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new RequestItem(
                            rs.getInt("Id"),
                            rs.getInt("TalepId"),
                            rs.getInt("UrunId"),
                            rs.getString("ProductName"),
                            rs.getInt("Quantity"),
                            rs.getDouble("Price"),
                            rs.getDouble("Price")
                    ));
                }
            }
        }
        return items;
    }

    /** Tekli silme (transaction). */
    public static int deleteRequestById(int id) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            boolean old = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement ps1 = c.prepareStatement("DELETE FROM dbo.TalepKalemleri WHERE TalepId=?");
                 PreparedStatement ps2 = c.prepareStatement("DELETE FROM dbo.Talepler       WHERE Id=?")) {

                ps1.setInt(1, id);
                ps1.executeUpdate();

                ps2.setInt(1, id);
                int affected = ps2.executeUpdate();

                c.commit();
                return affected;
            } catch (SQLException ex) {
                try { c.rollback(); } catch (SQLException ignore) { }
                throw ex;
            } finally {
                try { c.setAutoCommit(old); } catch (SQLException ignore) { }
            }
        }
    }

    /** Talebin toplam (iskontolu) tutarı. */
    public static double getRequestTotal(int requestId) throws SQLException {
        String sql = """
            SELECT COALESCE(SUM(Miktar * TeklifFiyati), 0)
            FROM dbo.TalepKalemleri
            WHERE TalepId = ?
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    // =================== ATOMİK ONAY ===================
    /** Stok düşme + müşteri bakiyesi artırma + talebi onaylama işlemlerini TEK transaction'da yapar. */
    public static void approveRequestTransactionally(int requestId, int approverId) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            boolean old = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // 1) Talep beklemede mi? müşteriId’yi al
                Integer customerId = null;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT MusteriId FROM dbo.Talepler WHERE Id=? AND Durum=N'Onay Bekliyor'")) {
                    ps.setInt(1, requestId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) customerId = rs.getInt(1);
                    }
                }
                if (customerId == null) throw new SQLException("Talep beklemede değil veya bulunamadı.");

                // 2) Kalemleri çek
                List<ItemLite> items = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT UrunId, Miktar, TeklifFiyati FROM dbo.TalepKalemleri WHERE TalepId=? ORDER BY Id")) {
                    ps.setInt(1, requestId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(new ItemLite(
                                    rs.getInt("UrunId"),
                                    rs.getInt("Miktar"),
                                    rs.getBigDecimal("TeklifFiyati")));
                        }
                    }
                }
                if (items.isEmpty()) throw new SQLException("Talebe ait kalem bulunamadı.");

                // 3) Stok düş (negatife izin verme)
                try (PreparedStatement up = c.prepareStatement(
                        "UPDATE dbo.Stoklar SET Stok = Stok - ? WHERE Id = ? AND Stok >= ?")) {
                    for (ItemLite it : items) {
                        up.setInt(1, it.qty());
                        up.setInt(2, it.productId());
                        up.setInt(3, it.qty());
                        int affected = up.executeUpdate();
                        if (affected != 1) throw new SQLException("Stok yetersiz (ÜrünId=" + it.productId() + ").");
                    }
                }

                // 4) Toplam (2 ondalık, HALF_UP)
                BigDecimal total = BigDecimal.ZERO;
                for (ItemLite it : items) {
                    BigDecimal sub = it.price().multiply(BigDecimal.valueOf(it.qty()));
                    total = total.add(sub);
                }
                total = total.setScale(2, RoundingMode.HALF_UP);

                // 5) Müşteri bakiyesi -= toplam (borç artar)
                try (PreparedStatement bal = c.prepareStatement(
                        "UPDATE dbo.Musteriler SET Bakiye = Bakiye - ? WHERE Id = ?")) {
                    bal.setBigDecimal(1, total);
                    bal.setInt(2, customerId);
                    bal.executeUpdate();
                }

                // 6) Talep onayla
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE dbo.Talepler SET Durum=N'Onaylandı', OnaylayanKullaniciId=?, OnayTarihi=GETDATE() WHERE Id=?")) {
                    ps.setInt(1, approverId);
                    ps.setInt(2, requestId);
                    ps.executeUpdate();
                }

                c.commit();
            } catch (SQLException ex) {
                try { c.rollback(); } catch (SQLException ignore) { }
                throw ex;
            } finally {
                try { c.setAutoCommit(old); } catch (SQLException ignore) { }
            }
        }
    }

    // =================== REDDET ===================
    /** Yalnızca durumu 'Reddedildi' yapar; stok/bakiye değişmez. Sadece 'Onay Bekliyor' için çalışır. */
    public static void rejectRequest(int requestId, int approverId) throws SQLException {
        String sql = """
            UPDATE dbo.Talepler
               SET Durum = N'Reddedildi',
                   OnaylayanKullaniciId = ?,
                   OnayTarihi = GETDATE()
             WHERE Id = ? AND Durum = N'Onay Bekliyor'
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, approverId);
            ps.setInt(2, requestId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Talep beklemede değil veya bulunamadı.");
            }
        }
    }

    // --- yardımcılar ---
    private static Request mapRowToRequest(ResultSet rs) throws SQLException {
        int id = rs.getInt("Id");
        int customerId = rs.getInt("MusteriId");
        Date d = rs.getDate("TalepTarihi");
        LocalDate requestDate = (d != null ? d.toLocalDate() : null);
        String status = rs.getString("Durum");
        Integer approvedBy = (Integer) rs.getObject("OnaylayanKullaniciId");
        Date approvedAtSql = rs.getDate("OnayTarihi");
        LocalDate approvedAt = approvedAtSql != null ? approvedAtSql.toLocalDate() : null;
        return new Request(id, customerId, requestDate, status, approvedBy, approvedAt);
    }

    private record ItemLite(int productId, int qty, BigDecimal price) {}
}
