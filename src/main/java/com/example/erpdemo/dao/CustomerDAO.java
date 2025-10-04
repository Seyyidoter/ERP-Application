package com.example.erpdemo.dao;

import com.example.erpdemo.model.Customer;
import com.example.erpdemo.util.DatabaseManager;
import com.example.erpdemo.util.Money;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public class CustomerDAO {

    public static ObservableList<Customer> getAllCustomers() throws SQLException {
        ObservableList<Customer> customerList = FXCollections.observableArrayList();
        String sql = "SELECT Id, FirmaAdi, IletisimKisi, Telefon, Eposta, Iskonto, Bakiye FROM Musteriler";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                BigDecimal bal = Money.scale2(rs.getBigDecimal("Bakiye"));
                Customer customer = new Customer(
                        rs.getInt("Id"),
                        rs.getString("FirmaAdi"),
                        rs.getString("IletisimKisi"),
                        rs.getString("Telefon"),
                        rs.getString("Eposta"),
                        rs.getInt("Iskonto"),
                        bal
                );
                customerList.add(customer);
            }
        }
        return customerList;
    }

    public static Customer getCustomerById(int customerId) throws SQLException {
        String sql = "SELECT Id, FirmaAdi, IletisimKisi, Telefon, Eposta, Iskonto, Bakiye FROM Musteriler WHERE Id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal bal = Money.scale2(rs.getBigDecimal("Bakiye"));
                    return new Customer(
                            rs.getInt("Id"),
                            rs.getString("FirmaAdi"),
                            rs.getString("IletisimKisi"),
                            rs.getString("Telefon"),
                            rs.getString("Eposta"),
                            rs.getInt("Iskonto"),
                            bal
                    );
                }
            }
        }
        return null;
    }

    public static void addCustomer(String companyName, String contactPerson, String phone, String email, int iskonto) throws SQLException {
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

    /** Bakiye ayarla (BigDecimal, 2 ondalık – tek noktadan ölçekleme). */
    public static void adjustBalance(int customerId, BigDecimal delta) throws SQLException {
        if (delta == null) throw new IllegalArgumentException("delta null olamaz");
        delta = Money.scale2(delta);

        String sql = "UPDATE Musteriler SET Bakiye = Bakiye + ? WHERE Id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, delta);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    /** double için aşırı yükleme (eski çağrılar uyumluluğu). */
    public static void adjustBalance(int customerId, double delta) throws SQLException {
        if (!Double.isFinite(delta)) throw new IllegalArgumentException("delta geçerli olmalı");
        adjustBalance(customerId, BigDecimal.valueOf(delta));
    }

    /** Toplu müşteri adı getirir. */
    public static Map<Integer, String> getCustomerNamesByIds(Set<Integer> ids) throws SQLException {
        Map<Integer, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) return map;

        StringBuilder sb = new StringBuilder("SELECT Id, FirmaAdi FROM Musteriler WHERE Id IN (");
        String sep = "";
        for (int i = 0; i < ids.size(); i++) { sb.append(sep).append("?"); sep = ","; }
        sb.append(")");

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            int idx = 1;
            for (Integer id : ids) ps.setInt(idx++, id);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getInt("Id"), rs.getString("FirmaAdi"));
            }
        }
        return map;
    }
}
