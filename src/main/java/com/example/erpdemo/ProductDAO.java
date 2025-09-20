package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.sql.*;

public class ProductDAO {

    public static ObservableList<Product> getAllProducts() throws SQLException {
        ObservableList<Product> productList = FXCollections.observableArrayList();
        String sql = "SELECT * FROM Stoklar";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                productList.add(new Product(
                        rs.getInt("Id"),
                        rs.getString("UrunAdi"),
                        rs.getDouble("Fiyat"),
                        rs.getInt("Stok"),
                        rs.getString("Birim")
                ));
            }
        }
        return productList;
    }

    public static Product getProductById(int productId) throws SQLException {
        String sql = "SELECT * FROM Stoklar WHERE Id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Product(
                            rs.getInt("Id"),
                            rs.getString("UrunAdi"),
                            rs.getDouble("Fiyat"),
                            rs.getInt("Stok"),
                            rs.getString("Birim")
                    );
                }
            }
        }
        return null;
    }

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

    /**
     * Güvenli stok güncellemesi.
     * quantityChange < 0 ise: negatifleşmeyi engellemek için WHERE koşulu eklenir.
     */
    public static void updateProductStock(int productId, int quantityChange) throws SQLException {
        String sql = "UPDATE Stoklar SET Stok = Stok + ? WHERE Id = ? AND Stok + ? >= 0";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, quantityChange);
            stmt.setInt(2, productId);
            stmt.setInt(3, quantityChange);
            int affected = stmt.executeUpdate();
            if (affected != 1) {
                throw new SQLException("Yetersiz stok veya ürün bulunamadı (Id=" + productId + ").");
            }
        }
    }

    public static void deleteProduct(int productId) throws SQLException {
        String sql = "DELETE FROM Stoklar WHERE Id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            stmt.executeUpdate();
        }
    }
}
