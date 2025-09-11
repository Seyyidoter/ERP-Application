package com.example.erpdemo;

import java.sql.Connection;
import java.sql.SQLException;

public class ConnectionTest {
    public static void main(String[] args) {
        System.out.println("Veritabanı bağlantısı test ediliyor (external config ile)...");
        try (Connection conn = DatabaseManager.getConnection()) {
            System.out.println(conn != null
                    ? "Başarılı: Veritabanına bağlanıldı!"
                    : "Hata: Bağlantı nesnesi null döndü.");
        } catch (SQLException ex) {
            System.err.println("Hata: Veritabanına bağlanılamadı.");
            System.err.println("SQLException: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
