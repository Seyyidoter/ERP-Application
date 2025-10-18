package com.example.erpdemo;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.scene.Node;
import java.util.Optional;
import javafx.scene.control.ButtonBar;

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
            System.out.println("[Dialog suppressed] " + String.valueOf(title) + ": " + String.valueOf(message));
            return;
        }

        // null güvenliği
        final String safeTitle = (title == null || title.isBlank()) ? "Bilgi" : title;
        final String raw = String.valueOf(message);
        final String msg = "null".equals(raw) ? "" : raw;

        try {
            runFxAndWait(() -> {
                Alert a = new Alert(type, msg, ButtonType.OK);
                a.setTitle(safeTitle);
                a.setHeaderText(null);
                try { IconUtil.decorateAlert(a); } catch (Throwable ignore) {}
                attachOwnerIfPossible(a);
                // uzun mesajlar için okunabilirlik
                try { a.getDialogPane().setMinWidth(420); } catch (Throwable ignore) {}
                a.showAndWait();
            });
        } catch (IllegalStateException fxClosed) {
            // FX platformu kapanırken çağrıldı; UI göstermeden logla
            System.err.println("[Dialog skipped: FX not available] " + safeTitle + ": " + msg);
        }
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
            return;
        }

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        // FX kapalı/kapanıyorsa runLater burada IllegalStateException fırlatır
        try {
            Platform.runLater(() -> {
                try { r.run(); }
                finally { latch.countDown(); }
            });
        } catch (IllegalStateException fxClosed) {
            throw fxClosed; // üst kat (show/confirm) güvenli biçimde ele alıyor
        }

        try {
            // Kullanıcı diyalogu kapatana kadar bekle (timeout yok)
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for FX thread", ie);
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

    public static boolean confirm(String title, String content, String yesText, String noText, Window owner) {
        if (SUPPRESS.get()) {
            System.out.println("[Dialog suppressed] " + title + ": " + content);
            return false; // kapanış modunda otomatik hayır
        }

        final String safeTitle = (title == null || title.isBlank()) ? "Onay" : title;
        final String msg = "null".equals(String.valueOf(content)) ? "" : String.valueOf(content);
        final ButtonType yes = new ButtonType(yesText == null ? "Evet" : yesText, ButtonBar.ButtonData.YES);
        final ButtonType no  = new ButtonType(noText  == null ? "Hayır" : noText, ButtonBar.ButtonData.NO);

        final boolean[] resultHolder = new boolean[1];
        resultHolder[0] = false; // varsayılan: hayır

        try {
            runFxAndWait(() -> {
                Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, yes, no);
                a.setTitle(safeTitle);
                a.setHeaderText(null);
                try { IconUtil.decorateAlert(a); } catch (Throwable ignore) {}

                if (owner != null) {
                    a.initOwner(owner);
                    a.initModality(Modality.WINDOW_MODAL);
                } else {
                    attachOwnerIfPossible(a);
                }

                try { a.getDialogPane().setMinWidth(420); } catch (Throwable ignore) {}

                Optional<ButtonType> res = a.showAndWait();
                resultHolder[0] = res.isPresent() && res.get() == yes;
            });
        } catch (IllegalStateException fxClosed) {
            // FX kapalı/kapanıyor → güvenli fallback
            System.err.println("[Dialog skipped: FX not available] " + safeTitle + ": " + msg);
            return false;
        }

        return resultHolder[0];
    }

    /** Node üzerinden kolay kullanım (owner = node.getScene().getWindow()) */
    public static boolean confirm(String title, String content, String yesText, String noText, Node ownerNode) {
        Window w = null;
        try {
            if (ownerNode != null && ownerNode.getScene() != null) {
                w = ownerNode.getScene().getWindow();
            }
        } catch (Throwable ignore) {}
        return confirm(title, content, yesText, noText, w);
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
