package com.example.erpdemo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

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
                        rs.getInt("Iskonto") // Buraya Iskonto sütununu ekledik
                );
                customerList.add(customer);
            }
        }
        return customerList;
    }

    public static void addCustomer(String companyName, String contactPerson, String phone, String email, int iskonto) throws SQLException {
        String sql = "INSERT INTO Musteriler (FirmaAdi, IletisimKisi, Telefon, Eposta, Iskonto) VALUES (?, ?, ?, ?, ?)";

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
    // CustomerDAO.java içine bu metotları ekle

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
}