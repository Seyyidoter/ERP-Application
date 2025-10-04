package com.example.erpdemo.dao;

import com.example.erpdemo.util.DatabaseManager;
import com.example.erpdemo.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {

    public static User getUserByUsername(String username) throws SQLException {
        String sql = "SELECT Id, KullaniciAdi, Rol FROM Kullanicilar WHERE KullaniciAdi = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
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
     */
    public static boolean updatePassword(int userId, String currentPassword, String newPassword) throws SQLException {
        // Tek sorgu: Koşullu UPDATE
        final String sql = """
            UPDATE Kullanicilar
               SET Sifre = ?
             WHERE Id = ? AND Sifre = ?
            """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPassword);
            ps.setInt(2, userId);
            ps.setString(3, currentPassword);

            int affected = ps.executeUpdate();
            return affected == 1;
        }
    }
}
