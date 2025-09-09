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
        String username = txtUser.getText();
        String password = txtPass.getText();

        loadingAlert = showAlert("Giriş Yapılıyor", "Lütfen bekleyin.");

        LoginTask loginTask = new LoginTask(username, password);

        loginTask.setOnSucceeded(e -> {
            boolean success = loginTask.getValue();
            loadingAlert.close();
            if (success) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"));
                    Parent root = loader.load();

                    MainController mainController = loader.getController();
                    mainController.setUser(UserDAO.getUserByUsername(username));

                    Stage stage = new Stage();
                    stage.setTitle("ERP Uygulaması");
                    stage.setScene(new Scene(root));
                    stage.show();

                    ((Stage) txtUser.getScene().getWindow()).close();

                } catch (IOException | SQLException ioException) {
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
        alert.show(); // showAndWait yerine show() kullanıldı
        return alert;
    }

    private class LoginTask extends Task<Boolean> {
        private final String username;
        private final String password;

        public LoginTask(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        protected Boolean call() throws SQLException {
            return DatabaseManager.validateLogin(username, password);
        }
    }
}