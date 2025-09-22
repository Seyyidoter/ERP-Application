package com.example.erpdemo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Uygulama giriş noktası.
 * Not: Uygulama kapanırken Hikari havuzunu kapatmak için stop() override edildi.
 */
public class HelloApplication extends Application {

    /** Oturum açmış kullanıcının ID’si (Approval ekranı tarafından kullanılıyor). */
    private static volatile int loggedInUserId = 0;

    public static int getLoggedInUserId() { return loggedInUserId; }
    public static void setLoggedInUserId(int id) { loggedInUserId = id; }

    @Override
    public void start(Stage stage) throws Exception {
        // Giriş (login) ekranını yükle
        FXMLLoader fxml = new FXMLLoader(getClass().getResource("hello-view.fxml"));
        Parent root = fxml.load();

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Omnis");
        IconUtil.setAppIcon(stage);

        stage.show();
    }

    /**
     * JavaFX yaşam döngüsü: pencere kapanırken çağrılır.
     * Burada HikariCP havuzunu düzgün biçimde kapatıyoruz.
     */
    @Override
    public void stop() {
        try {
            DatabaseManager.shutdownPool();
        } catch (Exception ex) {
            // Kapanışta hata olsa bile uygulamayı engellemeyelim; loglamak yeterli.
            System.err.println("Connection pool shutdown error: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
