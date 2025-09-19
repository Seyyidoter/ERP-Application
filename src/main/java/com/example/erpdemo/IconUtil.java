package com.example.erpdemo;

import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.InputStream;

/** Uygulamadaki tüm pencerelere/uyarılara icon basmak için yardımcı sınıf. */
public final class IconUtil {

    private static final String ICON_PATH = "/com/example/erpdemo/assets/logo-32.png";
    private static final Image APP_ICON = loadIcon();

    private IconUtil() {}

    private static Image loadIcon() {
        try (InputStream is = IconUtil.class.getResourceAsStream(ICON_PATH)) {
            return (is != null) ? new Image(is) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Verilen pencereye (Stage/Window) uygulama ikonunu ekler. */
    public static void setAppIcon(Window window) {
        if (APP_ICON == null || window == null) return;
        if (window instanceof Stage s) {
            if (s.getIcons().isEmpty()) s.getIcons().add(APP_ICON);
        }
    }

    /** Alert/Confirmation gibi dialogların ikonunu ayarlar. */
    public static void decorateAlert(Alert alert) {
        if (alert == null) return;
        // mevcutsa hemen ekle
        if (alert.getDialogPane() != null &&
                alert.getDialogPane().getScene() != null &&
                alert.getDialogPane().getScene().getWindow() != null) {
            setAppIcon(alert.getDialogPane().getScene().getWindow());
        }
        // açıldığında da garantiye al
        alert.setOnShown(e -> {
            if (alert.getDialogPane() != null &&
                    alert.getDialogPane().getScene() != null &&
                    alert.getDialogPane().getScene().getWindow() != null) {
                setAppIcon(alert.getDialogPane().getScene().getWindow());
            }
        });
    }

    /** TextInputDialog, ChoiceDialog ve genel Dialog<?> için ikon ayarı. */
    public static void decorateDialog(Dialog<?> dialog) {
        if (dialog == null) return;
        // mevcutsa hemen
        if (dialog.getDialogPane() != null &&
                dialog.getDialogPane().getScene() != null &&
                dialog.getDialogPane().getScene().getWindow() != null) {
            setAppIcon(dialog.getDialogPane().getScene().getWindow());
        }
        // açıldığında da
        dialog.setOnShown(e -> {
            if (dialog.getDialogPane() != null &&
                    dialog.getDialogPane().getScene() != null &&
                    dialog.getDialogPane().getScene().getWindow() != null) {
                setAppIcon(dialog.getDialogPane().getScene().getWindow());
            }
        });
    }
}
