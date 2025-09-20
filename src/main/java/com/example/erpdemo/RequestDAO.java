package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Talepler için DAO */
public class RequestDAO {

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
                if (rs.next()) return rs.getBigDecimal(1).intValue();
            }
        }
        return -1;
    }

    /** Talep kalemi ekler (fiyat = iskontolu, BigDecimal). */
    public static void addRequestItem(int requestId, int productId, int qty, BigDecimal fiyat) throws SQLException {
        if (fiyat == null) fiyat = BigDecimal.ZERO;
        String sql = """
            INSERT INTO dbo.TalepKalemleri (TalepId, UrunId, Miktar, TeklifFiyati)
            VALUES (?, ?, ?, ?)
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            ps.setInt(2, productId);
            ps.setInt(3, qty);
            ps.setBigDecimal(4, fiyat);
            ps.executeUpdate();
        }
    }

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

    /** Görüntüleme için talep kalemleri (liste ve iskontolu fiyat BigDecimal). */
    public static ObservableList<RequestItem> getRequestItemsByRequestId(int requestId) throws SQLException {
        ObservableList<RequestItem> items = FXCollections.observableArrayList();
        String sql = """
            SELECT k.Id, k.TalepId, k.UrunId,
                   s.UrunAdi AS ProductName,
                   k.Miktar  AS Quantity,
                   s.Fiyat   AS ListPrice,
                   k.TeklifFiyati AS DiscountedPrice
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
                            rs.getBigDecimal("ListPrice"),
                            rs.getBigDecimal("DiscountedPrice")
                    ));
                }
            }
        }
        return items;
    }

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

    /** Toplamı BigDecimal olarak döndürür (2 ondalık, HALF_UP). */
    public static BigDecimal getRequestTotal(int requestId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(Miktar * TeklifFiyati), 0) FROM dbo.TalepKalemleri WHERE TalepId = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                BigDecimal v = rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
                return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
            }
        }
    }

    /** Eski çağrılar için kolaylık. Yeni kodlarda BigDecimal kullanalım. */
    @Deprecated
    public static double getRequestTotalAsDouble(int requestId) throws SQLException {
        return getRequestTotal(requestId).doubleValue();
    }

    /**
     * Stok düşme + bakiye güncelleme + talebi onaylama işlemlerini
     * TEK transaction içinde, yarışa kapalı ve deadlock'a dayanıklı şekilde yapar.
     *
     * Durum geçişi: 'Onay Bekliyor' -> 'Onaylanıyor' -> 'Onaylandı'
     */
    public static void approveRequestTransactionally(int requestId, int approverId) throws SQLException {
        final int maxRetries = 3;
        final long[] backoff = {100L, 250L, 500L};

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (Connection c = DatabaseManager.getConnection()) {
                final boolean oldAuto = c.getAutoCommit();
                final int oldIso = c.getTransactionIsolation();
                c.setAutoCommit(false);
                c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                try (Statement st = c.createStatement()) {
                    st.execute("SET LOCK_TIMEOUT 5000"); // 5 sn
                }

                try {
                    // 0) Yalnız bir işlemci içeri girsin: Onay Bekliyor -> Onaylanıyor
                    int touched;
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE dbo.Talepler SET Durum=N'Onaylanıyor' WHERE Id=? AND Durum=N'Onay Bekliyor'")) {
                        ps.setInt(1, requestId);
                        touched = ps.executeUpdate();
                    }
                    if (touched != 1) {
                        throw new SQLException("Talep beklemede değil veya başka bir işlem tarafından alındı.");
                    }

                    // 1) Müşteri id
                    Integer customerId = null;
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT MusteriId FROM dbo.Talepler WHERE Id=?")) {
                        ps.setInt(1, requestId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) customerId = rs.getInt(1);
                        }
                    }
                    if (customerId == null) throw new SQLException("Talep başlığı bulunamadı.");

                    // 2) Kalemler (productId'e göre sırala → deadlock riski düşer)
                    List<ItemLite> items = new ArrayList<>();
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT UrunId, Miktar, TeklifFiyati FROM dbo.TalepKalemleri WHERE TalepId=?")) {
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
                    items.sort(java.util.Comparator.comparingInt(ItemLite::productId));

                    // 3) Stok düş (koşullu)
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

                    // 4) Toplam
                    BigDecimal total = BigDecimal.ZERO;
                    for (ItemLite it : items) {
                        BigDecimal sub = it.price().multiply(BigDecimal.valueOf(it.qty()));
                        total = total.add(sub);
                    }
                    total = total.setScale(2, RoundingMode.HALF_UP);

                    // 5) Bakiye (iş mantığına göre – sende müşteri bakiyesini düşürüyordu, korundu)
                    try (PreparedStatement bal = c.prepareStatement(
                            "UPDATE dbo.Musteriler SET Bakiye = Bakiye - ? WHERE Id = ?")) {
                        bal.setBigDecimal(1, total);
                        bal.setInt(2, customerId);
                        bal.executeUpdate();
                    }

                    // 6) Onayla (yalnızca 'Onaylanıyor' ise)
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE dbo.Talepler " +
                                    "SET Durum=N'Onaylandı', OnaylayanKullaniciId=?, OnayTarihi=GETDATE() " +
                                    "WHERE Id=? AND Durum=N'Onaylanıyor'")) {
                        ps.setInt(1, approverId);
                        ps.setInt(2, requestId);
                        int ok = ps.executeUpdate();
                        if (ok != 1) throw new SQLException("Talep durumu beklenmedik şekilde değişti.");
                    }

                    c.commit();
                    return; // başarı

                } catch (SQLException ex) {
                    try { c.rollback(); } catch (SQLException ignore) { }
                    if (isDeadlockOrTimeout(ex) && attempt < maxRetries) {
                        try { Thread.sleep(backoff[attempt - 1]); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        continue; // yeniden dene
                    }
                    throw ex;
                } finally {
                    try { c.setTransactionIsolation(oldIso); } catch (SQLException ignore) { }
                    try { c.setAutoCommit(oldAuto); } catch (SQLException ignore) { }
                }
            }
        }

        throw new SQLException("Onay işlemi tekrar denemelerine rağmen tamamlanamadı.");
    }

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
            if (affected == 0) throw new SQLException("Talep beklemede değil veya bulunamadı.");
        }
    }

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

    /** SQL Server deadlock (1205) veya lock timeout (1222) tespiti */
    private static boolean isDeadlockOrTimeout(SQLException ex) {
        int code = ex.getErrorCode();        // 1205: deadlock victim, 1222: lock timeout
        if (code == 1205 || code == 1222) return true;
        String state = ex.getSQLState();     // bazı sürümlerde 40001 (serialization failure) gelebilir
        return "40001".equals(state);
    }
}
