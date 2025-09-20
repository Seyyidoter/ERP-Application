package com.example.erpdemo;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/** Tüm bilgi / uyarı / hata mesajları için tek merkez. */
public final class AppDialogs {

    private AppDialogs() { }

    public static void info(String message) {
        show(Alert.AlertType.INFORMATION, "Bilgi", message);
    }

    public static void warn(String message) {
        show(Alert.AlertType.WARNING, "Uyarı", message);
    }

    public static void error(String message) {
        show(Alert.AlertType.ERROR, "Hata", message);
    }

    private static void show(Alert.AlertType type, String title, String message) {
        Alert a = new Alert(type, message, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        IconUtil.decorateAlert(a);
        a.showAndWait();
    }
}
