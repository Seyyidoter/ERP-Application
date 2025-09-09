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
                        rs.getString("Durum")
                );
                requestList.add(request);
            }
        }
        return requestList;
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

    // YENİ EKLENEN METOT: Bir talebe ürün ekleme işlevi
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

    public static void deleteRequest(int requestId) throws SQLException {
        String sql = "DELETE FROM TalepKalemleri WHERE TalepId=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, requestId);
            stmt.executeUpdate();
        }

        String sql2 = "DELETE FROM Talepler WHERE Id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql2)) {
            stmt.setInt(1, requestId);
            stmt.executeUpdate();
        }
    }
}