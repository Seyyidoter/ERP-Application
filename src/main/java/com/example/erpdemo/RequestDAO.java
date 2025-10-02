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

    /** Tüm taleplerin özetleri (müşteri adı dahil) */
    public static List<RequestSummary> findAllSummaries() throws SQLException {
        List<RequestSummary> list = new ArrayList<>();
        String sql = """
            SELECT t.Id,
                   t.MusteriId,
                   m.FirmaAdi                  AS CustomerName,
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

    /**
     * Controller’dan SQL’i kaldırmak için: Başlık + Kalemleri tek transaction’da ekler.
     * @return oluşturulan talep Id
     */
    public static int addRequestWithItems(int customerId, List<RequestItem> items) throws SQLException {
        if (items == null || items.isEmpty())
            throw new SQLException("Talep için en az bir kalem gereklidir.");

        final String insertHeaderSql = """
            INSERT INTO dbo.Talepler (MusteriId, TalepTarihi, Durum)
            VALUES (?, GETDATE(), N'Onay Bekliyor')
        """;
        final String insertItemSql = """
            INSERT INTO dbo.TalepKalemleri (TalepId, UrunId, Miktar, TeklifFiyati)
            VALUES (?, ?, ?, ?)
        """;

        try (Connection c = DatabaseManager.getConnection()) {
            boolean oldAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            int requestId = -1;

            try (PreparedStatement psHdr = c.prepareStatement(insertHeaderSql, Statement.RETURN_GENERATED_KEYS)) {
                psHdr.setInt(1, customerId);
                psHdr.executeUpdate();
                try (ResultSet keys = psHdr.getGeneratedKeys()) {
                    if (keys.next()) requestId = ((Number) keys.getObject(1)).intValue();
                }
            }
            if (requestId <= 0) throw new SQLException("Yeni talep Id alınamadı (generated keys).");

            try (PreparedStatement psItem = c.prepareStatement(insertItemSql)) {
                for (RequestItem it : items) {
                    BigDecimal discounted = Money.scale2(it.getDiscountedPrice()); // 🔸
                    psItem.setInt(1, requestId);
                    psItem.setInt(2, it.getProductId());
                    psItem.setInt(3, it.getQuantity());
                    psItem.setBigDecimal(4, discounted);
                    psItem.addBatch();
                }
                psItem.executeBatch();
            }

            c.commit();
            try { c.setAutoCommit(oldAuto); } catch (SQLException ignore) {}
            return requestId;
        }
    }

    /** Var olan iki basit metod (geriye dönük kullanım için bırakıldı) */
    public static int addRequest(int customerId) throws SQLException {
        String sql = """
            INSERT INTO dbo.Talepler (MusteriId, TalepTarihi, Durum)
            VALUES (?, GETDATE(), N'Onay Bekliyor')
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customerId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return ((Number) keys.getObject(1)).intValue();
            }
        }
        throw new SQLException("Yeni talep Id alınamadı (generated keys boş).");
    }

    /** Talep kalemi ekler (fiyat = iskontolu). */
    public static void addRequestItem(int requestId, int productId, int qty, BigDecimal fiyat) throws SQLException {
        String sql = """
            INSERT INTO dbo.TalepKalemleri (TalepId, UrunId, Miktar, TeklifFiyati)
            VALUES (?, ?, ?, ?)
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            ps.setInt(2, productId);
            ps.setInt(3, qty);
            ps.setBigDecimal(4, Money.scale2(fiyat)); // 🔸
            ps.executeUpdate();
        }
    }

    /** Bekleyen talepler (müşteri adı içermez; gerekirse getPendingSummaries kullan). */
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

    /** Onaylanmış talepler (müşteri adı içermez). */
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

    /** ONAYLANMIŞ talepleri tarih aralığına göre döndürür. */
    public static ObservableList<Request> getApprovedRequestsBetween(LocalDate from, LocalDate to) throws SQLException {
        ObservableList<Request> list = FXCollections.observableArrayList();

        StringBuilder sb = new StringBuilder("""
        SELECT Id, MusteriId, TalepTarihi, Durum, OnaylayanKullaniciId, OnayTarihi
          FROM dbo.Talepler
         WHERE Durum = N'Onaylandı'
        """);

        if (from != null) sb.append(" AND OnayTarihi >= ? ");
        if (to   != null) sb.append(" AND OnayTarihi <  ? ");
        sb.append(" ORDER BY OnayTarihi DESC, Id DESC ");

        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {
            int i = 1;
            if (from != null) ps.setTimestamp(i++, Timestamp.valueOf(from.atStartOfDay()));
            if (to   != null) ps.setTimestamp(i++, Timestamp.valueOf(to.atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRowToRequest(rs));
            }
        }
        return list;
    }

    /** Talep kalemleri (liste ve iskontolu fiyat BigDecimal) */
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

    /** Talebi ve tüm kalemlerini siler. */
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

    /** Toplamı BigDecimal olarak döndürür (2 ondalık, HALF_UP) — CAST ile güvenli. */
    public static BigDecimal getRequestTotal(int requestId) throws SQLException {
        final String sql = """
            SELECT COALESCE(
                     SUM(CAST(Miktar AS decimal(18,4)) * CAST(TeklifFiyati AS decimal(18,4)))
                   , 0)
            FROM dbo.TalepKalemleri
            WHERE TalepId = ?
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                BigDecimal v = rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
                return Money.scale2(v); // 🔸
            }
        }
    }

    @Deprecated
    public static double getRequestTotalAsDouble(int requestId) throws SQLException {
        return getRequestTotal(requestId).doubleValue();
    }

    /**
     * Onay işlemi: stok düş, bakiyeyi düş, talebi onayla (tek transaction).
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
                    st.execute("SET XACT_ABORT ON; SET LOCK_TIMEOUT 5000");
                }

                try {
                    int touched;
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE dbo.Talepler SET Durum=N'Onaylanıyor' WHERE Id=? AND Durum=N'Onay Bekliyor'")) {
                        ps.setInt(1, requestId);
                        touched = ps.executeUpdate();
                    }
                    if (touched != 1) throw new SQLException("Talep beklemede değil veya başka bir işlem tarafından alındı.");

                    Integer customerId = null;
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT MusteriId FROM dbo.Talepler WITH (UPDLOCK, ROWLOCK) WHERE Id=?")) {
                        ps.setInt(1, requestId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) customerId = rs.getInt(1);
                        }
                    }
                    if (customerId == null) throw new SQLException("Talep başlığı bulunamadı.");

                    List<ItemLite> items = new ArrayList<>();
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT UrunId, Miktar, TeklifFiyati FROM dbo.TalepKalemleri WITH (UPDLOCK, ROWLOCK) WHERE TalepId=?")) {
                        ps.setInt(1, requestId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) items.add(new ItemLite(
                                    rs.getInt("UrunId"),
                                    rs.getInt("Miktar"),
                                    rs.getBigDecimal("TeklifFiyati")));
                        }
                    }
                    if (items.isEmpty()) throw new SQLException("Talebe ait kalem bulunamadı.");
                    items.sort(java.util.Comparator.comparingInt(ItemLite::productId));

                    try (PreparedStatement up = c.prepareStatement(
                            "UPDATE s WITH (ROWLOCK) SET s.Stok = s.Stok - ? FROM dbo.Stoklar s WHERE s.Id = ? AND s.Stok >= ?")) {
                        for (ItemLite it : items) {
                            up.setInt(1, it.qty());
                            up.setInt(2, it.productId());
                            up.setInt(3, it.qty());
                            int affected = up.executeUpdate();
                            if (affected != 1) throw new SQLException("Stok yetersiz (ÜrünId=" + it.productId() + ").");
                        }
                    }

                    BigDecimal total = getRequestTotalInTx(c, requestId);

                    try (PreparedStatement bal = c.prepareStatement(
                            "UPDATE m WITH (ROWLOCK) SET m.Bakiye = m.Bakiye - ? FROM dbo.Musteriler m WHERE m.Id = ?")) {
                        bal.setBigDecimal(1, total);
                        bal.setInt(2, customerId);
                        bal.executeUpdate();
                    }

                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE dbo.Talepler SET Durum=N'Onaylandı', OnaylayanKullaniciId=?, OnayTarihi=GETDATE() WHERE Id=? AND Durum=N'Onaylanıyor'")) {
                        ps.setInt(1, approverId);
                        ps.setInt(2, requestId);
                        int ok = ps.executeUpdate();
                        if (ok != 1) throw new SQLException("Talep durumu beklenmedik şekilde değişti.");
                    }

                    c.commit();
                    return;

                } catch (SQLException ex) {
                    try { c.rollback(); } catch (SQLException ignore) {}
                    if (isDeadlockOrTimeout(ex) && attempt < maxRetries) {
                        try { Thread.sleep(backoff[attempt - 1]); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    throw ex;
                } finally {
                    try { c.setTransactionIsolation(oldIso); } catch (SQLException ignore) {}
                    try { c.setAutoCommit(oldAuto); } catch (SQLException ignore) {}
                }
            }
        }
        throw new SQLException("Onay işlemi tekrar denemelerine rağmen tamamlanamadı.");
    }

    /** 🔸 Reddetme metodu — Controllers bunu çağırıyor. */
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

    // ---------- Yardımcılar ----------

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
        int code = ex.getErrorCode();
        if (code == 1205 || code == 1222) return true;
        String state = ex.getSQLState();
        return "40001".equals(state);
    }

    /** Aynı transaction içinde toplamı alır; 2 ondalık HALF_UP ile döndürür. */
    private static BigDecimal getRequestTotalInTx(Connection c, int requestId) throws SQLException {
        final String sql = """
            SELECT COALESCE(
                     SUM(CAST(Miktar AS decimal(18,4)) * CAST(TeklifFiyati AS decimal(18,4)))
                   , 0)
            FROM dbo.TalepKalemleri
            WHERE TalepId = ?
        """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                BigDecimal v = rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
                return Money.scale2(v); // 🔸
            }
        }
    }

    /** Bekleyen taleplerin özetini (müşteri adı dahil) döndürür – onay ekranı burayı kullanır. */
    public static List<RequestSummary> getPendingSummaries() throws SQLException {
        List<RequestSummary> list = new ArrayList<>();
        String sql = """
            SELECT t.Id,
                   t.MusteriId,
                   m.FirmaAdi                  AS CustomerName,
                   CAST(t.TalepTarihi AS date) AS TalepTarihi,
                   t.Durum
            FROM dbo.Talepler t
            LEFT JOIN dbo.Musteriler m ON m.Id = t.MusteriId
            WHERE t.Durum = N'Onay Bekliyor'
            ORDER BY t.TalepTarihi DESC, t.Id DESC
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
}
