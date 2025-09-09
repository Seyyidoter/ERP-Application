package com.example.erpdemo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseManager {

    private static final String URL = "jdbc:sqlserver://SEYYIDOTER;databaseName=SirketDB;encrypt=true;trustServerCertificate=true;";
    private static final String USER = "SA";
    private static final String PASSWORD = "Password1";

    // SQL Injection'ı önleyen güvenli giriş doğrulama metodu
    public static boolean validateLogin(String kullanici, String sifre) throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            String sql = "SELECT * FROM Kullanicilar WHERE KullaniciAdi=? AND Sifre=?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, kullanici);
                stmt.setString(2, sifre);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }
}