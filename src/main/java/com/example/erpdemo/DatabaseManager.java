package com.example.erpdemo;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.Properties;

public class DatabaseManager {

    private static final String EXTERNAL_FILE = "db.properties"; // exe/jar ile aynı klasör
    private static String url;
    private static String user;
    private static String password;

    private static HikariDataSource dataSource;

    /** İsteğe bağlı: yalnızca geliştirme için kısıtlı bağlantı bilgisi logla. */
    private static boolean LOG_CONNECTION_INFO = false;

    static {
        Properties props = new Properties();

        // 1) Dış dosya
        try (InputStream in = new FileInputStream(EXTERNAL_FILE)) {
            props.load(in);
            safeInfo("[DB] Using external " + EXTERNAL_FILE);
        } catch (IOException ex) {
            safeWarn("[DB] " + EXTERNAL_FILE + " yok, application.properties deneniyor...");
            // 2) Gömülü
            try (InputStream in = DatabaseManager.class.getClassLoader().getResourceAsStream("application.properties")) {
                if (in != null) {
                    props.load(in);
                    safeInfo("[DB] Using embedded application.properties");
                } else {
                    safeWarn("[DB] application.properties da bulunamadı.");
                }
            } catch (IOException e) {
                safeWarn("[DB] application.properties okunamadı: " + e.getMessage());
            }
        }

        url      = props.getProperty("db.url", "");
        user     = props.getProperty("db.user", "");
        password = props.getProperty("db.password", "");

        // İsteğe bağlı sınırlı loglama bayrağı (varsayılan: false)
        LOG_CONNECTION_INFO = Boolean.parseBoolean(props.getProperty("db.logConnectionInfo", "false"));

        if (url.isBlank()) {
            safeError("[DB] HATA: db.url boş. " + EXTERNAL_FILE + " dosyasını exe/jar ile aynı klasöre koy.");
        } else {
            // Güvenlik: URL ve kullanıcı adı artık loglanmıyor.
            if (LOG_CONNECTION_INFO) {
                // Sadece sürücü ve maskeli kullanıcı bilgisini kısa not olarak verelim.
                String maskedUser = (user == null || user.isBlank()) ? "(integrated/blank)"
                        : maskMiddle(user);
                safeInfo("[DB] Connection properties yüklendi (driver=com.microsoft.sqlserver.jdbc.SQLServerDriver, user=" + maskedUser + ")");
            }
            initPool(props);
        }
    }

    private static void initPool(Properties props) {
        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        cfg.setJdbcUrl(url);

        if (user != null && !user.isBlank()) {
            cfg.setUsername(user);
            cfg.setPassword(password);
        }

        // Havuz ayarları (istersen properties’e yaz)
        cfg.setMaximumPoolSize(parseInt(props.getProperty("db.pool.size", "8"), 8));
        cfg.setMinimumIdle(parseInt(props.getProperty("db.pool.minIdle", "2"), 2));
        cfg.setConnectionTimeout(parseLong(props.getProperty("db.pool.connTimeoutMs", "30000"), 30000L));
        cfg.setConnectionTestQuery("SELECT 1");
        long leakMs = parseLong(props.getProperty("db.pool.leakDetectionMs", "0"), 0L);
        if (leakMs > 0) cfg.setLeakDetectionThreshold(leakMs);

        dataSource = new HikariDataSource(cfg);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) throw new SQLException("DB havuzu başlatılamadı (url boş olabilir).");
        return dataSource.getConnection();
    }

    public static void shutdownPool() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    // Basit login doğrulama (NOT: prod için hash’e geçin)
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

    /* ==================== küçük yardımcılar & güvenli log ==================== */

    /** Kullanıcı adını ortasını maskeleyerek göster (abc****yz gibi). */
    private static String maskMiddle(String s) {
        if (s == null || s.length() <= 2) return "***";
        int keep = Math.max(1, s.length() / 4);
        String start = s.substring(0, keep);
        String end = s.substring(s.length() - keep);
        return start + "***" + end;
    }

    private static void safeInfo(String msg) { System.out.println(msg); }
    private static void safeWarn(String msg) { System.out.println(msg); }
    private static void safeError(String msg) { System.err.println(msg); }
}
