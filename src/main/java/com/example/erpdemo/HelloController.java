package com.example.erpdemo;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

public class HelloController {

    @FXML private TextField txtUser;
    @FXML private PasswordField txtPass;
    @FXML private Button btnLogin; // Enter için default button

    @FXML
    public void initialize() {
        // Enter ile giriş
        txtUser.setOnAction(e -> handleLogin());
        txtPass.setOnAction(e -> handleLogin());
        if (btnLogin != null) btnLogin.setDefaultButton(true);
    }

    @FXML
    private void handleLogin() {
        final String username = txtUser.getText();
        final String password = txtPass.getText();

        // basit doğrulama
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            showError("Hata", "Kullanıcı adı ve şifre zorunludur.");
            return;
        }

        setBusy(true);

        var task = new Task<Boolean>() {
            @Override protected Boolean call() throws SQLException {
                return DatabaseManager.validateLogin(username, password);
            }
        };

        task.setOnSucceeded(ev -> {
            try {
                if (Boolean.TRUE.equals(task.getValue())) {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"));
                    Parent root = loader.load();

                    MainController mc = loader.getController();
                    mc.setUser(UserDAO.getUserByUsername(username));

                    Stage st = new Stage();
                    st.setTitle("Omnis");
                    st.getIcons().addAll(
                            new Image(getClass().getResourceAsStream("/com/example/erpdemo/assets/logo-16.png")),
                            new Image(getClass().getResourceAsStream("/com/example/erpdemo/assets/logo-32.png")),
                            new Image(getClass().getResourceAsStream("/com/example/erpdemo/assets/logo-64.png"))
                    );
                    st.setScene(new Scene(root));
                    st.setMaximized(true);
                    st.show();

                    // Login penceresini kapat
                    ((Stage) btnLogin.getScene().getWindow()).close();
                } else {
                    showError("Hata", "Kullanıcı adı veya şifre yanlış!");
                }
            } catch (IOException | SQLException ex) {
                showError("Hata", "Ana ekran açılamadı:\n" + ex.getMessage());
            } finally {
                setBusy(false);
            }
        });

        task.setOnFailed(ev -> {
            try {
                showError("Bağlantı Hatası", "Veritabanına bağlanılamadı.");
            } finally {
                setBusy(false);
            }
        });

        new Thread(task, "login-task").start();
    }

    private void setBusy(boolean busy) {
        if (txtUser != null) txtUser.setDisable(busy);
        if (txtPass != null) txtPass.setDisable(busy);
        if (btnLogin != null) btnLogin.setDisable(busy);
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        IconUtil.decorateAlert(a); // ← EKLENDİ: tüm uyarılar tutarlı
        a.showAndWait();
    }
}
