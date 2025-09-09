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

    // ID'ye göre tek bir ürünü çeker
    public static Product getProductById(int productId) throws SQLException {
        String sql = "SELECT * FROM Stoklar WHERE Id = ?";
        Product product = null;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, productId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    product = new Product(
                            rs.getInt("Id"),
                            rs.getString("UrunAdi"),
                            rs.getDouble("Fiyat"),
                            rs.getInt("Stok"),
                            rs.getString("Birim")
                    );
                }
            }
        }
        return product;
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

    // Bir ürünün stok miktarını günceller
    public static void updateProductStock(int productId, int quantityChange) throws SQLException {
        String sql = "UPDATE Stoklar SET Stok = Stok + ? WHERE Id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, quantityChange);
            stmt.setInt(2, productId);

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