package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.*;

public class ProductDAO {

    public static ObservableList<Product> getAllProducts() throws SQLException {
        ObservableList<Product> productList = FXCollections.observableArrayList();
        // YALNIZ GEREKLİ KOLONLAR
        String sql = "SELECT Id, UrunAdi, Fiyat, Stok, Birim FROM Stoklar";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                productList.add(new Product(
                        rs.getInt("Id"),
                        norm(rs.getString("UrunAdi")),
                        rs.getBigDecimal("Fiyat"),
                        rs.getInt("Stok"),
                        norm(rs.getString("Birim"))
                ));
            }
        }
        return productList;
    }

    public static Product getProductById(int productId) throws SQLException {
        // YALNIZ GEREKLİ KOLONLAR
        String sql = "SELECT Id, UrunAdi, Fiyat, Stok, Birim FROM Stoklar WHERE Id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Product(
                            rs.getInt("Id"),
                            norm(rs.getString("UrunAdi")),
                            rs.getBigDecimal("Fiyat"),
                            rs.getInt("Stok"),
                            norm(rs.getString("Birim"))
                    );
                }
            }
        }
        return null;
    }

    public static void addProduct(String urunAdi, BigDecimal fiyat, int stok, String birim) throws SQLException {
        if (fiyat == null) fiyat = BigDecimal.ZERO;
        String sql = "INSERT INTO Stoklar (UrunAdi, Fiyat, Stok, Birim) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, norm(urunAdi));
            stmt.setBigDecimal(2, fiyat);
            stmt.setInt(3, stok);
            stmt.setString(4, norm(birim));
            stmt.executeUpdate();
        }
    }

    public static void updateProduct(Product product) throws SQLException {
        String sql = "UPDATE Stoklar SET UrunAdi=?, Fiyat=?, Stok=?, Birim=? WHERE Id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, norm(product.getUrunAdi()));
            stmt.setBigDecimal(2, product.getFiyat());
            stmt.setInt(3, product.getStok());
            stmt.setString(4, norm(product.getBirim()));
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

    /**
     * Ürün silme: önce referans var mı diye kontrol eder,
     * yine de yarış olursa FK kırılımını (SQLServer: 547) yakalayıp anlaşılır mesajla sarar.
     */
    public static void deleteProduct(int productId) throws SQLException {
        // 0) Bağımlılık ön-kontrolü (kullanıcıya net mesaj için)
        if (hasAnyReferences(productId)) {
            throw new SQLException("Ürün silinemedi: Bu ürüne bağlı talepler bulunduğundan silme işlemi engellendi.");
        }

        String sql = "DELETE FROM Stoklar WHERE Id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Ürün bulunamadı (Id=" + productId + ").");
            }
        } catch (SQLException e) {
            if (isForeignKeyViolation(e)) {
                // yarış durumlarında yine de yakala
                throw new SQLException("Ürün silinemedi: Bu ürüne bağlı talepler bulunduğundan silme işlemi engellendi.", e);
            }
            throw e;
        }
    }

    /** TalepKalemleri vb. tablolarda bu ürüne referans var mı? */
    private static boolean hasAnyReferences(int productId) throws SQLException {
        // Şimdilik TalepKalemleri kontrolü. Başka referans tablolarınız varsa benzer kontroller ekleyin.
        final String sql = "SELECT COUNT(1) FROM dbo.TalepKalemleri WHERE UrunId = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** SQL Server FK kırılımı: errorCode=547. SQLState 23*** bütünlük ihlali sınıfı. */
    private static boolean isForeignKeyViolation(SQLException e) {
        // İlk exception
        if (e.getErrorCode() == 547) return true;
        String state = e.getSQLState();
        if (state != null && state.startsWith("23")) return true;

        // Zincirdeki diğer exception'ları da tara
        SQLException next = e.getNextException();
        while (next != null) {
            if (next.getErrorCode() == 547) return true;
            String ns = next.getSQLState();
            if (ns != null && ns.startsWith("23")) return true;
            next = next.getNextException();
        }

        // Mesaj inceleme (son çare)
        String msg = String.valueOf(e.getMessage()).toLowerCase();
        return msg.contains("foreign key") || msg.contains("reference constraint");
    }

    /** null-safe trim + iç boşluk sadeleştirme */
    private static String norm(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ");
    }
}
