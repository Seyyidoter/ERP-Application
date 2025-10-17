package com.example.erpdemo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.concurrent.TimeUnit;

/** Uygulama giriş noktası. */
public class HelloApplication extends Application {

    private static volatile int loggedInUserId = 0;
    public static int getLoggedInUserId() { return loggedInUserId; }
    public static void setLoggedInUserId(int id) { loggedInUserId = id; }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxml = new FXMLLoader(getClass().getResource("hello-view.fxml"));
        Parent root = fxml.load();

        stage.setScene(new Scene(root));
        stage.setTitle("Omnis");
        IconUtil.setAppIcon(stage);
        stage.show();
    }

    @Override
    public void stop() {
        // 1) Kapanışta diyalog göstermeyi bastır (Alert.showAndWait riskini kaldır)
        try { AppDialogs.suppressDialogs(true); } catch (Throwable ignore) {}

        // 2) Asenkron havuza yeni iş kabulünü durdur + kısa süre bekle
        try {
            Async.blockNewTasks();                       // yeni iş gelmesin
            Async.shutdownGracefully(5, TimeUnit.SECONDS); // mevcut işleri 5 sn bekle
        } catch (Throwable ignore) {}

        // 3) DB pool'u kapat (mevcut işler bittiyse bağlantılar boşta olacak)
        try {
            DatabaseManager.shutdownPool();
        } catch (Exception ex) {
            System.err.println("Connection pool shutdown error: " + ex.getMessage());
        }

        // 4) Hâlâ çalışan işler varsa zorla kapat
        try {
            Async.shutdownNow();
        } catch (Throwable ignore) {}
    }

    public static void main(String[] args) {
        launch();
    }
}
