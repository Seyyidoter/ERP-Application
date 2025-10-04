package com.example.erpdemo.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.sql.SQLException;

public final class AppDialogs {

    private AppDialogs() { }

    /* -------- Basit bilgi/uyarı -------- */
    public static void info(String message)  { show(Alert.AlertType.INFORMATION, "Bilgi", message); }
    public static void warn(String message)  { show(Alert.AlertType.WARNING,    "Uyarı", message); }
    public static void error(String message) { show(Alert.AlertType.ERROR,      "Hata",  message); }

    /* -------- Güvenli SQL hata gösterimi --------
       Kullanıcıya genel/faydalı mesaj verir; ayrıntıyı loglar. */
    public static void dbError(String context, SQLException ex) {
        log(context, ex);
        String friendly = mapSqlErrorToFriendlyText(ex, context);
        show(Alert.AlertType.ERROR, "Hata", friendly);
    }

    /* -------- Diğer beklenmeyen hatalar -------- */
    public static void unexpectedError(String context, Throwable ex) {
        log(context, ex);
        String msg = (context == null || context.isBlank())
                ? "Beklenmeyen bir hata oluştu."
                : context + " sırasında beklenmeyen bir hata oluştu.";
        show(Alert.AlertType.ERROR, "Hata", msg);
    }

    /* ================== private helpers ================== */

    private static void show(Alert.AlertType type, String title, String message) {
        Alert a = new Alert(type, message, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        IconUtil.decorateAlert(a);
        a.showAndWait();
    }

    private static void log(String context, Throwable ex) {
        System.err.println("=== UI Error ===");
        if (context != null && !context.isBlank()) System.err.println("Context: " + context);
        ex.printStackTrace(System.err);
        System.err.println("================");
    }

    private static String mapSqlErrorToFriendlyText(SQLException ex, String context) {
        // SQLState/Code yorumları (genel yaklaşım; sürücüye göre değişebilir)
        String state = safe(ex.getSQLState()).toUpperCase();
        int code = ex.getErrorCode();
        String ctx  = (context == null || context.isBlank()) ? "" : (context + " sırasında ");

        // Bağlantı sorunları
        if (state.startsWith("08")) {
            return ctx + "veritabanı bağlantısı kurulamadı. Lütfen ağ/erişim ayarlarını kontrol edin.";
        }

        // Erişim/izin
        if (state.equals("28000")) {
            return ctx + "veritabanı erişim izni reddedildi. Yetkilerinizi kontrol edin.";
        }

        // Kısıt ihlali (benzersizlik, foreign key vs.)
        if (state.startsWith("23")) {
            return ctx + "veri bütünlüğü kısıtı nedeniyle işlem tamamlanamadı.";
        }

        // SQL Server bazı bilinen kodlar
        // 547: FK ihlali, 2627/2601: unique ihlali
        if (code == 547)  return ctx + "ilişkili kayıtlar nedeniyle işlem tamamlanamadı.";
        if (code == 2627 || code == 2601) return ctx + "aynı veriden zaten mevcut, benzersiz kayıt kısıtı ihlali.";

        // Varsayılan genel mesaj
        return ctx + "işlem tamamlanamadı. Lütfen daha sonra tekrar deneyin.";
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
