package com.example.erpdemo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {

    /** Kullanıcı adından temel bilgiler (Id, KullaniciAdi, Rol) */
    public static User getUserByUsername(String username) throws SQLException {
        // Güvenlik/temizlik: null koruması + trim
        final String u = (username == null) ? "" : username.trim();

        final String sql = """
            SELECT Id, KullaniciAdi, Rol
              FROM dbo.Kullanicilar
             WHERE KullaniciAdi = ?
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, u);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("Id"),
                            rs.getString("KullaniciAdi"),
                            rs.getString("Rol")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Mevcut şifre doğruysa tek atomik UPDATE ile yeni şifreyi yazar.
     * @return true -> güncellendi; false -> mevcut şifre hatalı (veya kullanıcı yok)
     *
     * Not: Uygulamada şifreleri açık metin saklamak yerine hash (örn. bcrypt/argon2) kullanın.
     */
    public static boolean updatePassword(int userId, String currentPassword, String newPassword) throws SQLException {
        final String sql = """
            UPDATE dbo.Kullanicilar
               SET Sifre = ?
             WHERE Id = ? AND Sifre = ?
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPassword);
            ps.setInt(2, userId);
            ps.setString(3, currentPassword);
            return ps.executeUpdate() == 1;
        }
    }
}
