package com.example.erpdemo;

import java.io.*;
import java.sql.*;
import java.util.Properties;

public class DatabaseManager {

    private static final String EXTERNAL_FILE = "db.properties"; // ERPDemo.exe ile aynı klasörde
    private static String url;
    private static String user;
    private static String password;

    static {
        Properties props = new Properties();

        // 1) Dış dosya (EXE ile aynı klasör)
        try (InputStream in = new FileInputStream(EXTERNAL_FILE)) {
            props.load(in);
            System.out.println("[DB] Using external " + EXTERNAL_FILE);
        } catch (IOException ex) {
            System.err.println("[DB] " + EXTERNAL_FILE + " bulunamadı, uygulamaya gömülü application.properties deneniyor...");
            // 2) Gömülü fallback
            try (InputStream in = DatabaseManager.class.getClassLoader().getResourceAsStream("application.properties")) {
                if (in != null) {
                    props.load(in);
                    System.out.println("[DB] Using embedded application.properties");
                } else {
                    System.err.println("[DB] application.properties da yok! db.url/db.user/db.password bulunamadı.");
                }
            } catch (IOException e) {
                System.err.println("[DB] application.properties okunamadı: " + e.getMessage());
            }
        }

        url      = props.getProperty("db.url", "");
        user     = props.getProperty("db.user", "");
        password = props.getProperty("db.password", "");

        if (url.isBlank()) {
            System.err.println("[DB] HATA: db.url boş. " + EXTERNAL_FILE + " dosyasını ERPDemo.exe ile aynı klasöre koy.");
        } else {
            System.out.println("[DB] url=" + url);
            System.out.println("[DB] user=" + user);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (user == null || user.isBlank()) {
            // (İsteğe bağlı) Windows auth kullanmak istersen URL’inde "integratedSecurity=true" vb. ayarlar olmalı
            return DriverManager.getConnection(url);
        } else {
            return DriverManager.getConnection(url, user, password);
        }
    }

    // Login için basit doğrulama (tablo/kolon adlarını kendi şemanla eşleştir)
    public static boolean validateLogin(String kullanici, String sifre) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Kullanicilar WHERE KullaniciAdi=? AND Sifre=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, kullanici);
            ps.setString(2, sifre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
