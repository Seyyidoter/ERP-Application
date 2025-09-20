package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RequestDAO {

    /** Liste ekranı */
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
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /** Yeni talep başlığı ekle – durum her zaman 'Onay Bekliyor' */
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
                if (rs.next()) {
                    return rs.getBigDecimal(1).intValue(); // SCOPE_IDENTITY() decimal döner
                }
            }
        }
        return -1;
    }

    /** Yeni talep kalemi ekle (fiyat = iskontolu fiyat) */
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

    /** Onay ekranı: bekleyenler */
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

    /** RAPOR: Onaylanmış talepler */
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

    /** Onay ekranı: talep kalemleri */
    public static ObservableList<RequestItem> getRequestItemsByRequestId(int requestId) throws SQLException {
        ObservableList<RequestItem> items = FXCollections.observableArrayList();
        String sql = """
            SELECT k.Id,
                   k.TalepId,
                   k.UrunId,
                   s.UrunAdi       AS ProductName,
                   k.Miktar        AS Quantity,
                   k.TeklifFiyati  AS Price
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
                            rs.getDouble("Price") // indirimli = fiyat
                    ));
                }
            }
        }
        return items;
    }

    /** Onayla */
    public static void approveRequest(int requestId, int userId) throws SQLException {
        String sql = """
            UPDATE dbo.Talepler
               SET Durum = N'Onaylandı',
                   OnaylayanKullaniciId = ?,
                   OnayTarihi = GETDATE()
             WHERE Id = ?
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, requestId);
            ps.executeUpdate();
        }
    }

    /** Reddet (tarihi temizle) */
    public static void rejectRequest(int requestId, int userId) throws SQLException {
        String sql = """
            UPDATE dbo.Talepler
               SET Durum = N'Reddedildi',
                   OnaylayanKullaniciId = ?,
                   OnayTarihi = NULL
             WHERE Id = ?
        """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, requestId);
            ps.executeUpdate();
        }
    }

    /** Tekli silme (önce kalemler, sonra başlık) */
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
                c.setAutoCommit(old);
                return affected;
            } catch (SQLException ex) {
                c.rollback();
                c.setAutoCommit(true);
                throw ex;
            }
        }
    }

    /** Toplam tutar (iskontolu): SUM(Miktar * TeklifFiyati) */
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

    // --- Yardımcı ---
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
}
