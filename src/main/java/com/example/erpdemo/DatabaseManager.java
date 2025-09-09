package com.example.erpdemo;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseManager {

    private static final Properties props = new Properties();
    private static String url;
    private static String user;
    private static String password;

    static {
        try (InputStream input = DatabaseManager.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.err.println("Configuration file not found: application.properties");
                throw new IOException("Unable to find application.properties");
            }
            props.load(input);

            url = props.getProperty("db.url");
            user = props.getProperty("db.user");
            password = props.getProperty("db.password");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // SQL Injection'ı önleyen güvenli giriş doğrulama metodu
    public static boolean validateLogin(String kullanici, String sifre) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
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

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}