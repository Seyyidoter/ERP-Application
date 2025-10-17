package com.example.erpdemo;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AppDialogs {

    private AppDialogs() { }

    // --- KAPANIŞTA DİYALOG BASTIRMAYI AÇ/KAPA ---
    private static final AtomicBoolean SUPPRESS = new AtomicBoolean(false);

    /** Uygulama kapanırken true yap: hiçbir diyalog gösterilmez. */
    public static void suppressDialogs(boolean on) { SUPPRESS.set(on); }

    /** Şu an bastırılıyor mu? (Gerekirse dışarıdan da kontrol edebilirsin) */
    public static boolean isSuppressed() { return SUPPRESS.get(); }

    /* -------- Basit bilgi/uyarı -------- */
    public static void info(String message)  { show(Alert.AlertType.INFORMATION, "Bilgi", message); }
    public static void warn(String message)  { show(Alert.AlertType.WARNING,    "Uyarı", message); }
    public static void error(String message) { show(Alert.AlertType.ERROR,      "Hata",  message); }

    /* -------- Güvenli SQL hata gösterimi -------- */
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

    private static void attachOwnerIfPossible(javafx.scene.control.Dialog<?> d) {
        try {
            javafx.stage.Window owner = null;
            for (var w : javafx.stage.Window.getWindows()) {
                if (w.isFocused() && w.isShowing()) { owner = w; break; }
            }
            if (owner == null) {
                for (var w : javafx.stage.Window.getWindows()) {
                    if (w.isShowing()) { owner = w; break; }
                }
            }
            if (owner != null) d.initOwner(owner);
        } catch (Throwable ignore) {}
    }

    /* ================== private helpers ================== */

    private static void show(Alert.AlertType type, String title, String message) {
        // KAPANIŞ MODUNDAYSAN: diyalog basma, sessizce geç
        if (SUPPRESS.get()) {
            // İstersen hafif bir log bırak:
            System.out.println("[Dialog suppressed] " + title + ": " + String.valueOf(message));
            return;
        }

        runFxAndWait(() -> {
            Alert a = new Alert(type, message, ButtonType.OK);
            a.setTitle(title);
            a.setHeaderText(null);
            try { IconUtil.decorateAlert(a); } catch (Throwable ignore) {}
            attachOwnerIfPossible(a);
            a.showAndWait();
        });
    }

    private static void log(String context, Throwable ex) {
        System.err.println("=== UI Error ===");
        if (context != null && !context.isBlank()) System.err.println("Context: " + context);
        ex.printStackTrace(System.err);
        System.err.println("================");
    }

    private static void runFxAndWait(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            final CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try { r.run(); }
                finally { latch.countDown(); }
            });
            try { latch.await(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
    }

    private static String mapSqlErrorToFriendlyText(SQLException ex, String context) {
        String state = safe(ex.getSQLState()).toUpperCase();
        int code = ex.getErrorCode();
        String ctx  = (context == null || context.isBlank()) ? "" : (context + " sırasında ");

        if (state.startsWith("08")) {
            return ctx + "veritabanı bağlantısı kurulamadı. Lütfen ağ/erişim ayarlarını kontrol edin.";
        }
        if (state.equals("28000")) {
            return ctx + "veritabanı erişim izni reddedildi. Yetkilerinizi kontrol edin.";
        }
        if (state.startsWith("23")) {
            return ctx + "veri bütünlüğü kısıtı nedeniyle işlem tamamlanamadı.";
        }
        if (code == 547)                  return ctx + "ilişkili kayıtlar nedeniyle işlem tamamlanamadı.";
        if (code == 2627 || code == 2601) return ctx + "aynı veriden zaten mevcut, benzersiz kayıt kısıtı ihlali.";

        return ctx + "işlem tamamlanamadı. Lütfen daha sonra tekrar deneyin.";
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
