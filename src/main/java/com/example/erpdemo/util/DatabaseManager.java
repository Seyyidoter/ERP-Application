package com.example.erpdemo.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.Properties;

/**
 * Uygulama genelinde tek havuz (HikariCP) yöneten yardımcı sınıf.
 * - Özellikleri öncelik sırasıyla yükler: ./db.properties -> classpath:application.properties
 * - DataSource ve Connection erişimi sağlar.
 * - Geriye uyumluluk için validateLogin() bırakıldı.
 */
public final class DatabaseManager {

    private static final String EXTERNAL_FILE = "db.properties"; // exe/jar ile aynı klasör
    private static final String DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

    private static String url;
    private static String user;
    private static String password;

    private static HikariDataSource dataSource;

    /** Yalnızca geliştirme için sınırlı bağlantı bilgisi logla. (kullanıcı maskelenir) */
    private static boolean LOG_CONNECTION_INFO = false;

    private DatabaseManager() { }

    /* ================== Statik önyükleme ================== */
    static {
        Properties props = new Properties();

        // 1) Dış dosya
        try (InputStream in = new FileInputStream(EXTERNAL_FILE)) {
            props.load(in);
            safeInfo("[DB] Using external " + EXTERNAL_FILE);
        } catch (IOException ex) {
            safeWarn("[DB] " + EXTERNAL_FILE + " yok, application.properties deneniyor...");
            // 2) Gömülü dosya
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

        LOG_CONNECTION_INFO = Boolean.parseBoolean(props.getProperty("db.logConnectionInfo", "false"));

        if (url == null || url.isBlank()) {
            safeError("[DB] HATA: db.url boş. " + EXTERNAL_FILE + " dosyasını exe/jar ile aynı klasöre koy.");
        } else {
            if (LOG_CONNECTION_INFO) {
                String maskedUser = (user == null || user.isBlank()) ? "(integrated/blank)" : maskMiddle(user);
                safeInfo("[DB] Connection properties yüklendi (driver=" + DRIVER + ", user=" + maskedUser + ")");
            }
            initPool(props);
        }
    }

    /* ================== Havuz kurulumu ================== */
    private static void initPool(Properties props) {
        HikariConfig cfg = new HikariConfig();

        cfg.setDriverClassName(DRIVER);
        cfg.setJdbcUrl(url);

        if (user != null && !user.isBlank()) {
            cfg.setUsername(user);
            cfg.setPassword(password);
        }

        // Havuz ayarları (istersen properties'e taşıyabilirsin)
        cfg.setMaximumPoolSize(parseInt(props.getProperty("db.pool.size", "8"), 8));
        cfg.setMinimumIdle(parseInt(props.getProperty("db.pool.minIdle", "2"), 2));
        cfg.setConnectionTimeout(parseLong(props.getProperty("db.pool.connTimeoutMs", "30000"), 30000L));
        cfg.setConnectionTestQuery("SELECT 1");
        long leakMs = parseLong(props.getProperty("db.pool.leakDetectionMs", "0"), 0L);
        if (leakMs > 0) cfg.setLeakDetectionThreshold(leakMs);

        dataSource = new HikariDataSource(cfg);
    }

    /* ================== Dış API ================== */

    /** Yeni bir bağlantı döndürür (çağıran kapatmakla yükümlüdür). */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) throw new SQLException("DB havuzu başlatılamadı (url boş olabilir).");
        return dataSource.getConnection();
    }

    /** Service/DAO katmanları için DataSource verir (transaction yönetimi için). */
    public static DataSource getDataSource() {
        if (dataSource == null) throw new IllegalStateException("DB havuzu başlatılamadı (url boş olabilir).");
        return dataSource;
    }

    /** Uygulama kapanışında havuzu temiz kapat. */
    public static void shutdownPool() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    /** Geriye uyum için basit login doğrulaması. */
    public static boolean validateLogin(String kullanici, String sifre) throws SQLException {
        final String sql = "SELECT COUNT(*) FROM Kullanicilar WHERE KullaniciAdi=? AND Sifre=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, kullanici);
            ps.setString(2, sifre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /* ================== Küçük yardımcılar & güvenli log ================== */
    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

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
