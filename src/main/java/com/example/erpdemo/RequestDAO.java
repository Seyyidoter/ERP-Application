package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

public class RequestDAO {

    public static ObservableList<Request> getAllRequests() throws SQLException {
        ObservableList<Request> requestList = FXCollections.observableArrayList();
        String sql = "SELECT * FROM Talepler";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Request request = new Request(
                        rs.getInt("Id"),
                        rs.getInt("MusteriId"),
                        rs.getDate("TalepTarihi").toLocalDate(),
                        rs.getString("Durum"),
                        rs.getObject("OnaylayanKullaniciId", Integer.class),
                        rs.getObject("OnayTarihi", LocalDate.class)
                );
                requestList.add(request);
            }
        }
        return requestList;
    }

    public static ObservableList<Request> getPendingRequests() throws SQLException {
        ObservableList<Request> pendingList = FXCollections.observableArrayList();
        String sql = "SELECT * FROM Talepler WHERE Durum = 'Onay Bekliyor'";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Request request = new Request(
                        rs.getInt("Id"),
                        rs.getInt("MusteriId"),
                        rs.getDate("TalepTarihi").toLocalDate(),
                        rs.getString("Durum"),
                        rs.getObject("OnaylayanKullaniciId", Integer.class),
                        rs.getObject("OnayTarihi", LocalDate.class)
                );
                pendingList.add(request);
            }
        }
        return pendingList;
    }

    public static ObservableList<Request> getApprovedRequests() throws SQLException {
        ObservableList<Request> approvedList = FXCollections.observableArrayList();
        String sql = "SELECT * FROM Talepler WHERE Durum = 'Onaylandı'";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Request request = new Request(
                        rs.getInt("Id"),
                        rs.getInt("MusteriId"),
                        rs.getDate("TalepTarihi").toLocalDate(),
                        rs.getString("Durum"),
                        rs.getObject("OnaylayanKullaniciId", Integer.class),
                        rs.getObject("OnayTarihi", LocalDate.class)
                );
                approvedList.add(request);
            }
        }
        return approvedList;
    }

    public static int addRequest(int customerId) throws SQLException {
        String sql = "INSERT INTO Talepler (MusteriId, TalepTarihi, Durum) VALUES (?, ?, ?)";
        int requestId = -1;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, customerId);
            stmt.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
            stmt.setString(3, "Onay Bekliyor");

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        requestId = rs.getInt(1);
                    }
                }
            }
        }
        return requestId;
    }

    public static void addRequestItem(int requestId, int productId, int quantity, double price) throws SQLException {
        String sql = "INSERT INTO TalepKalemleri (TalepId, UrunId, Miktar, TeklifFiyati) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, requestId);
            stmt.setInt(2, productId);
            stmt.setInt(3, quantity);
            stmt.setDouble(4, price);
            stmt.executeUpdate();
        }
    }

    public static ObservableList<RequestItem> getRequestItemsByRequestId(int requestId) throws SQLException {
        ObservableList<RequestItem> items = FXCollections.observableArrayList();
        String sql = "SELECT ti.*, s.UrunAdi, s.Fiyat FROM TalepKalemleri ti JOIN Stoklar s ON ti.UrunId = s.Id WHERE ti.TalepId = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, requestId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RequestItem item = new RequestItem(
                            rs.getInt("Id"),
                            rs.getInt("TalepId"),
                            rs.getInt("UrunId"),
                            rs.getString("UrunAdi"),
                            rs.getInt("Miktar"),
                            rs.getDouble("Fiyat"),
                            rs.getDouble("TeklifFiyati")
                    );
                    items.add(item);
                }
            }
        }
        return items;
    }

    public static void updateRequestStatus(int requestId, String newStatus, int approverId) throws SQLException {
        String sql = "UPDATE Talepler SET Durum=?, OnaylayanKullaniciId=?, OnayTarihi=? WHERE Id=?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newStatus);
            stmt.setInt(2, approverId);
            stmt.setDate(3, java.sql.Date.valueOf(LocalDate.now()));
            stmt.setInt(4, requestId);

            stmt.executeUpdate();
        }
    }

    public static void deleteRequest(int requestId) throws SQLException {
        String sql1 = "DELETE FROM TalepKalemleri WHERE TalepId=?";
        String sql2 = "DELETE FROM Talepler WHERE Id=?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt1 = conn.prepareStatement(sql1);
             PreparedStatement stmt2 = conn.prepareStatement(sql2)) {

            stmt1.setInt(1, requestId);
            stmt1.executeUpdate();

            stmt2.setInt(1, requestId);
            stmt2.executeUpdate();
        }
    }
}