package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.sql.*;

public class CustomerDAO {

    public static ObservableList<Customer> getAllCustomers() throws SQLException {
        ObservableList<Customer> customerList = FXCollections.observableArrayList();
        String sql = "SELECT * FROM Musteriler";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Customer customer = new Customer(
                        rs.getInt("Id"),
                        rs.getString("FirmaAdi"),
                        rs.getString("IletisimKisi"),
                        rs.getString("Telefon"),
                        rs.getString("Eposta"),
                        rs.getInt("Iskonto"),
                        rs.getDouble("Bakiye")
                );
                customerList.add(customer);
            }
        }
        return customerList;
    }

    public static Customer getCustomerById(int customerId) throws SQLException {
        String sql = "SELECT * FROM Musteriler WHERE Id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Customer(
                            rs.getInt("Id"),
                            rs.getString("FirmaAdi"),
                            rs.getString("IletisimKisi"),
                            rs.getString("Telefon"),
                            rs.getString("Eposta"),
                            rs.getInt("Iskonto"),
                            rs.getDouble("Bakiye")
                    );
                }
            }
        }
        return null;
    }

    public static void addCustomer(String companyName, String contactPerson, String phone, String email, int iskonto) throws SQLException {
        // Bakiye kolonu varsa varsayılanı 0 olsun; yoksa bu alanı bırak.
        String sql = "INSERT INTO Musteriler (FirmaAdi, IletisimKisi, Telefon, Eposta, Iskonto, Bakiye) VALUES (?, ?, ?, ?, ?, 0)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, companyName);
            stmt.setString(2, contactPerson);
            stmt.setString(3, phone);
            stmt.setString(4, email);
            stmt.setInt(5, iskonto);
            stmt.executeUpdate();
        }
    }

    public static void updateCustomer(Customer customer) throws SQLException {
        String sql = "UPDATE Musteriler SET FirmaAdi=?, IletisimKisi=?, Telefon=?, Eposta=?, Iskonto=? WHERE Id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, customer.getCompanyName());
            stmt.setString(2, customer.getContactPerson());
            stmt.setString(3, customer.getPhone());
            stmt.setString(4, customer.getEmail());
            stmt.setInt(5, customer.getIskonto());
            stmt.setInt(6, customer.getId());
            stmt.executeUpdate();
        }
    }

    public static void deleteCustomer(int customerId) throws SQLException {
        String sql = "DELETE FROM Musteriler WHERE Id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.executeUpdate();
        }
    }

    /** Bakiye ayarla: delta pozitifse borç azalır (bakiye artar), negatifse borç artar (bakiye düşer). */
    public static void adjustBalance(int customerId, double delta) throws SQLException {
        String sql = "UPDATE Musteriler SET Bakiye = Bakiye + ? WHERE Id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, delta);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    public static void changeBalance(int customerId, double delta) throws SQLException {
        adjustBalance(customerId, delta);
    }
}
