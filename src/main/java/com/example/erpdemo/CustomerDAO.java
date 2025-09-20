package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;

public class CustomerDAO {

    public static ObservableList<Customer> getAllCustomers() throws SQLException {
        ObservableList<Customer> customerList = FXCollections.observableArrayList();
        String sql = "SELECT * FROM Musteriler";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                // Bakiye DECIMAL ise BigDecimal oku, UI modeli double ile devam edebilir
                BigDecimal bal = rs.getBigDecimal("Bakiye");
                double balance = (bal == null ? 0.0 : bal.doubleValue());

                Customer customer = new Customer(
                        rs.getInt("Id"),
                        rs.getString("FirmaAdi"),
                        rs.getString("IletisimKisi"),
                        rs.getString("Telefon"),
                        rs.getString("Eposta"),
                        rs.getInt("Iskonto"),
                        balance
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
                    BigDecimal bal = rs.getBigDecimal("Bakiye");
                    double balance = (bal == null ? 0.0 : bal.doubleValue());

                    return new Customer(
                            rs.getInt("Id"),
                            rs.getString("FirmaAdi"),
                            rs.getString("IletisimKisi"),
                            rs.getString("Telefon"),
                            rs.getString("Eposta"),
                            rs.getInt("Iskonto"),
                            balance
                    );
                }
            }
        }
        return null;
    }

    public static void addCustomer(String companyName, String contactPerson, String phone, String email, int iskonto) throws SQLException {
        // Bakiye varsayılan 0.00
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

    /**
     * Bakiye ayarla: delta pozitifse borç azalır (bakiye artar), negatifse borç artar (bakiye düşer).
     * BigDecimal ile ve 2 ondalık ölçeğe yuvarlanarak çalışır.
     */
    public static void adjustBalance(int customerId, BigDecimal delta) throws SQLException {
        if (delta == null) throw new IllegalArgumentException("delta null olamaz");
        delta = delta.setScale(2, RoundingMode.HALF_UP);

        String sql = "UPDATE Musteriler SET Bakiye = Bakiye + ? WHERE Id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, delta);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    /** Kolaylık: double çağrıları için güvenli BigDecimal’a çevirir. */
    public static void adjustBalance(int customerId, double delta) throws SQLException {
        if (!Double.isFinite(delta)) {
            throw new IllegalArgumentException("delta geçerli bir sayı olmalı.");
        }
        adjustBalance(customerId, BigDecimal.valueOf(delta));
    }
}
