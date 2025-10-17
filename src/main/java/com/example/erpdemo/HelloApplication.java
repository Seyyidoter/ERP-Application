package com.example.erpdemo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

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
        try {
            DatabaseManager.shutdownPool();
        } catch (Exception ex) {
            System.err.println("Connection pool shutdown error: " + ex.getMessage());
        }
        // Asenkron havuzu da kapat
        try {
            Async.shutdownNow();
        } catch (Exception ignore) {}
    }

    public static void main(String[] args) {
        launch();
    }
}
