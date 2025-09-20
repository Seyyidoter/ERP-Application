package com.example.erpdemo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {

    public static User getUserByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM Kullanicilar WHERE KullaniciAdi=?";
        User user = null;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    user = new User(
                            rs.getInt("Id"),
                            rs.getString("KullaniciAdi"),
                            rs.getString("Rol")
                    );
                }
            }
        }
        return user;
    }

    /**
     * Mevcut şifreyi doğrular; doğruysa yeni şifreyi yazar.
     * @return true -> güncellendi, false -> mevcut şifre hatalı
     */
    public static boolean updatePassword(int userId, String currentPassword, String newPassword) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM Kullanicilar WHERE Id=? AND Sifre=?";
        String updateSql = "UPDATE Kullanicilar SET Sifre=? WHERE Id=?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement check = conn.prepareStatement(checkSql)) {

            check.setInt(1, userId);
            check.setString(2, currentPassword);

            try (ResultSet rs = check.executeQuery()) {
                boolean matches = rs.next() && rs.getInt(1) > 0;
                if (!matches) return false;
            }

            try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                upd.setString(1, newPassword);
                upd.setInt(2, userId);
                upd.executeUpdate();
                return true;
            }
        }
    }
}
