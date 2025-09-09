package com.example.erpdemo;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

public class HelloController {

    @FXML private TextField txtUser;
    @FXML private PasswordField txtPass;

    private Alert loadingAlert;

    @FXML
    protected void handleLogin() {
        String kullanici = txtUser.getText();
        String sifre = txtPass.getText();

        loadingAlert = showAlert("Giriş Yapılıyor", "Lütfen bekleyin...");

        LoginTask loginTask = new LoginTask(kullanici, sifre);

        loginTask.setOnSucceeded(e -> {
            boolean success = loginTask.getValue();
            loadingAlert.close();
            if (success) {
                // BAŞARILI GİRİŞ SONRASI BURASI ÇALIŞACAK
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"));
                    Parent root = loader.load();

                    Stage stage = new Stage();
                    stage.setTitle("ERP Uygulaması");
                    stage.setScene(new Scene(root));
                    stage.show();

                    // Giriş penceresini kapatır
                    ((Stage) txtUser.getScene().getWindow()).close();

                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            } else {
                showAlert("Hata", "Kullanıcı adı veya şifre yanlış!");
            }
        });

        loginTask.setOnFailed(e -> {
            loadingAlert.close();
            Throwable exception = loginTask.getException();
            showAlert("Veritabanı Bağlantı Hatası", "Veritabanına bağlanılamadı. Lütfen bilgilerinizi kontrol edin.");
            exception.printStackTrace();
        });

        new Thread(loginTask).start();
    }

    private Alert showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
        return alert;
    }

    private class LoginTask extends Task<Boolean> {
        private final String kullanici;
        private final String sifre;

        public LoginTask(String kullanici, String sifre) {
            this.kullanici = kullanici;
            this.sifre = sifre;
        }

        @Override
        protected Boolean call() throws SQLException {
            return DatabaseManager.validateLogin(kullanici, sifre);
        }
    }
}