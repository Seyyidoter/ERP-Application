package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ProductDAO {

    // Tüm ürünleri veritabanından çeker
    public static ObservableList<Product> getAllProducts() throws SQLException {
        ObservableList<Product> productList = FXCollections.observableArrayList();
        String sql = "SELECT * FROM Stoklar";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Product product = new Product(
                        rs.getInt("Id"),
                        rs.getString("UrunAdi"),
                        rs.getDouble("Fiyat"),
                        rs.getInt("Stok"),
                        rs.getString("Birim")
                );
                productList.add(product);
            }
        }
        return productList;
    }

    // Yeni bir ürünü veritabanına ekler
    public static void addProduct(String urunAdi, double fiyat, int stok, String birim) throws SQLException {
        String sql = "INSERT INTO Stoklar (UrunAdi, Fiyat, Stok, Birim) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, urunAdi);
            stmt.setDouble(2, fiyat);
            stmt.setInt(3, stok);
            stmt.setString(4, birim);

            stmt.executeUpdate();
        }
    }

    // Mevcut bir ürünün bilgilerini günceller
    public static void updateProduct(Product product) throws SQLException {
        String sql = "UPDATE Stoklar SET UrunAdi=?, Fiyat=?, Stok=?, Birim=? WHERE Id=?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, product.getUrunAdi());
            stmt.setDouble(2, product.getFiyat());
            stmt.setInt(3, product.getStok());
            stmt.setString(4, product.getBirim());
            stmt.setInt(5, product.getId());

            stmt.executeUpdate();
        }
    }

    // Seçili bir ürünü veritabanından siler
    public static void deleteProduct(int productId) throws SQLException {
        String sql = "DELETE FROM Stoklar WHERE Id=?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, productId);
            stmt.executeUpdate();
        }
    }
}