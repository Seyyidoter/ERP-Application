package com.example.erpdemo;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public final class LogoutUtil {
    private LogoutUtil() {}

    /** Mevcut ana pencereyi kapatır, login ekranını tekrar açar (uygulama kapanmaz). */
    public static void performLogout(Node anyNodeInsideWindow) {
        Stage current = (Stage) anyNodeInsideWindow.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader(LogoutUtil.class.getResource("hello-view.fxml"));
            Parent root = loader.load();

            Stage login = new Stage();
            login.setTitle("Omnis – Giriş");
            login.getIcons().add(new Image(LogoutUtil.class.getResourceAsStream("/com/example/erpdemo/assets/logo-32.png")));
            login.setScene(new Scene(root));
            login.show();

            current.close();
        } catch (IOException ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Giriş ekranı açılamadı:\n" + ex.getMessage());
            a.setHeaderText(null);
            a.setTitle("Hata");
            a.showAndWait();
        }
    }
}
