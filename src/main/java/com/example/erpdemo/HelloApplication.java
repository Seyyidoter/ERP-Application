package com.example.erpdemo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Locale;

public class HelloApplication extends Application {

    /** Uygulamanın ana sahnesi (PDF kaydet vb. yerlerde lazım) */
    private static Stage primaryStage;

    /** Basit oturum bilgisi: onaylayan kullanıcı id (şimdilik 1) */
    private static int loggedInUserId = 1;

    public static Stage getPrimaryStage() { return primaryStage; }
    public static int getLoggedInUserId() { return loggedInUserId; }
    public static void setLoggedInUserId(int id) { loggedInUserId = id; }

    @Override
    public void start(Stage stage) throws Exception {
        Locale.setDefault(new Locale("tr","TR"));

        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 600, 420);
        var css = HelloApplication.class.getResource("hello.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setTitle("Omnis");
        stage.getIcons().addAll(
                new Image(HelloApplication.class.getResourceAsStream("/com/example/erpdemo/assets/logo-16.png")),
                new Image(HelloApplication.class.getResourceAsStream("/com/example/erpdemo/assets/logo-32.png")),
                new Image(HelloApplication.class.getResourceAsStream("/com/example/erpdemo/assets/logo-64.png"))
        );

        stage.setMinWidth(560);
        stage.setMinHeight(380);
        stage.setScene(scene);
        stage.centerOnScreen();

        primaryStage = stage;
        stage.show();
    }

    public static void main(String[] args) { launch(); }
}
