package com.example.erpdemo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {

    public static User getUserByUsername(String username) throws SQLException { // getKullaniciByUsername -> getUserByUsername
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
}