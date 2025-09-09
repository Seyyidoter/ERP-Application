package com.example.erpdemo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionTest {

    // Veritabanı bağlantı bilgileri
    private static final String URL = "jdbc:sqlserver://SEYYIDOTER;databaseName=SirketDB;encrypt=true;";
    private static final String USER = "SA";
    private static final String PASSWORD = "Password1";

    public static void main(String[] args) {
        System.out.println("Veritabanı bağlantısı test ediliyor...");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            if (conn != null) {
                System.out.println("Başarılı: Veritabanına başarıyla bağlanıldı!");
            }
        } catch (SQLException ex) {
            System.err.println("Hata: Veritabanına bağlanılamadı.");
            System.err.println("SQLException: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}