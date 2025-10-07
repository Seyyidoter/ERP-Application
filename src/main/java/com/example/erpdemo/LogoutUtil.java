package com.example.erpdemo;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.List;

public final class LogoutUtil {
    private LogoutUtil() {}

    /** Oturumu kapatır: pencere boyutunu/konumunu bozmadan yumuşak bir fade ile login ekranına geçer. */
    public static void performLogout(Node anyNodeInsideWindow) {
        if (anyNodeInsideWindow == null || anyNodeInsideWindow.getScene() == null) {
            showError("Oturum kapatma işlemi başlatılamadı (geçersiz sahne).");
            return;
        }

        Stage stage = (Stage) anyNodeInsideWindow.getScene().getWindow();
        Scene currentScene = stage.getScene();
        if (currentScene == null || currentScene.getRoot() == null) {
            // Olağan dışı bir durum: animasyonsuz fallback
            swapToLogin(stage, /*keepSize=*/true, /*animate=*/false);
            return;
        }

        // Boyut/konumu sakla (ekran ortalamada ihtiyacımız olabilir)
        final double keepX = stage.getX();
        final double keepY = stage.getY();
        final double keepW = stage.getWidth();
        final double keepH = stage.getHeight();

        // 1) Mevcut root'u yumuşakça söndür (200–250ms ideal)
        FadeTransition fadeOut = new FadeTransition(Duration.millis(220), currentScene.getRoot());
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(ev -> {
            try {
                FXMLLoader loader = new FXMLLoader(LogoutUtil.class.getResource("hello-view.fxml"));
                Parent loginRoot = loader.load();

                // 2) LOGIN sahnesini aynı pencere boyutunda ver → ani küçülme yok
                Scene loginScene = new Scene(loginRoot, keepW, keepH);
                stage.setScene(loginScene);
                stage.setTitle("Omnis – Giriş");
                IconUtil.setAppIcon(stage);

                // Konumu da aynı kalsın (merkezdeyse zaten ortada kalır)
                stage.setX(keepX);
                stage.setY(keepY);

                // 3) Login root’u sıfır opaklıkla başlat, içeri doğru yumuşakça girsin
                loginRoot.setOpacity(0.0);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(220), loginRoot);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();

            } catch (IOException ex) {
                showError("Giriş ekranı açılamadı:\n" + ex.getMessage());
            } catch (Throwable t) {
                showError("Beklenmeyen bir hata oluştu:\n" + t.getMessage());
            }
        });

        fadeOut.play();
    }

    /* --- İstersen animasyonsuz veya login boyutuna göre açmak için yardımcı metot --- */
    private static void swapToLogin(Stage stage, boolean keepSize, boolean animate) {
        try {
            FXMLLoader loader = new FXMLLoader(LogoutUtil.class.getResource("hello-view.fxml"));
            Parent root = loader.load();

            Scene scene;
            if (keepSize) {
                // Pencere boyutunu koru
                scene = new Scene(root, stage.getWidth(), stage.getHeight());
            } else {
                // Login'in doğal boyutuna geç (NOT: ani küçülmeye sebep olabilir)
                scene = new Scene(root);
            }

            stage.setScene(scene);
            stage.setTitle("Omnis – Giriş");
            IconUtil.setAppIcon(stage);

            if (!keepSize) {
                // Login doğal boyuta geçtiyse bulunduğu ekranda ortaya al
                Platform.runLater(() -> centerOnSameScreen(stage));
            }

            if (animate) {
                root.setOpacity(0);
                FadeTransition fi = new FadeTransition(Duration.millis(200), root);
                fi.setFromValue(0); fi.setToValue(1); fi.play();
            }
        } catch (IOException ex) {
            showError("Giriş ekranı açılamadı:\n" + ex.getMessage());
        }
    }

    /** Stage’in bulunduğu ekranda merkezi konum. */
    private static void centerOnSameScreen(Stage stage) {
        var screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        Screen s = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
        Rectangle2D vb = s.getVisualBounds();
        double x = vb.getMinX() + (vb.getWidth()  - stage.getWidth())  / 2.0;
        double y = vb.getMinY() + (vb.getHeight() - stage.getHeight()) / 2.0;
        stage.setX(x); stage.setY(y);
    }

    private static void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle("Hata");
        IconUtil.decorateAlert(a);
        a.showAndWait();
    }
}
