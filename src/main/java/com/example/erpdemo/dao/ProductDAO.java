package com.example.erpdemo.dao;

import com.example.erpdemo.util.DatabaseManager;
import com.example.erpdemo.util.Money;
import com.example.erpdemo.model.Product;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.*;

/** Ürün (Stoklar) DAO */
public class ProductDAO {

    /** Tüm ürünler – yalnız gerekli kolonlar */
    public static ObservableList<Product> getAllProducts() throws SQLException {
        ObservableList<Product> productList = FXCollections.observableArrayList();
        String sql = "SELECT Id, UrunAdi, Fiyat, Stok, Birim FROM dbo.Stoklar";
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

    /** Tek ürün (yalnız gerekli kolonlar) */
    public static Product getProductById(int productId) throws SQLException {
        String sql = "SELECT Id, UrunAdi, Fiyat, Stok, Birim FROM dbo.Stoklar WHERE Id = ?";
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

    /** Ürün ekle (fiyat tek noktadan ölçeklenir) */
    public static void addProduct(String urunAdi, BigDecimal fiyat, int stok, String birim) throws SQLException {
        fiyat = Money.scale2(fiyat);
        String sql = "INSERT INTO dbo.Stoklar (UrunAdi, Fiyat, Stok, Birim) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, norm(urunAdi));
            stmt.setBigDecimal(2, fiyat);
            stmt.setInt(3, stok);
            stmt.setString(4, norm(birim));
            stmt.executeUpdate();
        }
    }

    /** Ürün güncelle (fiyat tek noktadan ölçeklenir) */
    public static void updateProduct(Product product) throws SQLException {
        BigDecimal fiyat = Money.scale2(product.getFiyat());
        String sql = "UPDATE dbo.Stoklar SET UrunAdi=?, Fiyat=?, Stok=?, Birim=? WHERE Id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, norm(product.getUrunAdi()));
            stmt.setBigDecimal(2, fiyat);
            stmt.setInt(3, product.getStok());
            stmt.setString(4, norm(product.getBirim()));
            stmt.setInt(5, product.getId());
            stmt.executeUpdate();
        }
    }

    /**
     * Güvenli stok güncellemesi.
     * quantityChange < 0 ise: negatifleşmeyi engellemek için WHERE koşulu zaten var.
     * Başarısız olursa, ürün adını ve mevcut stoku içeren anlaşılır hata mesajı fırlatır.
     */
    public static void updateProductStock(int productId, int quantityChange) throws SQLException {
        final String sql = "UPDATE dbo.Stoklar SET Stok = Stok + ? WHERE Id = ? AND Stok + ? >= 0";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, quantityChange);
            stmt.setInt(2, productId);
            stmt.setInt(3, quantityChange);

            int affected = stmt.executeUpdate();
            if (affected == 1) return;

            // Teşhis: aynı bağlantı üzerinde ürünü oku (ürün yok mu, stok mu yetersiz?)
            Product p = getProductByIdTx(conn, productId);
            if (p == null) {
                throw new SQLException("Ürün bulunamadı (Id=" + productId + ").");
            }

            int current = p.getStok();
            int result = current + quantityChange;

            if (quantityChange < 0 && result < 0) {
                // Kullanıcı-dostu mesaj: ürün adı + stok detayları
                throw new SQLException(
                        p.getUrunAdi() + " için yetersiz stok: Mevcut=" + current +
                                ", Değişim=" + quantityChange + ", Sonuç=" + result + "."
                );
            }

            // Nadir durum: başka nedenle dokunulamadı
            throw new SQLException("Stok güncellenemedi (Id=" + productId + ").");
        }
    }

    /**
     * Ürün silme: önce referans var mı diye kontrol eder,
     * yarış olursa FK kırılımını yakalayıp anlaşılır mesajla sarar.
     */
    public static void deleteProduct(int productId) throws SQLException {
        // 0) Bağımlılık ön-kontrolü (kullanıcıya net mesaj için)
        if (hasAnyReferences(productId)) {
            throw new SQLException("Ürün silinemedi: Bu ürüne bağlı talepler bulunduğundan silme işlemi engellendi.");
        }

        String sql = "DELETE FROM dbo.Stoklar WHERE Id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Ürün bulunamadı (Id=" + productId + ").");
            }
        } catch (SQLException e) {
            if (isForeignKeyViolation(e)) {
                throw new SQLException("Ürün silinemedi: Bu ürüne bağlı talepler bulunduğundan silme işlemi engellendi.", e);
            }
            throw e;
        }
    }

    /** TalepKalemleri vb. tablolarda bu ürüne referans var mı? */
    private static boolean hasAnyReferences(int productId) throws SQLException {
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
        if (e.getErrorCode() == 547) return true;
        String state = e.getSQLState();
        if (state != null && state.startsWith("23")) return true;

        SQLException next = e.getNextException();
        while (next != null) {
            if (next.getErrorCode() == 547) return true;
            String ns = next.getSQLState();
            if (ns != null && ns.startsWith("23")) return true;
            next = next.getNextException();
        }

        String msg = String.valueOf(e.getMessage()).toLowerCase();
        return msg.contains("foreign key") || msg.contains("reference constraint");
    }

    /** Aynı connection içinde ürün oku (teşhis için) */
    private static Product getProductByIdTx(Connection conn, int productId) throws SQLException {
        String sql = "SELECT Id, UrunAdi, Fiyat, Stok, Birim FROM dbo.Stoklar WHERE Id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
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

    /** null-safe trim + iç boşluk sadeleştirme */
    private static String norm(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ");
    }
}
