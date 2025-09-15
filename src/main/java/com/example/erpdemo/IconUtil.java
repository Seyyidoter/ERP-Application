package com.example.erpdemo;

import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.InputStream;

/** Uygulamadaki tüm pencerelere/uyarılara icon basmak için yardımcı sınıf. */
public final class IconUtil {

    // Kaynağınızdaki mevcut dosyayı kullanıyoruz:
    private static final String ICON_PATH = "/com/example/erpdemo/assets/logo-32.png";
    private static final Image APP_ICON = loadIcon();

    private IconUtil() {}

    private static Image loadIcon() {
        try (InputStream is = IconUtil.class.getResourceAsStream(ICON_PATH)) {
            return (is != null) ? new Image(is) : null;
        } catch (Exception e) {
            return null; // ikon yoksa sessizce geç
        }
    }

    /** Verilen pencereye (Stage/Window) uygulama ikonunu ekler. */
    public static void setAppIcon(Window window) {
        if (APP_ICON == null || window == null) return;
        if (window instanceof Stage s) {
            if (s.getIcons().isEmpty()) {
                s.getIcons().add(APP_ICON);
            }
        }
    }

    /** Alert/Confirmation gibi dialogların ikonunu ayarlar. */
    public static void decorateAlert(Alert alert) {
        if (alert == null) return;
        // Sahne hazırsa hemen ekle
        if (alert.getDialogPane() != null &&
                alert.getDialogPane().getScene() != null &&
                alert.getDialogPane().getScene().getWindow() != null) {
            setAppIcon(alert.getDialogPane().getScene().getWindow());
        }
        alert.setOnShown(e -> {
            if (alert.getDialogPane() != null &&
                    alert.getDialogPane().getScene() != null &&
                    alert.getDialogPane().getScene().getWindow() != null) {
                setAppIcon(alert.getDialogPane().getScene().getWindow());
            }
        });
    }
}
